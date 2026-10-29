import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Box, Center, Group, Loader, Select, Stack, Title } from '@mantine/core'
import StreamPlayer from '../components/StreamPlayer'
import AudioListener from '../components/AudioListener'
import { api } from '../api'

// The landing page. It plays Motion's published stream for the selected camera:
// the host comes from the `stream_url` setting and the port from that camera's
// `stream_port` row (8081 for Camera 1, 8082 for Camera 2), so the feed shown is
// the one Motion is producing and recording. Both cameras are always selectable;
// the default is the first enabled one (Camera 1, else Camera 2). A stopped
// camera has no process, so the player shows its "no stream" state; start it
// back on from that camera's page.
const DEFAULT_CAMERA = 1

export default function StreamPage() {
  const [parameters, setParameters] = useState([])
  const [settings, setSettings] = useState([])
  const [selected, setSelected] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [reloadKey, setReloadKey] = useState(0)

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

  // Restore the user's last-viewed camera from the database profile on mount,
  // so reloads and cross-device navigation pick up where they left off.
  useEffect(() => {
    api
      .getProfile()
      .then((profile) => {
        if (profile && profile.activeCamera) {
          const value = String(profile.activeCamera).trim()
          if (/^\d+$/.test(value) && parseInt(value, 10) >= 1) {
            setSelected(value)
            // Persist for the next visit (the SELECT may not have fired yet).
            api.updateActiveCamera(value).catch(() => {})
          }
        }
      })
      .catch(() => {}) // profile fetch failure is non-fatal; fall through to defaults
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

  // The default is Camera 1 if it is marked running/should-be-running, otherwise
  // Camera 2 when that one is marked running, and otherwise Camera 1. A stopped
  // camera has no process, so the player shows its "no stream" state; start it
  // from that camera's page.
  const defaultCameraNumber =
    cameras.find((camera) => camera.enabled)?.cameraNumber ?? DEFAULT_CAMERA

  useEffect(() => {
    if (selected === null && cameras.length > 0) {
      setSelected(String(defaultCameraNumber))
    }
  }, [selected, cameras.length, defaultCameraNumber])

  // Every camera is offered, enabled or not: picking a stopped one just shows
  // the player's "no stream" state; it can be started from that camera's page.
  const options = cameras.map((camera) => ({
    value: String(camera.cameraNumber),
    label: camera.name
      ? `Camera ${camera.cameraNumber} (${camera.name})`
      : `Camera ${camera.cameraNumber}`,
  }))

  const selectedCamera = cameras.find((camera) => String(camera.cameraNumber) === selected)
  // A stopped camera, or one with no host/port, has nothing to play. The page
  // still renders the selector and player so the user can start it.
  const url =
    selectedCamera?.enabled && streamBase && selectedCamera.streamPort
      ? `${streamBase}:${selectedCamera.streamPort}`
      : null

  // Switching a camera on starts Motion on the server. Give the MJPEG stream a
  // chance to accept connections a moment after the process is up, before the
  // player tries (and retries) to load it. A camera is brought up from its
  // Camera page's Start/Apply, so this is the stream catching up.
  useEffect(() => {
    if (!selectedCamera?.enabled) {
      setReloadKey(0)
      return
    }
    let cancelled = false
    const timer = setTimeout(() => {
      if (cancelled) return
      setReloadKey((k) => k + 1)
    }, 800)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [selectedCamera?.cameraNumber, selectedCamera?.enabled])

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Title order={2}>Live stream</Title>
        <Select
          aria-label="Camera"
          data={options}
          value={selected}
          onChange={(value) => {
            if (value) {
              setSelected(value)
              api.updateActiveCamera(value).catch(() => {}) // save; non-fatal
            }
          }}
          allowDeselect={false}
          disabled={loading || options.length === 0}
          w={200}
        />
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
          <StreamPlayer url={url} reloadKey={reloadKey} />
        )}
      </Box>

      {selectedCamera && <AudioListener cameraNumber={selectedCamera.cameraNumber} />}
    </Stack>
  )
}
