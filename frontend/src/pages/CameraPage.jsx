import { useCallback, useEffect, useState } from 'react'
import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Center,
  Group,
  Loader,
  Stack,
  Table,
  Text,
  TextInput,
  Title,
} from '@mantine/core'
import { api } from '../api'

// Mantine's default Button height (size "sm"). The status badges are wrapped in
// rows of this height so a badge's midpoint lines up with the button beside it.
const CONTROL_ROW_HEIGHT = 36

// The app carries no icon library, so the few glyphs this page needs are small
// inline SVGs. Drawn like Tabler's icons so they sit naturally beside the
// Mantine controls.
function TrashIcon({ size = 16 }) {
  return (
    <svg
      aria-hidden="true"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M4 7h16" />
      <path d="M10 11v6" />
      <path d="M14 11v6" />
      <path d="M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2l1-12" />
      <path d="M9 7V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v3" />
    </svg>
  )
}

function PlusIcon({ size = 16 }) {
  return (
    <svg
      aria-hidden="true"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

// One camera's parameters, from babymonitor.cameras. The same page backs both
// menu entries, "Camera1" and "Camera2": the camera number arrives as a prop.
// The parameter names are Motion option names. Adding, editing and deleting a
// row only touch the table: Apply is what rebuilds config and reaches Motion.
//
// Start/Apply marks the camera as running, rebuilds its config and starts
// Motion (or reloads it if it is already up). Stop marks it not running and
// kills the process. The backend's MotionSupervisor keeps a camera that is
// marked running alive: if its process dies it is restarted and a System
// notification is recorded. Startup is cold, so a camera comes up only from
// here.
export default function CameraPage({ cameraNumber }) {
  const [parameters, setParameters] = useState([])
  const [drafts, setDrafts] = useState({})
  const [motion, setMotion] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [savingKey, setSavingKey] = useState(null)
  const [deletingKey, setDeletingKey] = useState(null)
  const [savedKey, setSavedKey] = useState(null)
  const [stopping, setStopping] = useState(false)
  const [applying, setApplying] = useState(false)
  const [audio, setAudio] = useState(null)
  const [audioBusy, setAudioBusy] = useState(false)

  // The "add a row" form. Its values live here rather than in the parameters
  // list, so cancelling leaves nothing behind.
  const [adding, setAdding] = useState(false)
  const [newKey, setNewKey] = useState('')
  const [newValue, setNewValue] = useState('')
  const [savingNew, setSavingNew] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [list, status] = await Promise.all([
        api.getCamera(cameraNumber),
        api.getCameraMotion(cameraNumber),
      ])
      try {
        const audioStatus = await api.getCameraAudio(cameraNumber)
        setAudio(audioStatus)
      } catch (e) {
        setAudio(null)
      }
      setParameters(list)
      setDrafts(Object.fromEntries(list.map((p) => [p.key, p.value])))
      setMotion(status)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [cameraNumber])

  useEffect(() => {
    load()
  }, [load])

  // The audio chip has to reflect the process, not just the last click: the
  // backend's AudioSupervisor may restart a dead capture between actions, so
  // poll while the page is open.
  useEffect(() => {
    let cancelled = false
    const id = setInterval(async () => {
      try {
        const status = await api.getCameraAudio(cameraNumber)
        if (!cancelled) {
          setAudio(status)
        }
      } catch {
        // A transient fetch failure leaves the last known status in place.
      }
    }, 5000)
    return () => {
      cancelled = true
      clearInterval(id)
    }
  }, [cameraNumber])

  const saveValue = async (key) => {
    setError(null)
    setSavingKey(key)
    setSavedKey(null)
    try {
      const updated = await api.updateCameraParameter(cameraNumber, key, drafts[key])
      setParameters((current) => current.map((p) => (p.key === key ? updated : p)))
      setSavedKey(key)
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingKey(null)
    }
  }

  const startAdd = () => {
    setError(null)
    setNewKey('')
    setNewValue('')
    setAdding(true)
  }

  const cancelAdd = () => {
    setAdding(false)
    setNewKey('')
    setNewValue('')
  }

  const addParameter = async () => {
    const key = newKey.trim()
    if (!key) {
      setError('A parameter name is required')
      return
    }
    if (!newValue.trim()) {
      setError('A value is required')
      return
    }
    setError(null)
    setSavingNew(true)
    try {
      const created = await api.createCameraParameter(cameraNumber, key, newValue)
      // The API returns the rows in name order, so slot the new one in rather
      // than appending and leaving the table unsorted.
      setParameters((current) =>
        [...current, created].sort((a, b) => a.key.localeCompare(b.key)),
      )
      setDrafts((current) => ({ ...current, [created.key]: created.value }))
      setAdding(false)
      setNewKey('')
      setNewValue('')
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingNew(false)
    }
  }

  // Deleting is destructive and cannot be undone, so ask first. The row is gone
  // from the database, from the camera's generated config and from this page.
  const deleteValue = async (key) => {
    setError(null)
    if (!window.confirm(`Delete the "${key}" parameter? This cannot be undone.`)) {
      return
    }
    setDeletingKey(key)
    try {
      await api.deleteCameraParameter(cameraNumber, key)
      setParameters((current) => current.filter((p) => p.key !== key))
      setDrafts((current) => {
        const next = { ...current }
        delete next[key]
        return next
      })
      // Drop a "Saved" badge left over from the row that just went away.
      if (savedKey === key) {
        setSavedKey(null)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setDeletingKey(null)
    }
  }

  // Audio follows the camera, but the Audio button owns it directly.
  const refreshAudio = async () => {
    try {
      setAudio(await api.getCameraAudio(cameraNumber))
    } catch {
      // Leave the last known status in place on a transient failure.
    }
  }

  const apply = async () => {
    setError(null)
    setApplying(true)
    try {
      const status = await api.applyCameraMotion(cameraNumber)
      setMotion(status)
      // Apply marks the camera as running on the server; mirror it locally so
      // the "restarting" hint below is accurate without a reload.
      setParameters((current) => current.map((p) => ({ ...p, enabled: true })))
      // Audio follows the camera, so pick up its new state too.
      await refreshAudio()
    } catch (err) {
      setError(err.message)
    } finally {
      setApplying(false)
    }
  }

  // Stop marks the camera not running and kills its process, so the backend
  // supervisor stops watching it.
  const stop = async () => {
    setError(null)
    setStopping(true)
    try {
      const status = await api.stopCameraMotion(cameraNumber)
      setMotion(status)
      setParameters((current) => current.map((p) => ({ ...p, enabled: false })))
      // Audio follows the camera, so pick up its new state too.
      await refreshAudio()
    } catch (err) {
      setError(err.message)
    } finally {
      setStopping(false)
    }
  }

  const toggleAudio = async () => {
    setError(null)
    setAudioBusy(true)
    try {
      const status = audio?.running
        ? await api.stopCameraAudio(cameraNumber)
        : await api.startCameraAudio(cameraNumber)
      setAudio(status)
    } catch (err) {
      setError(err.message)
      await refreshAudio()
    } finally {
      setAudioBusy(false)
    }
  }

  const name = parameters.find((p) => p.key === 'camera_name')?.value
  // The persisted desired state: set by Start/Apply, cleared by Stop. The badge
  // follows the process, not this flag.
  const desired = parameters[0]?.enabled ?? false
  const running = motion?.running ?? false
  const configWritten = motion?.configExists ?? false

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Group gap="sm" align="center">
          <Title order={2}>Camera {cameraNumber}</Title>
          {name && (
            <Text c="dimmed" size="lg">
              {name}
            </Text>
          )}
        </Group>
        <Group gap="sm" align="flex-start">
          <Badge color={configWritten ? 'blue' : 'gray'} variant="light">
            {configWritten ? 'Config written' : 'No config yet'}
          </Badge>
          <Stack gap="xs" align="flex-end">
            <Group h={CONTROL_ROW_HEIGHT} align="center" justify="flex-end">
              {motion?.error && (running || desired) ? (
                <Badge color="red" variant="light" title={motion.error}>
                  Camera unavailable
                </Badge>
              ) : running ? (
                <Badge color="green" variant="light">
                  Motion running (pid {motion.pid})
                </Badge>
              ) : desired ? (
                <Badge color="yellow" variant="light">
                  Motion down - restarting
                </Badge>
              ) : (
                <Badge color="gray" variant="light">
                  Motion stopped
                </Badge>
              )}
            </Group>
            <Group h={CONTROL_ROW_HEIGHT} align="center" justify="flex-end">
              {audio?.running ? (
                <Badge color="green" variant="light">
                  Audio processing (pid {audio.pid})
                </Badge>
              ) : audio?.enabled ? (
                <Badge color="yellow" variant="light">
                  Audio down - restarting
                </Badge>
              ) : (
                <Badge color="gray" variant="light">
                  Audio stopped
                </Badge>
              )}
            </Group>
          </Stack>
          <Button
            variant="default"
            leftSection={<PlusIcon />}
            onClick={startAdd}
            disabled={loading || adding}
          >
            Add Row
          </Button>
          <Stack gap="xs" align="flex-end">
            <Button onClick={apply} loading={applying} disabled={loading}>
              Start/Apply
            </Button>
            <Button
              variant="default"
              onClick={toggleAudio}
              loading={audioBusy}
              disabled={loading}
            >
              {audio?.running ? 'Stop audio' : 'Start audio'}
            </Button>
          </Stack>
          <Button
            onClick={stop}
            loading={stopping}
            disabled={loading || (!running && !desired)}
            variant="default"
          >
            Stop
          </Button>
        </Group>
      </Group>

      {motion?.error && (running || desired) && (
        <Alert color="red" variant="light">
          {motion.error}
        </Alert>
      )}

      {error && (
        <Alert color="red" variant="light">
          {error}
        </Alert>
      )}

      {loading && (
        <Center>
          <Loader size="sm" />
        </Center>
      )}

      {!loading && !adding && parameters.length === 0 && (
        <Text c="dimmed">This camera has no parameters.</Text>
      )}

      {!loading && (adding || parameters.length > 0) && (
        <Table.ScrollContainer minWidth={560}>
          <Table striped highlightOnHover withTableBorder>
            <Table.Thead>
              <Table.Tr>
                <Table.Th w={200}>Parameter</Table.Th>
                <Table.Th>Value</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {adding && (
                <Table.Tr>
                  <Table.Td>
                    <TextInput
                      autoFocus
                      name="new-parameter-name"
                      placeholder="option_name"
                      aria-label="New parameter name"
                      styles={{ input: { fontFamily: 'monospace' } }}
                      value={newKey}
                      onChange={(event) => setNewKey(event.currentTarget.value)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          addParameter()
                        }
                      }}
                    />
                  </Table.Td>
                  <Table.Td>
                    <Group align="center" gap="sm" wrap="nowrap">
                      <TextInput
                        name="new-parameter-value"
                        placeholder="value"
                        aria-label="New parameter value"
                        style={{ flex: 1 }}
                        value={newValue}
                        onChange={(event) => setNewValue(event.currentTarget.value)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            addParameter()
                          }
                        }}
                      />
                      <Button onClick={addParameter} loading={savingNew}>
                        Add
                      </Button>
                      <Button variant="subtle" color="gray" onClick={cancelAdd}>
                        Cancel
                      </Button>
                    </Group>
                  </Table.Td>
                </Table.Tr>
              )}
              {parameters.map((parameter) => (
                <Table.Tr key={parameter.key}>
                  <Table.Td>
                    <Text ff="monospace" size="sm">
                      {parameter.key}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Group align="center" gap="sm" wrap="nowrap">
                      <TextInput
                        id={`camera-${cameraNumber}-${parameter.key}`}
                        aria-label={parameter.key}
                        style={{ flex: 1 }}
                        value={drafts[parameter.key] ?? ''}
                        onChange={(event) => {
                          // Read the value before calling setDrafts: the updater
                          // runs later, after React has reset currentTarget.
                          const { value } = event.currentTarget
                          setDrafts((current) => ({ ...current, [parameter.key]: value }))
                        }}
                      />
                      <Button
                        onClick={() => saveValue(parameter.key)}
                        loading={savingKey === parameter.key}
                        disabled={drafts[parameter.key] === parameter.value}
                      >
                        Save
                      </Button>
                      {savedKey === parameter.key && (
                        <Badge color="green" variant="light">
                          Saved
                        </Badge>
                      )}
                      <ActionIcon
                        color="red"
                        variant="subtle"
                        aria-label={`Delete ${parameter.key}`}
                        title={`Delete ${parameter.key}`}
                        loading={deletingKey === parameter.key}
                        disabled={deletingKey !== null && deletingKey !== parameter.key}
                        onClick={() => deleteValue(parameter.key)}
                      >
                        <TrashIcon />
                      </ActionIcon>
                    </Group>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}
    </Stack>
  )
}
