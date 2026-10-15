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
  Text,
  Title,
} from '@mantine/core'
import { api } from '../api'
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
 * any browser: the accent colour (Mantine's palette) and light / dark / auto.
 */
export default function ProfilePage() {
  const { user, colorScheme, themeColor, saveColorScheme, saveThemeColor } = useAuth()
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  const [savingColor, setSavingColor] = useState(false)
  const [colorSaved, setColorSaved] = useState(false)
  const [colorError, setColorError] = useState(null)

  const [savingScheme, setSavingScheme] = useState(false)
  const [schemeSaved, setSchemeSaved] = useState(false)
  const [schemeError, setSchemeError] = useState(null)

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

  // Prefer the freshly loaded record, but fall back to the stored name while
  // the request is in flight.
  const displayName = profile?.name ?? user ?? ''

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Title order={2}>Personal settings</Title>
        <Button variant="default" onClick={load} loading={loading}>
          Refresh
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
                Mantine&apos;s colour palette. The colour you pick becomes the accent used for
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
          </Stack>
        </Card>
      )}
    </Stack>
  )
}
