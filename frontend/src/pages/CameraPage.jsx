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
// The parameter names are Motion option names; editing one only stores it.
//
// One switch owns the camera's enabled flag. Switching it on writes the config
// and starts Motion; switching off kills the process. Startup is cold -- the app
// marks every camera not enabled -- so this switch is where a camera comes up.
// Start/Apply is for after an edit: it rebuilds the config and reloads a running
// process, or starts one if it is down.
export default function CameraPage({ cameraNumber }) {
  const [parameters, setParameters] = useState([])
  const [drafts, setDrafts] = useState({})
  const [motion, setMotion] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [savingKey, setSavingKey] = useState(null)
  const [savedKey, setSavedKey] = useState(null)
  const [savingMotion, setSavingMotion] = useState(false)
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

  // One switch owns the camera's enabled flag. Switching on writes the config
  // and starts Motion; switching off kills it. The flag lives on every row, so
  // the response replaces them all.
  const toggleEnabled = async (next) => {
    setError(null)
    setSavingMotion(true)
    try {
      const rows = await api.updateCameraEnabled(cameraNumber, next)
      setParameters(rows)
      setDrafts(Object.fromEntries(rows.map((p) => [p.key, p.value])))
      setMotion(await api.getCameraMotion(cameraNumber))
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingMotion(false)
    }
  }

  const name = parameters.find((p) => p.key === 'camera_name')?.value
  const enabled = parameters[0]?.enabled ?? false
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
        <Group gap="sm" align="center">
          <Switch
            label="Camera on"
            checked={enabled}
            onChange={(event) => toggleEnabled(event.currentTarget.checked)}
            disabled={loading}
            loading={savingMotion}
          />
          <Badge color={configWritten ? 'blue' : 'gray'} variant="light">
            {configWritten ? 'Config written' : 'No config yet'}
          </Badge>
          <Badge color={running ? 'green' : 'gray'} variant="light">
            {running ? `Motion running (pid ${motion.pid})` : 'Motion stopped'}
          </Badge>
          <Button onClick={apply} loading={applying} disabled={loading}>
            Start/Apply
          </Button>
        </Group>
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
