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
import { THEMES } from '../theme'

// The signed-in user's own record, pulled from babymonitor.users and shown
// in the main content area. Reached by clicking the initial in the title bar.
function initialOf(name) {
  const trimmed = (name ?? '').trim()
  return trimmed ? trimmed.charAt(0).toUpperCase() : '?'
}

export default function ProfilePage() {
  const { user, theme, saveTheme } = useAuth()
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [savingTheme, setSavingTheme] = useState(false)
  const [themeError, setThemeError] = useState(null)
  const [themeSaved, setThemeSaved] = useState(false)

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

  // The preference lives on the account, so this writes through to
  // babymonitor.users rather than to the browser.
  const changeTheme = async (next) => {
    setThemeError(null)
    setThemeSaved(false)
    setSavingTheme(true)
    try {
      await saveTheme(next)
      setThemeSaved(true)
    } catch (err) {
      setThemeError(err.message)
    } finally {
      setSavingTheme(false)
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
            <Avatar size={64} radius="xl" bg="blue.6" c="white" fw={600}>
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

          <Stack gap="xs" align="flex-start">
            <Text fw={600}>Theme</Text>
            <Text c="dimmed" size="sm">
              Stored with your account, so it follows you to any browser you sign in from.
            </Text>
            <SegmentedControl
              value={theme}
              onChange={changeTheme}
              data={THEMES}
              disabled={savingTheme}
            />
            {themeSaved && (
              <Badge color="green" variant="light">
                Saved
              </Badge>
            )}
            {themeError && (
              <Alert color="red" variant="light">
                {themeError}
              </Alert>
            )}
          </Stack>
        </Card>
      )}
    </Stack>
  )
}
