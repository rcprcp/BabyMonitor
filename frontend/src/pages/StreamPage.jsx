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
// camera that can actually capture. A stopped or unavailable camera's button is
// disabled and labelled; its page is where it can be started or checked.
const DEFAULT_CAMERA = 1
const MOTION_POLL_MS = 5_000

export default function StreamPage() {
  const [parameters, setParameters] = useState([])
  const [settings, setSettings] = useState([])
  const [motionStatuses, setMotionStatuses] = useState({})
  const [selected, setSelected] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [reloadKey, setReloadKey] = useState(0)

  const fetchMotionStatuses = useCallback(async (numbers) => {
    if (numbers.length === 0) {
      return {}
    }
    const entries = await Promise.all(
      numbers.map(async (cameraNumber) => {
        try {
          return [cameraNumber, await api.getCameraMotion(cameraNumber)]
        } catch {
          // A failed status read must not hide a camera that may be fine.
          return [cameraNumber, null]
        }
      }),
    )
    return Object.fromEntries(entries.filter(([, status]) => status))
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [list, config] = await Promise.all([api.listCameras(), api.listSettings()])
      // Resolve availability before showing the player, so a camera whose
      // device is missing never gets a "Live" chip over Motion's placeholder.
      const numbers = [...new Set(list.map((row) => row.cameraNumber))]
      const statuses = await fetchMotionStatuses(numbers)
      setParameters(list)
      setSettings(config)
      setMotionStatuses(statuses)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [fetchMotionStatuses])

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

  const cameraNumbersKey = useMemo(
    () => [...new Set(parameters.map((row) => row.cameraNumber))].sort((a, b) => a - b).join(','),
    [parameters],
  )

  // Motion keeps running when its device disappears, so re-check availability:
  // the placeholder it serves is a valid MJPEG frame and would otherwise look
  // live. A plug-in is picked up without a page reload.
  useEffect(() => {
    if (!cameraNumbersKey) {
      return undefined
    }
    const numbers = cameraNumbersKey.split(',').map(Number)
    let cancelled = false
    const id = setInterval(async () => {
      const statuses = await fetchMotionStatuses(numbers)
      if (!cancelled) {
        setMotionStatuses((previous) => ({ ...previous, ...statuses }))
      }
    }, MOTION_POLL_MS)
    return () => {
      cancelled = true
      clearInterval(id)
    }
  }, [cameraNumbersKey, fetchMotionStatuses])

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
        deviceAvailable: true,
        statusError: null,
      }
      entry.enabled = parameter.enabled
      if (parameter.key === 'camera_name') {
        entry.name = parameter.value
      }
      if (parameter.key === 'stream_port') {
        entry.streamPort = parameter.value
      }
      const status = motionStatuses[parameter.cameraNumber]
      if (status) {
        entry.deviceAvailable = status.deviceAvailable !== false
        entry.statusError = status.error ?? null
      }
      map.set(parameter.cameraNumber, entry)
    }
    return [...map.values()].sort((a, b) => a.cameraNumber - b.cameraNumber)
  }, [parameters, motionStatuses])

  // The default is the first running, available camera; if none is available,
  // the first enabled one so the page can explain why.
  const defaultCameraNumber =
    cameras.find((camera) => camera.enabled && camera.deviceAvailable)?.cameraNumber ??
    cameras.find((camera) => camera.enabled)?.cameraNumber ??
    cameras[0]?.cameraNumber ??
    DEFAULT_CAMERA

  useEffect(() => {
    if (selected === null && cameras.length > 0) {
      setSelected(String(defaultCameraNumber))
    }
  }, [selected, cameras.length, defaultCameraNumber])

  // Stopped and unavailable cameras stay visible in the picker but cannot be
  // selected; their grayed button says which, and the selected-camera notice
  // links to the page where it can be started or checked.
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
  const selectedStopped = Boolean(selectedCamera && !selectedCamera.enabled)
  const selectedUnavailable = Boolean(
    selectedCamera?.enabled && selectedCamera.deviceAvailable === false,
  )
  // A stopped or unavailable camera, or one with no host/port, has nothing to
  // play. The notice explains why and links to its page instead of showing a
  // black frame.
  const url =
    selectedCamera?.enabled &&
    selectedCamera.deviceAvailable &&
    streamBase &&
    selectedCamera.streamPort
      ? `${streamBase}:${selectedCamera.streamPort}`
      : null

  // Switching a camera on starts Motion on the server. Give the MJPEG stream a
  // chance to accept connections a moment after the process is up, before the
  // player tries (and retries) to load it. A camera is brought up from its
  // Camera page's Restart camera, so this is the stream catching up.
  useEffect(() => {
    if (!selectedCamera?.enabled || !selectedCamera.deviceAvailable) {
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
  }, [selectedCamera?.cameraNumber, selectedCamera?.enabled, selectedCamera?.deviceAvailable])

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Title order={2}>{selectedName ? `Live stream — ${selectedName}` : 'Live stream'}</Title>
        <Group gap={0} justify="flex-end" align="center">
          {cameras.map((camera, position) => {
            const label = camera.name
              ? `${camera.name} (Camera${camera.cameraNumber})`
              : `(Camera${camera.cameraNumber})`
            const stopped = !camera.enabled
            const unavailable = camera.enabled && !camera.deviceAvailable
            const suffix = stopped ? ' · stopped' : unavailable ? ' · unavailable' : ''
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
                  disabled={loading || stopped || unavailable}
                  title={
                    stopped
                      ? 'This camera is stopped'
                      : unavailable
                        ? camera.statusError || 'This camera is unavailable'
                        : undefined
                  }
                >
                  {label}
                  {suffix}
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
      ) : selectedStopped ? (
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
      ) : selectedUnavailable ? (
        <Alert color="red" variant="light" title={`${selectedName} is unavailable`}>
          <Stack gap="xs" align="flex-start">
            <Text size="sm">
              {selectedCamera.statusError ||
                'Its video device was not found, so the camera cannot send a picture.'}
            </Text>
            <Text size="sm">
              Plug the camera in, then restart it from its camera page. The stream will not show a
              last frame until a real one arrives.
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
