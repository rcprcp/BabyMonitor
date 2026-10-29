import { useCallback, useEffect, useState } from 'react'
import {
  ActionIcon,
  Alert,
  Badge,
  Button,
  Center,
  Checkbox,
  Divider,
  Group,
  Loader,
  Paper,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  Title,
  Tooltip,
} from '@mantine/core'
import { api } from '../api'
import { notifyCamerasChanged } from '../useCameras'

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
// row only touch the table: starting or restarting the camera is what rebuilds
// config and reaches Motion.
//
// Pressing it marks the camera as running, rebuilds its config and starts
// Motion (or reloads it if it is already up). The button says "Start camera"
// or "Restart camera" depending on that persisted state, so the label describes
// what pressing it will do. Stop marks it not running and kills the process.
// The backend's MotionSupervisor keeps a camera that is marked running alive:
// if its process dies it is restarted and a System notification is recorded.
// Startup is cold, so a camera comes up only from here.
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
      notifyCamerasChanged()
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
      setParameters((current) => [...current, created].sort((a, b) => a.key.localeCompare(b.key)))
      setDrafts((current) => ({ ...current, [created.key]: created.value }))
      setAdding(false)
      setNewKey('')
      setNewValue('')
      notifyCamerasChanged()
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
      notifyCamerasChanged()
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
      // Starting or restarting marks the camera as running on the server; mirror
      // it locally so the "restarting" hint below is accurate without a reload.
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

  // "Listen for cries" is a preference stored on the camera: starting audio
  // also remembers it, so the next start of the camera honours the choice.
  const setListening = async (enabled) => {
    setError(null)
    setAudioBusy(true)
    try {
      const status = enabled
        ? await api.startCameraAudio(cameraNumber)
        : await api.stopCameraAudio(cameraNumber)
      setAudio(status)
    } catch (err) {
      setError(err.message)
      await refreshAudio()
    } finally {
      setAudioBusy(false)
    }
  }

  const name = parameters.find((p) => p.key === 'camera_name')?.value
  // The persisted desired state: set when the camera is switched on, cleared by
  // Stop. The chips follow the processes, not this flag.
  const desired = parameters[0]?.enabled ?? false
  const running = motion?.running ?? false
  const configWritten = motion?.configExists ?? false
  // One control owns both processes. `on` reflects either the running camera or
  // the persisted "should be running" state, so a camera that is still starting
  // does not flicker off.
  const on = running || desired
  const busy = loading || applying || stopping

  const setPower = (enabled) => (enabled ? apply() : stop())

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
        <Group gap="sm" align="center">
          <Badge color={configWritten ? 'blue' : 'gray'} variant="light">
            {configWritten ? 'Config written' : 'No config yet'}
          </Badge>
          {motion?.error && on ? (
            <Badge color="red" variant="light" title={motion.error}>
              Camera unavailable
            </Badge>
          ) : running ? (
            <Tooltip
              label={`Motion process ${motion.pid}`}
              withArrow
              openDelay={200}
              disabled={!motion.pid}
              position="bottom"
            >
              <Badge color="green" variant="light">
                Recording
              </Badge>
            </Tooltip>
          ) : desired ? (
            <Badge color="yellow" variant="light">
              Recording down — restarting
            </Badge>
          ) : (
            <Badge color="gray" variant="light">
              Stopped
            </Badge>
          )}
          {/* Only while recording: a stopped camera cannot have honest audio. */}
          {running &&
            audio &&
            (audio.running ? (
              <Tooltip
                label={`Audio process ${audio.pid}`}
                withArrow
                openDelay={200}
                disabled={!audio.pid}
                position="bottom"
              >
                <Badge color="green" variant="light">
                  Audio on
                </Badge>
              </Tooltip>
            ) : audio?.enabled ? (
              <Badge color="yellow" variant="light">
                Audio down — restarting
              </Badge>
            ) : (
              <Badge color="gray" variant="light">
                Audio off
              </Badge>
            ))}
        </Group>
      </Group>

      {motion?.error && on && (
        <Alert color="red" variant="light">
          {motion.error}
        </Alert>
      )}

      {error && (
        <Alert color="red" variant="light">
          {error}
        </Alert>
      )}

      {!loading && (
        <Paper withBorder p="md">
          <Group justify="space-between" align="center" wrap="nowrap">
            <Stack gap={2}>
              <Text fw={600}>Monitor this camera</Text>
              <Text size="sm" c="dimmed">
                {on
                  ? 'Recording. If the camera drops, it restarts on its own.'
                  : 'Stopped. Cameras start again when the monitor restarts.'}
              </Text>
            </Stack>
            <Switch
              aria-label="Monitor this camera"
              checked={on}
              onChange={(event) => setPower(event.currentTarget.checked)}
              disabled={busy}
            />
          </Group>

          <Divider my="md" />

          <Group justify="space-between" align="center" wrap="nowrap">
            <Stack gap={2}>
              <Text fw={600}>Listen for cries</Text>
              <Text size="sm" c="dimmed">
                {on
                  ? 'Audio alerts need this on. Recording happens either way.'
                  : 'Turn the camera on to change its audio.'}
              </Text>
            </Stack>
            <Checkbox
              aria-label="Listen for cries"
              checked={Boolean(audio?.enabled)}
              onChange={(event) => setListening(event.currentTarget.checked)}
              disabled={!on || audioBusy || loading}
            />
          </Group>
        </Paper>
      )}

      {loading && (
        <Center>
          <Loader size="sm" />
        </Center>
      )}

      {!loading && !adding && parameters.length === 0 && (
        <Text c="dimmed">This camera has no parameters.</Text>
      )}

      {!loading && (
        <Group justify="space-between" align="center">
          <Text fw={600}>Parameters</Text>
          <Button variant="default" leftSection={<PlusIcon />} onClick={startAdd} disabled={adding}>
            Add Row
          </Button>
        </Group>
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

      {/* T3 keeps the manual audio control reachable: an installer can prove the
          microphone works without starting the camera. T2 will fold this into
          the Advanced panel. A native disclosure keeps it keyboard-accessible
          without an animation that could hide the controls. */}
      {!loading && (
        <Paper withBorder p="md" component="details">
          <Text component="summary" fw={600} style={{ cursor: 'pointer' }}>
            Diagnostics
          </Text>
          <Stack gap="xs" mt="md">
            <Text size="sm" c="dimmed">
              Audio can be started on its own to test the microphone. It is not restarted
              automatically while the camera is stopped.
            </Text>
            <Group gap="sm" align="center">
              <Button
                variant="default"
                onClick={() => setListening(!audio?.running)}
                loading={audioBusy}
                disabled={loading}
              >
                {audio?.running ? 'Stop audio' : 'Start audio'}
              </Button>
              <Text size="sm" c="dimmed">
                {audio?.running
                  ? `Capture running (pid ${audio.pid ?? 'unknown'})`
                  : 'Capture not running'}
              </Text>
            </Group>
            <Text size="sm" c="dimmed">
              Motion process: {running ? `pid ${motion.pid ?? 'unknown'}` : 'not running'}
            </Text>
          </Stack>
        </Paper>
      )}
    </Stack>
  )
}
