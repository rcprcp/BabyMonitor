import { useCallback, useEffect, useState } from 'react'
import {
  ActionIcon,
  Alert,
  Autocomplete,
  Badge,
  Button,
  Center,
  Checkbox,
  Divider,
  Group,
  Loader,
  NumberInput,
  Paper,
  Select,
  SimpleGrid,
  Stack,
  Switch,
  Table,
  Text,
  TextInput,
  Title,
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

// The resolutions a parent actually recognises. Anything else is kept as the
// current "custom" size and can still be edited under Advanced.
const RESOLUTIONS = [
  { value: '640x480', label: '640 × 480 (VGA)', width: '640', height: '480' },
  { value: '1280x720', label: '1280 × 720 (720p)', width: '1280', height: '720' },
  { value: '1920x1080', label: '1920 × 1080 (1080p)', width: '1920', height: '1080' },
]

// Changing any of these needs Motion to reload. The name does not: it is only a
// label and Motion ignores it.
const RESTART_KEYS = new Set(['video_device', 'width', 'height', 'framerate', 'stream_port'])

// One camera's page. The same component backs both cameras; the number arrives
// as a prop.
//
// The default view is a friendly form for the five things a parent or installer
// actually sets, plus the camera's power and audio preference. Every Motion
// option, the generated-config status, the pids and a manual audio-only start
// live under Advanced, because choosing alert sounds or a resolution should not
// require reading `motion.config`.
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

  // The friendly form. It is filled once per camera, not on every parameter
  // change, so restarts and Advanced edits cannot wipe unsaved typing.
  const [details, setDetails] = useState({
    name: '',
    device: '',
    width: '',
    height: '',
    framerate: '',
    streamPort: '',
  })
  const [devices, setDevices] = useState([])
  const [savingDetails, setSavingDetails] = useState(false)
  const [detailsSaved, setDetailsSaved] = useState(false)
  const [needsRestart, setNeedsRestart] = useState(false)

  // The "add a row" form in Advanced. Its values live here rather than in the
  // parameters list, so cancelling leaves nothing behind.
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
        setAudio(await api.getCameraAudio(cameraNumber))
      } catch {
        setAudio(null)
      }
      setParameters(list)
      setDrafts(Object.fromEntries(list.map((p) => [p.key, p.value])))
      setDetails(friendlyDetails(list))
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

  // Detected video devices, for the device field's suggestions. An empty list
  // just means the path has to be typed.
  useEffect(() => {
    let cancelled = false
    api
      .listVideoDevices()
      .then((list) => {
        if (!cancelled) {
          setDevices(list ?? [])
        }
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  // The audio status has to reflect the process, not just the last click: the
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
      setDetails((current) => withParameter(current, key, updated.value))
      setSavedKey(key)
      if (running && RESTART_KEYS.has(key)) {
        setNeedsRestart(true)
      }
      notifyCamerasChanged()
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingKey(null)
    }
  }

  // One save for the whole friendly form: only changed rows are written. A
  // change that Motion only reads at startup leaves a Restart prompt instead of
  // restarting behind the user's back.
  const saveDetails = async () => {
    setError(null)
    const name = details.name.trim()
    const device = details.device.trim()
    const framerate = Number(details.framerate)
    const streamPort = Number(details.streamPort)
    if (!name) {
      setError('Give this camera a name.')
      return
    }
    if (!device) {
      setError('Give this camera a video device, for example /dev/video0.')
      return
    }
    if (!Number.isInteger(framerate) || framerate < 1 || framerate > 60) {
      setError('Frame rate must be between 1 and 60.')
      return
    }
    if (!Number.isInteger(streamPort) || streamPort < 1024 || streamPort > 65535) {
      setError('Stream port must be between 1024 and 65535.')
      return
    }

    const currentValue = (key) => parameters.find((parameter) => parameter.key === key)?.value ?? ''
    const changes = []
    if (name !== currentValue('camera_name')) changes.push(['camera_name', name])
    if (device !== currentValue('video_device')) changes.push(['video_device', device])
    if (String(details.width) !== currentValue('width'))
      changes.push(['width', String(details.width)])
    if (String(details.height) !== currentValue('height')) {
      changes.push(['height', String(details.height)])
    }
    if (String(framerate) !== currentValue('framerate')) {
      changes.push(['framerate', String(framerate)])
    }
    if (String(streamPort) !== currentValue('stream_port')) {
      changes.push(['stream_port', String(streamPort)])
    }

    if (changes.length === 0) {
      setDetailsSaved(true)
      setTimeout(() => setDetailsSaved(false), 3000)
      return
    }

    setSavingDetails(true)
    try {
      const updated = []
      for (const [key, value] of changes) {
        const exists = parameters.some((parameter) => parameter.key === key)
        updated.push(
          exists
            ? await api.updateCameraParameter(cameraNumber, key, value)
            : await api.createCameraParameter(cameraNumber, key, value),
        )
      }
      setParameters((current) =>
        current.map((parameter) => updated.find((row) => row.key === parameter.key) ?? parameter),
      )
      // Keep the Advanced table's drafts in step with what was just written.
      setDrafts((current) => ({
        ...current,
        ...Object.fromEntries(updated.map((row) => [row.key, row.value])),
      }))
      setDetails({
        ...details,
        name,
        device,
        framerate: String(framerate),
        streamPort: String(streamPort),
      })
      notifyCamerasChanged()
      setDetailsSaved(true)
      setTimeout(() => setDetailsSaved(false), 3000)
      setNeedsRestart(running && changes.some(([key]) => RESTART_KEYS.has(key)))
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingDetails(false)
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
      setDetails((current) => withParameter(current, created.key, created.value))
      setAdding(false)
      setNewKey('')
      setNewValue('')
      if (running) {
        setNeedsRestart(true)
      }
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
      setDetails((current) => withParameter(current, key, ''))
      if (running) {
        setNeedsRestart(true)
      }
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
      // it locally so the status line is accurate without a reload.
      setParameters((current) => current.map((p) => ({ ...p, enabled: true })))
      await refreshAudio()
      setNeedsRestart(false)
    } catch (err) {
      setError(err.message)
    } finally {
      setApplying(false)
    }
  }

  // Stop marks the camera not running and kills its process, so the backend
  // supervisor stops watching it. It starts again at the next boot.
  const stop = async () => {
    setError(null)
    setStopping(true)
    try {
      const status = await api.stopCameraMotion(cameraNumber)
      setMotion(status)
      setParameters((current) => current.map((p) => ({ ...p, enabled: false })))
      await refreshAudio()
      setNeedsRestart(false)
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

  const name = details.name || parameters.find((p) => p.key === 'camera_name')?.value
  // The persisted desired state: set when the camera is switched on, cleared by
  // Stop. The status line follows the processes, not this flag.
  const desired = parameters[0]?.enabled ?? false
  const running = motion?.running ?? false
  const configWritten = motion?.configExists ?? false
  // One control owns both processes. `on` reflects either the running camera or
  // the persisted "should be running" state, so a camera that is still starting
  // does not flicker off.
  const on = running || desired
  const busy = loading || applying || stopping

  const setPower = (enabled) => (enabled ? apply() : stop())

  // One status line instead of a row of chips: "Recording · Audio on".
  const recordingState =
    motion?.error && on
      ? 'Camera unavailable'
      : running
        ? 'Recording'
        : desired
          ? 'Recording down — restarting'
          : 'Stopped'
  const audioState =
    running && audio
      ? audio.running
        ? 'Audio on'
        : audio.enabled
          ? 'Audio down — restarting'
          : 'Audio off'
      : null
  const summary = audioState ? `${recordingState} · ${audioState}` : recordingState
  const summaryColor =
    motion?.error && on
      ? 'red'
      : running
        ? audio && audio.enabled && !audio.running
          ? 'yellow'
          : 'green'
        : desired
          ? 'yellow'
          : 'gray'

  const resolution = RESOLUTIONS.find(
    (candidate) =>
      candidate.width === String(details.width) && candidate.height === String(details.height),
  )
  const resolutionData = RESOLUTIONS.map((candidate) => ({
    value: candidate.value,
    label: candidate.label,
  }))
  if (!resolution && details.width && details.height) {
    resolutionData.unshift({
      value: 'current',
      label: `${details.width} × ${details.height} (current)`,
    })
  }

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
        <Badge color={summaryColor} variant="light" size="lg">
          {summary}
        </Badge>
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

      {needsRestart && (
        <Alert color="blue" variant="light">
          <Group justify="space-between" align="center" wrap="nowrap">
            <Text size="sm">
              Saved. Restart the camera to use the new device, resolution, frame rate or stream
              port.
            </Text>
            <Button size="xs" onClick={apply} loading={applying}>
              Restart camera
            </Button>
          </Group>
        </Alert>
      )}

      {loading ? (
        <Center>
          <Loader size="sm" />
        </Center>
      ) : (
        <>
          <Paper withBorder p="md">
            <Stack gap="md">
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

              <Divider />

              <Group justify="space-between" align="center" wrap="nowrap">
                <Stack gap={2}>
                  <Text fw={600}>Listen for cries</Text>
                  <Text size="sm" c="dimmed">
                    {!on
                      ? 'Turn the camera on to change its audio.'
                      : audio?.enabled
                        ? 'Audio alerts need this on. Recording happens either way.'
                        : 'Recording happens either way. Listening starts again when the monitor restarts.'}
                  </Text>
                </Stack>
                <Checkbox
                  aria-label="Listen for cries"
                  checked={Boolean(audio?.enabled)}
                  onChange={(event) => setListening(event.currentTarget.checked)}
                  disabled={!on || audioBusy || loading}
                />
              </Group>
            </Stack>
          </Paper>

          <Paper withBorder p="md">
            <Stack gap="md">
              <Text fw={600}>Camera settings</Text>
              <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md">
                <TextInput
                  label="Camera name"
                  value={details.name}
                  onChange={(event) =>
                    setDetails((current) => ({ ...current, name: event.currentTarget.value }))
                  }
                  maxLength={120}
                />
                <Autocomplete
                  label="Camera device"
                  placeholder="/dev/video0"
                  description={
                    devices.length > 0
                      ? `Detected: ${devices
                          .map(
                            (device) => `${device.device}${device.name ? ` (${device.name})` : ''}`,
                          )
                          .join(', ')}`
                      : 'Type the /dev/video* node. No devices were detected.'
                  }
                  data={devices.map((device) => device.device)}
                  value={details.device}
                  onChange={(value) => setDetails((current) => ({ ...current, device: value }))}
                />
                <Select
                  label="Resolution"
                  description="The camera must support the size it is sent."
                  data={resolutionData}
                  value={resolution?.value ?? (details.width && details.height ? 'current' : null)}
                  onChange={(value) => {
                    const chosen = RESOLUTIONS.find((candidate) => candidate.value === value)
                    if (chosen) {
                      setDetails((current) => ({
                        ...current,
                        width: chosen.width,
                        height: chosen.height,
                      }))
                    }
                  }}
                  allowDeselect={false}
                />
                <NumberInput
                  label="Frame rate"
                  description="Frames per second."
                  min={1}
                  max={60}
                  value={details.framerate}
                  onChange={(value) =>
                    setDetails((current) => ({ ...current, framerate: String(value ?? '') }))
                  }
                />
                <NumberInput
                  label="Stream port"
                  description="Where this camera's MJPEG stream is served."
                  min={1024}
                  max={65535}
                  value={details.streamPort}
                  onChange={(value) =>
                    setDetails((current) => ({ ...current, streamPort: String(value ?? '') }))
                  }
                />
              </SimpleGrid>
              <Group justify="flex-end" gap="sm">
                {detailsSaved && (
                  <Badge color="green" variant="light">
                    Saved
                  </Badge>
                )}
                <Button onClick={saveDetails} loading={savingDetails}>
                  Save changes
                </Button>
              </Group>
            </Stack>
          </Paper>

          {/* Everything Motion-specific lives here: option names, paths, pids and
              the generated-config status are installer material, not daily use. */}
          <Paper withBorder p="md" component="details">
            <Text component="summary" fw={600} style={{ cursor: 'pointer' }}>
              Advanced
            </Text>
            <Stack gap="md" mt="md">
              <Group gap="lg">
                <Text size="sm">
                  Motion: {running ? `running (pid ${motion.pid ?? 'unknown'})` : 'not running'}
                </Text>
                <Text size="sm">
                  Audio:{' '}
                  {audio?.running
                    ? `capturing (pid ${audio.pid ?? 'unknown'})`
                    : audio?.enabled
                      ? 'down — restarting'
                      : 'off'}
                </Text>
                <Text size="sm">Config: {configWritten ? 'written' : 'not written yet'}</Text>
              </Group>

              <Group justify="space-between" align="center" wrap="nowrap">
                <Text size="sm" c="dimmed">
                  Audio can be started on its own to test the microphone. It is not restarted
                  automatically while the camera is stopped.
                </Text>
                <Button
                  variant="default"
                  size="xs"
                  onClick={() => setListening(!audio?.running)}
                  loading={audioBusy}
                >
                  {audio?.running ? 'Stop audio' : 'Start audio'}
                </Button>
              </Group>

              <Divider />

              <Group justify="space-between" align="center">
                <Text fw={600}>Motion options</Text>
                <Button
                  variant="default"
                  leftSection={<PlusIcon />}
                  onClick={startAdd}
                  disabled={adding}
                >
                  Add Row
                </Button>
              </Group>

              {!adding && parameters.length === 0 && (
                <Text c="dimmed">This camera has no parameters.</Text>
              )}

              {(adding || parameters.length > 0) && (
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
                                  // Read the value before calling setDrafts: the
                                  // updater runs later, after React has reset
                                  // currentTarget.
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
          </Paper>
        </>
      )}
    </Stack>
  )
}

/** The five friendly fields, read from the camera parameter rows. */
function friendlyDetails(rows) {
  const valueOf = (key) => rows.find((row) => row.key === key)?.value ?? ''
  return {
    name: valueOf('camera_name'),
    device: valueOf('video_device'),
    width: valueOf('width'),
    height: valueOf('height'),
    framerate: valueOf('framerate'),
    streamPort: valueOf('stream_port'),
  }
}

// Which field of the friendly form a raw parameter feeds, so an Advanced edit
// cannot leave the form showing a stale value.
const DETAIL_FIELDS = {
  camera_name: 'name',
  video_device: 'device',
  width: 'width',
  height: 'height',
  framerate: 'framerate',
  stream_port: 'streamPort',
}

function withParameter(details, key, value) {
  const field = DETAIL_FIELDS[key]
  return field ? { ...details, [field]: value } : details
}
