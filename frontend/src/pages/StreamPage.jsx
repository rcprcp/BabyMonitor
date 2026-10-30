import { Fragment, useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Button, Center, Group, Loader, Stack, Text, Title } from '@mantine/core'
import { Link as RouterLink } from 'react-router-dom'
import StreamPlayer from '../components/StreamPlayer'
import AudioListener from '../components/AudioListener'
import { api } from '../api'

// The landing page. It plays Motion's published stream for the selected camera:
// the host comes from the `stream_url` setting and the port from that camera's
// `stream_port` row (8081 for Camera 1, 8082 for Camera 2), so the feed shown is
// the one Motion is producing and recording. The default is the first enabled
// camera (Camera 1, else Camera 2). A stopped camera's button is disabled and
// labelled as stopped; its page is where it can be started again.
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
  // the first available camera (even if stopped, so the page can explain how to
  // start it), and otherwise Camera 1.
  const defaultCameraNumber =
    cameras.find((camera) => camera.enabled)?.cameraNumber ??
    cameras[0]?.cameraNumber ??
    DEFAULT_CAMERA

  useEffect(() => {
    if (selected === null && cameras.length > 0) {
      setSelected(String(defaultCameraNumber))
    }
  }, [selected, cameras.length, defaultCameraNumber])

  // Stopped cameras stay visible in the picker but cannot be selected; their
  // grayed button says so, and the selected-camera notice links to the page
  // where it can be started.
  const chooseCamera = (value) => {
    setSelected(value)
    api.updateActiveCamera(value).catch(() => {}) // save; non-fatal
  }

  const selectedCamera = cameras.find((camera) => String(camera.cameraNumber) === selected)
  const selectedName = selectedCamera?.name?.trim()
    ? selectedCamera.name.trim()
    : selectedCamera
      ? `Camera ${selectedCamera.cameraNumber}`
      : null
  // A stopped camera, or one with no host/port, has nothing to play. A stopped
  // camera gets an explanation and a link to its page instead of a black frame.
  const url =
    selectedCamera?.enabled && streamBase && selectedCamera.streamPort
      ? `${streamBase}:${selectedCamera.streamPort}`
      : null

  // Switching a camera on starts Motion on the server. Give the MJPEG stream a
  // chance to accept connections a moment after the process is up, before the
  // player tries (and retries) to load it. A camera is brought up from its
  // Camera page's Restart camera, so this is the stream catching up.
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
        <Title order={2}>{selectedName ? `Live stream — ${selectedName}` : 'Live stream'}</Title>
        <Group gap={0} justify="flex-end" align="center">
          {cameras.map((camera, position) => {
            const label = camera.name
              ? `${camera.name} (Camera${camera.cameraNumber})`
              : `(Camera${camera.cameraNumber})`
            return (
              <Fragment key={camera.cameraNumber}>
                {position > 0 && (
                  <Text c="dimmed" mx="sm" aria-hidden="true">
                    -
                  </Text>
                )}
                <Button
                  size="sm"
                  variant={String(camera.cameraNumber) === selected ? 'filled' : 'default'}
                  aria-pressed={String(camera.cameraNumber) === selected}
                  onClick={() => chooseCamera(String(camera.cameraNumber))}
                  disabled={loading || !camera.enabled}
                  title={camera.enabled ? undefined : 'This camera is stopped'}
                >
                  {camera.enabled ? label : `${label} · stopped`}
                </Button>
              </Fragment>
            )
          })}
        </Group>
      </Group>

      {error && (
        <Alert color="red" variant="light">
          {error}
        </Alert>
      )}

      {loading ? (
        <Center>
          <Loader size="sm" />
        </Center>
      ) : selectedCamera && !selectedCamera.enabled ? (
        <Alert color="yellow" variant="light" title={`${selectedName} is stopped`}>
          <Stack gap="xs" align="flex-start">
            <Text size="sm">
              There is no live picture while the camera is stopped. Start it from its camera page,
              and check the stream port there if it does not come back.
            </Text>
            <Button
              component={RouterLink}
              to={`/cameras/${selectedCamera.cameraNumber}`}
              size="xs"
              variant="light"
            >
              Open camera settings
            </Button>
          </Stack>
        </Alert>
      ) : (
        <StreamPlayer url={url} reloadKey={reloadKey} />
      )}

      {selectedCamera && <AudioListener cameraNumber={selectedCamera.cameraNumber} />}
    </Stack>
  )
}
