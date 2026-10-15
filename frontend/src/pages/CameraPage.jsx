import { useCallback, useEffect, useState } from 'react'
import {
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
} from '@mantine/core'
import { api } from '../api'

// One camera's parameters, from babymonitor.cameras. The same page backs both
// menu entries, "Camera1" and "Camera2": the camera number arrives as a prop.
// The parameter names are Motion option names; editing one only stores it, and
// the Apply button rebuilds the camera's Motion config from the current rows and
// starts the process (or reloads it with SIGHUP when it is already running).
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

      {!loading && parameters.length === 0 && (
        <Text c="dimmed">This camera has no parameters.</Text>
      )}

      {!loading && parameters.length > 0 && (
        <Table.ScrollContainer minWidth={560}>
          <Table striped highlightOnHover withTableBorder>
            <Table.Thead>
              <Table.Tr>
                <Table.Th w={200}>Parameter</Table.Th>
                <Table.Th>Value</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
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
