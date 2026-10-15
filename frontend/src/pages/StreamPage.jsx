import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Box, Center, Group, Loader, Select, Stack, Switch, Title } from '@mantine/core'
import StreamPlayer from '../components/StreamPlayer'
import { api } from '../api'

// The landing page. It plays Motion's published stream for the selected camera:
// the host comes from the `stream_url` setting and the port from that camera's
// `stream_port` row (8081 for Camera 1, 8082 for Camera 2), so the feed shown is
// the one Motion is producing and recording. Both cameras are always selectable;
// the default is the first enabled one (Camera 1, else Camera 2). A switched-off
// camera has no process, so the player shows its "no stream" state until the
// Enabled switch turns it back on -- which starts Motion immediately.
const DEFAULT_CAMERA = 1

export default function StreamPage() {
  const [parameters, setParameters] = useState([])
  const [settings, setSettings] = useState([])
  const [selected, setSelected] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [savingEnabled, setSavingEnabled] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [list, config] = await Promise.all([api.listCameras(), api.listSettings()])
      setParameters(list)
      setSettings(config)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  // The host serving Motion's streams, without a trailing slash: the port is
  // appended per camera. Null until the setting exists, which disables playback.
  const streamBase = useMemo(() => {
    const setting = settings.find((entry) => entry.tag === 'stream_url')
    return setting && setting.value ? setting.value.replace(/\/+$/, '') : null
  }, [settings])

  // The endpoint returns one row per parameter across every camera, so roll
  // them up into an entry per camera holding what the player and switch need.
  const cameras = useMemo(() => {
    const map = new Map()
    for (const parameter of parameters) {
      const entry = map.get(parameter.cameraNumber) ?? {
        cameraNumber: parameter.cameraNumber,
        enabled: parameter.enabled,
        name: null,
        streamPort: null,
      }
      entry.enabled = parameter.enabled
      if (parameter.key === 'camera_name') {
        entry.name = parameter.value
      }
      if (parameter.key === 'stream_port') {
        entry.streamPort = parameter.value
      }
      map.set(parameter.cameraNumber, entry)
    }
    return [...map.values()].sort((a, b) => a.cameraNumber - b.cameraNumber)
  }, [parameters])

  // The default is Camera 1 while it is switched on, otherwise Camera 2 when
  // that one is on. With both off there is nothing to play: the choice stays on
  // Camera 1 and the player shows its "no stream" state.
  const defaultCameraNumber =
    cameras.find((camera) => camera.enabled)?.cameraNumber ?? DEFAULT_CAMERA

  useEffect(() => {
    if (selected === null && cameras.length > 0) {
      setSelected(String(defaultCameraNumber))
    }
  }, [selected, cameras.length, defaultCameraNumber])

  // Every camera is offered, enabled or not: picking a switched-off one just
  // shows the player's "no stream" state, where the Enabled switch can turn it
  // back on.
  const options = cameras.map((camera) => ({
    value: String(camera.cameraNumber),
    label: camera.name ? `Camera ${camera.cameraNumber} (${camera.name})` : `Camera ${camera.cameraNumber}`,
  }))

  const selectedCamera = cameras.find((camera) => String(camera.cameraNumber) === selected)
  // A switched-off camera, or one with no host/port, has nothing to play.
  const url =
    selectedCamera?.enabled && streamBase && selectedCamera.streamPort
      ? `${streamBase}:${selectedCamera.streamPort}`
      : null

  // Switch the selected camera on or off. The flag is stored on every one of
  // its rows in babymonitor.cameras, and the server starts or kills Motion to
  // match, so swap those rows for the server's response.
  const toggleEnabled = async (enabled) => {
    if (!selectedCamera) {
      return
    }
    setError(null)
    setSavingEnabled(true)
    try {
      const updated = await api.updateCameraEnabled(selectedCamera.cameraNumber, enabled)
      setParameters((current) => [
        ...current.filter((p) => p.cameraNumber !== selectedCamera.cameraNumber),
        ...updated,
      ])
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingEnabled(false)
    }
  }

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Title order={2}>Live stream</Title>
        <Group gap="lg" align="center">
          <Switch
            label="Enabled"
            checked={selectedCamera?.enabled ?? false}
            disabled={loading || savingEnabled || !selectedCamera}
            onChange={(event) => toggleEnabled(event.currentTarget.checked)}
          />
          <Select
            aria-label="Camera"
            data={options}
            value={selected}
            onChange={(value) => value && setSelected(value)}
            allowDeselect={false}
            disabled={loading || options.length === 0}
            w={200}
          />
        </Group>
      </Group>

      {error && (
        <Alert color="red" variant="light">
          {error}
        </Alert>
      )}

      <Box className="stream-frame">
        {loading ? (
          <Center>
            <Loader size="sm" />
          </Center>
        ) : (
          <StreamPlayer url={url} />
        )}
      </Box>
    </Stack>
  )
}
