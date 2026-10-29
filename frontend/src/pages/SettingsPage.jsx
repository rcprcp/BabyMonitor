import { useState } from 'react'
import {
  Alert,
  Anchor,
  Avatar,
  Button,
  Card,
  Divider,
  Group,
  Stack,
  Text,
  Title,
} from '@mantine/core'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { useOnboarding } from '../onboarding'
import AppearanceSettings from '../components/AppearanceSettings'
import NotificationSound from '../components/NotificationSound'

function initialOf(name) {
  const trimmed = (name ?? '').trim()
  return trimmed ? trimmed.charAt(0).toUpperCase() : '?'
}

// The account-facing settings: alert behaviour, appearance and the account
// itself. System-wide settings (name, stream address, raw tags) live on
// /system-settings, linked from the header and the sidebar.
export default function SettingsPage() {
  const navigate = useNavigate()
  const { restart } = useOnboarding()
  const { user, logout } = useAuth()

  const [setupError, setSetupError] = useState(null)
  const [signingOut, setSigningOut] = useState(false)

  const runSetup = async () => {
    setSetupError(null)
    try {
      await restart()
      navigate('/welcome')
    } catch (err) {
      setSetupError(err.message)
    }
  }

  const signOut = async () => {
    setSigningOut(true)
    try {
      await logout()
      navigate('/login', { replace: true })
    } finally {
      setSigningOut(false)
    }
  }

  return (
    <Stack>
      <Group justify="space-between" align="center" wrap="wrap">
        <Stack gap={2}>
          <Title order={2}>Settings</Title>
          <Text c="dimmed" size="sm">
            Alerts, appearance and your account.
          </Text>
        </Stack>
        <Button component={RouterLink} to="/system-settings" variant="default">
          System Settings
        </Button>
      </Group>

      <Card withBorder padding="lg">
        <Stack gap="md">
          <Stack gap={2}>
            <Text fw={600}>Alerts</Text>
            <Text size="sm" c="dimmed">
              What gets your attention, and what it sounds like.
            </Text>
          </Stack>
          <NotificationSound />
          <Divider />
          <Group justify="space-between" align="center" wrap="nowrap">
            <Stack gap={2}>
              <Text fw={500}>Audio Configuration</Text>
              <Text size="sm" c="dimmed">
                Which sounds may raise an alert.
              </Text>
            </Stack>
            <Anchor component={RouterLink} to="/audio-alerts">
              Open
            </Anchor>
          </Group>
          <Group justify="space-between" align="center" wrap="nowrap">
            <Stack gap={2}>
              <Text fw={500}>Environment</Text>
              <Text size="sm" c="dimmed">
                Temperature, humidity and brightness readings.
              </Text>
            </Stack>
            <Anchor component={RouterLink} to="/environment">
              Open
            </Anchor>
          </Group>
          <Group justify="space-between" align="center" wrap="nowrap">
            <Stack gap={2}>
              <Text fw={500}>Environment Configuration</Text>
              <Text size="sm" c="dimmed">
                Alert bounds and the camera-light calibration.
              </Text>
            </Stack>
            <Anchor component={RouterLink} to="/environment-config">
              Open
            </Anchor>
          </Group>
        </Stack>
      </Card>

      <Card withBorder padding="lg">
        <Stack gap="md">
          <Stack gap={2}>
            <Text fw={600}>Appearance</Text>
            <Text size="sm" c="dimmed">
              Stored with your account, so it follows you to any browser.
            </Text>
          </Stack>
          <AppearanceSettings />
        </Stack>
      </Card>

      <Card withBorder padding="lg">
        <Group justify="space-between" align="center" wrap="nowrap">
          <Group wrap="nowrap">
            <Avatar size={48} radius="xl" variant="light" fw={600}>
              {initialOf(user)}
            </Avatar>
            <Stack gap={0}>
              <Text fw={600}>{user || 'Signed in'}</Text>
              <Text size="sm" c="dimmed">
                Your account
              </Text>
            </Stack>
          </Group>
          <Button variant="default" color="red" onClick={signOut} loading={signingOut}>
            Sign out
          </Button>
        </Group>
      </Card>

      <Card withBorder padding="lg">
        <Group justify="space-between" align="center" wrap="nowrap">
          <Stack gap={2}>
            <Text fw={600}>Setup wizard</Text>
            <Text size="sm" c="dimmed">
              Walk through the monitor name, camera devices and a stream test again.
            </Text>
          </Stack>
          <Button variant="default" onClick={runSetup}>
            Run setup again
          </Button>
        </Group>
        {setupError && (
          <Alert color="red" variant="light" mt="sm">
            {setupError}
          </Alert>
        )}
      </Card>
    </Stack>
  )
}
