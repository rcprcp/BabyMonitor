import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Avatar,
  Badge,
  Button,
  Card,
  Center,
  Divider,
  Group,
  Loader,
  Popover,
  ScrollArea,
  SegmentedControl,
  Select,
  Stack,
  Switch,
  Text,
  Title,
} from '@mantine/core'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import {
  alertsEnabled,
  setAlertsEnabled,
  unlockAudio,
} from '../alerting'
import { useAuth, PROMPT_SOUNDS } from '../auth'
import { COLOR_SCHEMES } from '../theme'
import ThemePalette from '../components/ThemePalette'

/** Plays the selected sound once so the user can audition it. */
function previewSound(name) {
  const audio = new Audio(`/sounds/${name}.mp3`)
  audio.play().catch(() => {})
}

// The signed-in user's own record, pulled from babymonitor.users and shown
// in the main content area. Reached by clicking the initial in the title bar.
function initialOf(name) {
  const trimmed = (name ?? '').trim()
  return trimmed ? trimmed.charAt(0).toUpperCase() : '?'
}

/**
 * Both theme preferences are stored on the account, so they follow the user to
 * any browser: the accent color (Mantine's palette) and light / dark / auto.
 */
export default function ProfilePage() {
  const { user, colorScheme, themeColor, promptSound, setPromptSound, saveColorScheme, saveThemeColor, savePromptSound, logout } = useAuth()
  const navigate = useNavigate()
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  const [savingAlertSound, setSavingAlertSound] = useState(false)
  const [savingColor, setSavingColor] = useState(false)
  const [colorSaved, setColorSaved] = useState(false)
  const [colorError, setColorError] = useState(null)

  const [savingScheme, setSavingScheme] = useState(false)
  const [schemeSaved, setSchemeSaved] = useState(false)
  const [schemeError, setSchemeError] = useState(null)

  const [alertsOn, setAlertsOn] = useState(() => alertsEnabled())

  // When the user picks a sound on this page we write it through both local and
  // server state; the component's own save button reflects the result.
  const [alertSoundSaved, setAlertSoundSaved] = useState(false)
  const [savingAlertSoundUI, setSavingAlertSoundUI] = useState(false)

  /** Read (or re-read) the profile so the fresh promptSound is reflected here. */
  const refreshAlertSound = useCallback(async () => {
    try {
      const current = await api.getProfile()
      if (current.promptSound) {
        setPromptSound(current.promptSound)
      }
    } catch {
      // If the call fails we keep whatever's already there.
    } finally {
      setSavingAlertSoundUI(false)
    }
  }, [setPromptSound])

  const changeAlertSound = async (next) => {
    setAlertSoundSaved(false)
    setSavingAlertSoundUI(true)
    savePromptSound(next).then(() => {
      setAlertSoundSaved(true)
      setTimeout(() => setAlertSoundSaved(false), 3000)
    })
  }

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setProfile(await api.getProfile())
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const changeThemeColor = async (next) => {
    setColorError(null)
    setColorSaved(false)
    setSavingColor(true)
    try {
      await saveThemeColor(next)
      setColorSaved(true)
    } catch (err) {
      setColorError(err.message)
    } finally {
      setSavingColor(false)
    }
  }

  const changeColorScheme = async (next) => {
    setSchemeError(null)
    setSchemeSaved(false)
    setSavingScheme(true)
    try {
      await saveColorScheme(next)
      setSchemeSaved(true)
    } catch (err) {
      setSchemeError(err.message)
    } finally {
      setSavingScheme(false)
    }
  }

  // Sign out, then bounce to the login screen (same as the title bar menu).
  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  // Turning alerts on primes the audio context so later alerts can sound.
  const toggleAlerts = () => {
    if (alertsOn) {
      setAlertsEnabled(false)
      setAlertsOn(false)
      return
    }
    unlockAudio()
    setAlertsEnabled(true)
    setAlertsOn(true)
  }

  // Prefer the freshly loaded record, but fall back to the stored name while
  // the request is in flight.
  const displayName = profile?.name ?? user ?? ''

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Title order={2}>Personal settings</Title>
        <Button variant="default" color="red" onClick={handleLogout}>
          Logout
        </Button>
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

      {!loading && profile && (
        <Card withBorder padding="lg">
          <Group wrap="nowrap" align="flex-start">
            <Avatar
              size={64}
              radius="xl"
              bg="var(--mantine-primary-color-filled)"
              c="var(--mantine-primary-color-contrast)"
              fw={600}
            >
              {initialOf(displayName)}
            </Avatar>
            <Stack gap={4}>
              <Title order={3}>{profile.name}</Title>
              <Text c="dimmed" size="sm">
                Your account record, read from the database.
              </Text>
              <Group gap="xl" mt="xs">
                <Stack gap={0}>
                  <Text size="xs" c="dimmed">
                    Name
                  </Text>
                  <Text>{profile.name}</Text>
                </Stack>
                <Stack gap={0}>
                  <Text size="xs" c="dimmed">
                    User ID
                  </Text>
                  <Text>{profile.id}</Text>
                </Stack>
              </Group>
            </Stack>
          </Group>

          <Divider my="lg" />

          <Stack gap="lg">
            <Stack gap="xs" align="flex-start">
              <Text fw={600}>Theme Selection</Text>
              <Text c="dimmed" size="sm">
                Mantine&apos;s color palette. The color you pick becomes the accent used for
                buttons, links and highlights.
              </Text>
              <ThemePalette value={themeColor} onChange={changeThemeColor} disabled={savingColor} />
              {colorSaved && (
                <Badge color="green" variant="light">
                  Saved
                </Badge>
              )}
              {colorError && (
                <Alert color="red" variant="light">
                  {colorError}
                </Alert>
              )}
            </Stack>

            <Stack gap="xs" align="flex-start">
              <Text fw={600}>Light / dark</Text>
              <Text c="dimmed" size="sm">
                Stored with your account, so it follows you to any browser you sign in from.
              </Text>
              <SegmentedControl
                value={colorScheme}
                onChange={changeColorScheme}
                data={COLOR_SCHEMES}
                disabled={savingScheme}
              />
              {schemeSaved && (
                <Badge color="green" variant="light">
                  Saved
                </Badge>
              )}
              {schemeError && (
                <Alert color="red" variant="light">
                  {schemeError}
                </Alert>
              )}
            </Stack>

            <Stack gap="xs" align="flex-start">
              <Text fw={600}>Alerts</Text>
              <Text c="dimmed" size="sm">
                Show an in-app notification when the cameras report a new event, and sound a
                chime for audio events such as a cry. Alerts are per device, so turn them on
                on every screen you want alerted.
              </Text>
              <Switch
                checked={alertsOn}
                onChange={toggleAlerts}
                label="Alert me to new notifications"
              />
            </Stack>

            <Stack gap="xs" align="flex-start">
              <Group gap={0}>
                <Text fw={600}>Notification sound</Text>
                <Button
                  variant="subtle"
                  size="xs"
                  disabled={savingAlertSoundUI}
                  onClick={() => {
                    setSavingAlertSoundUI(true)
                    refreshAlertSound()
                  }}
                >
                  Refresh
                </Button>
              </Group>
              <Text c="dimmed" size="sm">
                Select the tone played when an audio alert (crying, noise) arrives. Saved on your account so it follows you across browsers.
              </Text>
              <Popover position="bottom" withArrow width={360}>
                <Popover.Target>
                  <Button variant="outline" fullWidth radius="md">
                    {PROMPT_SOUNDS.find((s) => s.id === promptSound)?.label ?? 'Select a sound'}
                  </Button>
                </Popover.Target>
                <Popover.Dropdown mah={300} style={{ overflow: 'hidden' }}>
                  <ScrollArea.Autosize mah={300}>
                    {PROMPT_SOUNDS.map((sound) => (
                      <Group
                        key={sound.id}
                        gap="sm"
                        justify="space-between"
                        wrap="nowrap"
                        px="xs"
                        py="xs"
                        radius="md"
                        bg={sound.id === promptSound ? 'var(--mantine-primary-color-light)' : undefined}
                        onClick={() => { previewSound(sound.prefix); changeAlertSound(sound.id) }}
                        style={{ cursor: savingAlertSoundUI ? 'wait' : 'pointer', userSelect: 'none' }}
                      >
                        <Group gap="xs" wrap="nowrap">
                          <Button
                            variant="light"
                            size="xs"
                            px={8}
                            fw={600}
                            c="blue"
                            radius="md"
                            onClick={(e) => { e.stopPropagation(); previewSound(sound.prefix) }}
                          >
                            Play ▶
                          </Button>{" "}
                          <Text>{sound.label}</Text>
                        </Group>
                      </Group>
                    ))}
                  </ScrollArea.Autosize>
                </Popover.Dropdown>
              </Popover>
              {alertSoundSaved && (
                <Badge color="green" variant="light">
                  Saved
                </Badge>
              )}
              <Group gap={0}>
                <Text c="dimmed" size="sm" fw={500}>Current:</Text>{' '}
                <Text c="blue" fw={500} span>
                  {PROMPT_SOUNDS.find((s) => s.id === promptSound)?.label ?? '—'}
                </Text>
              </Group>
            </Stack>
          </Stack>
        </Card>
      )}
    </Stack>
  )
}
