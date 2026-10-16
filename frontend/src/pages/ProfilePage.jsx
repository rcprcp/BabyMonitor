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
  SegmentedControl,
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
import { useAuth } from '../auth'
import { COLOR_SCHEMES } from '../theme'
import ThemePalette from '../components/ThemePalette'

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
  const { user, colorScheme, themeColor, saveColorScheme, saveThemeColor, logout } = useAuth()
  const navigate = useNavigate()
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  const [savingColor, setSavingColor] = useState(false)
  const [colorSaved, setColorSaved] = useState(false)
  const [colorError, setColorError] = useState(null)

  const [savingScheme, setSavingScheme] = useState(false)
  const [schemeSaved, setSchemeSaved] = useState(false)
  const [schemeError, setSchemeError] = useState(null)

  const [alertsOn, setAlertsOn] = useState(() => alertsEnabled())

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
          </Stack>
        </Card>
      )}
    </Stack>
  )
}
