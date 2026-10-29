import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Center,
  Group,
  Loader,
  Paper,
  Stack,
  Stepper,
  Text,
  TextInput,
  Title,
} from '@mantine/core'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { PROMPT_SOUNDS, useAuth } from '../auth'
import { getSoundUrl } from '../alerting'
import { useOnboarding } from '../onboarding'
import { notifyCamerasChanged } from '../useCameras'
import { notifySettingsChanged } from '../settingsEvents'

// First-run setup: name the monitor, point each camera at a device, then look at
// the picture and hear a sound. It writes the same rows the Settings and Camera
// pages write, so nothing here is a special case once setup is done.
const STEPS = ['Name', 'Cameras', 'Test']

// Accounts created by the seed migration: their password is their name.
const DEMO_ACCOUNTS = ['Cindy', 'Marij', 'Stephen', 'Bob']

export default function OnboardingPage() {
  const navigate = useNavigate()
  const { markComplete } = useOnboarding()
  const { promptSound, user } = useAuth()

  const [step, setStep] = useState(0)
  const [systemName, setSystemName] = useState('')
  const [streamBase, setStreamBase] = useState(null)
  const [cameras, setCameras] = useState([])
  const [devices, setDevices] = useState([])
  const [drafts, setDrafts] = useState({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const [previewKey, setPreviewKey] = useState(0)
  const [previewFailed, setPreviewFailed] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [rows, settings, detected] = await Promise.all([
        api.listCameras(),
        api.listSettings(),
        api.listVideoDevices().catch(() => []),
      ])
      const rolled = summarise(rows)
      setCameras(rolled)
      setDevices(detected ?? [])
      setDrafts(
        Object.fromEntries(
          rolled.map((camera) => [
            camera.cameraNumber,
            { name: camera.name, device: camera.device },
          ]),
        ),
      )
      setSystemName(settings.find((setting) => setting.tag === 'systemName')?.value ?? '')
      const base = settings.find((setting) => setting.tag === 'stream_url')?.value
      setStreamBase(base ? base.replace(/\/+$/, '') : null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const saveName = async () => {
    const name = systemName.trim()
    if (!name) {
      setError('Give the monitor a name.')
      return false
    }
    setError(null)
    setSaving(true)
    try {
      await api.updateSetting('systemName', name)
      notifySettingsChanged()
      return true
    } catch (err) {
      setError(err.message)
      return false
    } finally {
      setSaving(false)
    }
  }

  const saveCameras = async () => {
    setError(null)
    setSaving(true)
    try {
      const changed = new Set()
      for (const camera of cameras) {
        const draft = drafts[camera.cameraNumber]
        if (!draft) {
          continue
        }
        if (draft.name !== camera.name) {
          await api.updateCameraParameter(camera.cameraNumber, 'camera_name', draft.name)
          changed.add(camera.cameraNumber)
        }
        if (draft.device !== camera.device) {
          const device = draft.device.trim()
          if (!device) {
            setError(`Camera ${camera.cameraNumber} needs a device path.`)
            return false
          }
          await api.updateCameraParameter(camera.cameraNumber, 'video_device', device)
          // Rebuild the config and restart it, so the new device takes effect.
          await api.applyCameraMotion(camera.cameraNumber).catch(() => {})
          changed.add(camera.cameraNumber)
        }
      }
      if (changed.size > 0) {
        notifyCamerasChanged()
      }
      return true
    } catch (err) {
      setError(err.message)
      return false
    } finally {
      setSaving(false)
    }
  }

  const next = async () => {
    if (step === 0 && !(await saveName())) {
      return
    }
    if (step === 1 && !(await saveCameras())) {
      return
    }
    setStep((current) => current + 1)
  }

  const finish = async () => {
    setError(null)
    setSaving(true)
    try {
      await markComplete()
      navigate('/', { replace: true })
    } catch (err) {
      setError(err.message)
      setSaving(false)
    }
  }

  const playTestSound = () => {
    const prefix =
      PROMPT_SOUNDS.find((sound) => sound.id === promptSound)?.prefix ?? 'notification-chime'
    const audio = new Audio(getSoundUrl(prefix))
    audio.play().catch(() => {
      // A browser that has not seen a click may block it; the toast still works.
    })
  }

  const updateDraft = (cameraNumber, field, value) => {
    setDrafts((current) => ({
      ...current,
      [cameraNumber]: { ...current[cameraNumber], [field]: value },
    }))
  }

  // Preview the first camera that has a port, so a camera without one does not
  // hide a perfectly good second camera.
  const previewCamera = cameras.find((camera) => camera.streamPort) ?? cameras[0]
  const previewUrl =
    streamBase && previewCamera?.streamPort
      ? `${streamBase}:${previewCamera.streamPort}?setup=${previewKey}`
      : null

  return (
    <Stack>
      <Stack gap={2}>
        <Title order={2}>Set up your monitor</Title>
        <Text c="dimmed" size="sm">
          Three steps: name it, point the cameras at their devices, then check the picture and
          sound. Everything can be changed later.
        </Text>
      </Stack>

      <Stepper active={step} size="sm" allowNextStepsSelect={false}>
        {STEPS.map((label) => (
          <Stepper.Step key={label} label={label} />
        ))}
      </Stepper>

      {error && (
        <Alert color="red" variant="light">
          {error}
        </Alert>
      )}

      {loading ? (
        <Center>
          <Loader size="sm" />
        </Center>
      ) : (
        <Paper withBorder p="lg">
          {step === 0 && (
            <Stack>
              <Text fw={600}>What should this monitor be called?</Text>
              <TextInput
                label="Monitor name"
                description="Shown in the title bar and the browser tab."
                value={systemName}
                onChange={(event) => setSystemName(event.currentTarget.value)}
                maxLength={120}
              />
              {DEMO_ACCOUNTS.includes(user) && (
                <Alert color="yellow" variant="light" title="Demo account">
                  You are signed in as {user}, whose password is the same as its name. Create a real
                  account and delete the demo ones before relying on this monitor — the commands are
                  in DEVOPS.md.
                </Alert>
              )}
            </Stack>
          )}

          {step === 1 && (
            <Stack>
              <Text fw={600}>Which device is each camera?</Text>
              <Text size="sm" c="dimmed">
                {devices.length > 0
                  ? `Found ${devices.length} video device${devices.length === 1 ? '' : 's'}: ${devices
                      .map((device) => `${device.device}${device.name ? ` (${device.name})` : ''}`)
                      .join(', ')}`
                  : 'No /dev/video* devices were found. Plug the camera in, then reload this page — or type the path.'}
              </Text>
              {cameras.length === 0 && (
                <Text c="dimmed" size="sm">
                  No cameras are configured yet. Add a camera row, then reload this page.
                </Text>
              )}
              {cameras.map((camera) => (
                <Paper key={camera.cameraNumber} withBorder p="md">
                  <Stack gap="sm">
                    <Text fw={600}>Camera {camera.cameraNumber}</Text>
                    <TextInput
                      label={`Camera ${camera.cameraNumber} name`}
                      value={drafts[camera.cameraNumber]?.name ?? ''}
                      onChange={(event) =>
                        updateDraft(camera.cameraNumber, 'name', event.currentTarget.value)
                      }
                      maxLength={120}
                    />
                    <Autocomplete
                      label={`Camera ${camera.cameraNumber} video device`}
                      description={`Stream port ${camera.streamPort ?? 'not set'}`}
                      placeholder="/dev/video0"
                      data={devices.map((device) => device.device)}
                      value={drafts[camera.cameraNumber]?.device ?? ''}
                      onChange={(value) => updateDraft(camera.cameraNumber, 'device', value)}
                    />
                  </Stack>
                </Paper>
              ))}
            </Stack>
          )}

          {step === 2 && (
            <Stack>
              <Text fw={600}>Check the picture and the sound</Text>
              <Text size="sm" c="dimmed">
                The preview is the same stream the Live stream page plays. If it stays black, check
                the device path and that the camera is plugged in.
              </Text>
              <Box className="stream-frame" style={{ minHeight: 240 }}>
                {previewUrl ? (
                  <img
                    className="stream-media"
                    src={previewUrl}
                    alt="Setup camera preview"
                    onLoad={() => setPreviewFailed(false)}
                    onError={() => setPreviewFailed(true)}
                  />
                ) : (
                  <Center>
                    <Text c="gray.3">No stream URL or port is configured yet.</Text>
                  </Center>
                )}
              </Box>
              {previewFailed && previewUrl && (
                <Alert color="yellow" variant="light">
                  No picture yet. Check the device path in the previous step, then try again. If you
                  are watching from another device, check the stream address under Settings.
                </Alert>
              )}
              <Stack gap="xs">
                <Text fw={600}>Alert sound</Text>
                <Text size="sm" c="dimmed">
                  Plays the tone an alert will use. You can change it in Personal settings later.
                </Text>
                <Group gap="sm">
                  <Button variant="default" onClick={playTestSound}>
                    Play test sound
                  </Button>
                  <Button
                    variant="subtle"
                    onClick={() => {
                      setPreviewFailed(false)
                      setPreviewKey((current) => current + 1)
                    }}
                  >
                    Try the picture again
                  </Button>
                </Group>
              </Stack>
            </Stack>
          )}
        </Paper>
      )}

      <Group justify="space-between" align="center">
        <Button variant="subtle" color="gray" onClick={finish} loading={saving}>
          Skip for now
        </Button>
        <Group gap="sm">
          <Button
            variant="default"
            disabled={step === 0 || saving}
            onClick={() => setStep((current) => current - 1)}
          >
            Back
          </Button>
          {step < STEPS.length - 1 ? (
            <Button onClick={next} loading={saving} disabled={loading}>
              Continue
            </Button>
          ) : (
            <Button onClick={finish} loading={saving} disabled={loading}>
              Finish setup
            </Button>
          )}
        </Group>
      </Group>
    </Stack>
  )
}

/** Collapse the parameter rows into one entry per camera. */
function summarise(rows) {
  const map = new Map()
  for (const row of rows ?? []) {
    const entry = map.get(row.cameraNumber) ?? {
      cameraNumber: row.cameraNumber,
      name: '',
      device: '',
      streamPort: null,
    }
    if (row.key === 'camera_name') {
      entry.name = row.value
    }
    if (row.key === 'video_device') {
      entry.device = row.value
    }
    if (row.key === 'stream_port') {
      entry.streamPort = row.value
    }
    map.set(row.cameraNumber, entry)
  }
  return [...map.values()].sort((a, b) => a.cameraNumber - b.cameraNumber)
}
