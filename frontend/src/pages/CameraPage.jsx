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
  Switch,
  Table,
  Text,
  TextInput,
  Title,
  Tooltip,
} from '@mantine/core'
import { api } from '../api'

// One camera's parameters, from babymonitor.cameras. The same page backs both
// menu entries, "Camera1" and "Camera2": the camera number arrives as a prop.
// The parameter names are Motion option names; editing one only stores it, and
// the Apply button rebuilds the camera's Motion config from the current rows and
// starts the process (or reloads it with SIGHUP when it is already running).
// The Add Row button appends a row and each row's trash-can button removes
// one; like editing, both only touch the table, so Apply is still what reaches
// Motion.
// The Enabled switch is separate and immediate: on starts Motion, off kills it.
export default function CameraPage({ cameraNumber }) {
  const [parameters, setParameters] = useState([])
  const [drafts, setDrafts] = useState({})
  const [motion, setMotion] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [savingKey, setSavingKey] = useState(null)
  const [savedKey, setSavedKey] = useState(null)
  const [savingEnabled, setSavingEnabled] = useState(false)
  const [applying, setApplying] = useState(false)

  // The "add a row" form. Its values live here rather than in the parameters
  // list so a cancelled form leaves nothing behind.
  const [adding, setAdding] = useState(false)
  const [newKey, setNewKey] = useState('')
  const [newValue, setNewValue] = useState('')
  const [savingNew, setSavingNew] = useState(false)
  const [deletingKey, setDeletingKey] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [list, status] = await Promise.all([
        api.getCamera(cameraNumber),
        api.getCameraMotion(cameraNumber),
      ])
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

  const removeParameter = async (key) => {
    setError(null)
    setDeletingKey(key)
    try {
      await api.deleteCameraParameter(cameraNumber, key)
      setParameters((current) => current.filter((parameter) => parameter.key !== key))
      setDrafts((current) => {
        const next = { ...current }
        delete next[key]
        return next
      })
      if (savedKey === key) {
        setSavedKey(null)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setDeletingKey(null)
    }
  }

  const apply = async () => {
    setError(null)
    setApplying(true)
    try {
      const status = await api.applyCameraMotion(cameraNumber)
      setMotion(status)
    } catch (err) {
      setError(err.message)
    } finally {
      setApplying(false)
    }
  }

  const toggleEnabled = async (next) => {
    setError(null)
    setSavingEnabled(true)
    try {
      const updated = await api.updateCameraEnabled(cameraNumber, next)
      setParameters(updated)
      setDrafts(Object.fromEntries(updated.map((p) => [p.key, p.value])))
      setMotion(await api.getCameraMotion(cameraNumber))
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingEnabled(false)
    }
  }

  const name = parameters.find((p) => p.key === 'camera_name')?.value
  // The flag is repeated on every row of the camera, so any row carries it.
  const enabled = parameters[0]?.enabled ?? false
  const running = motion?.running ?? false

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
          <Badge color={enabled ? 'green' : 'gray'} variant="light">
            {enabled ? 'Enabled' : 'Disabled'}
          </Badge>
        </Group>
        <Group gap="sm" align="center">
          <Badge color={running ? 'green' : 'gray'} variant="light">
            {running ? 'Motion running' : 'Motion stopped'}
          </Badge>
          <Button
            variant="default"
            leftSection={<PlusIcon />}
            onClick={startAdd}
            disabled={loading || adding}
          >
            Add Row
          </Button>
          <Button onClick={apply} loading={applying} disabled={loading}>
            Apply
          </Button>
        </Group>
      </Group>

      <Group>
        <Switch
          checked={enabled}
          disabled={loading || savingEnabled}
          label="Enabled"
          onChange={(event) => toggleEnabled(event.currentTarget.checked)}
        />
      </Group>

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
                      <Tooltip label={`Delete ${parameter.key}`}>
                        <ActionIcon
                          color="red"
                          variant="subtle"
                          aria-label={`Delete ${parameter.key}`}
                          loading={deletingKey === parameter.key}
                          onClick={() => removeParameter(parameter.key)}
                        >
                          <TrashIcon />
                        </ActionIcon>
                      </Tooltip>
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

// The + and the trash can are drawn inline so the page does not have to pull in
// an icon package; both inherit the surrounding text colour.
function PlusIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

function TrashIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M3 6h18" />
      <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
      <path d="M10 11v6M14 11v6" />
    </svg>
  )
}
