import { useState } from 'react'
import { Anchor, Avatar, Button, Card, Divider, Group, Stack, Text, Title } from '@mantine/core'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import AppearanceSettings from '../components/AppearanceSettings'
import NotificationSound from '../components/NotificationSound'

function LogoutIcon({ size = 16 }) {
  return (
    <svg
      aria-hidden="true"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <path d="m16 17 5-5-5-5" />
      <path d="M21 12H9" />
    </svg>
  )
}

function initialOf(name) {
  const trimmed = (name ?? '').trim()
  return trimmed ? trimmed.charAt(0).toUpperCase() : '?'
}

// The account-facing settings: alert behaviour, appearance and the account
// itself. System-wide settings (name, stream address, raw tags and the setup
// wizard) live on /system-settings, linked from the account card and sidebar.
export default function SettingsPage() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  const [signingOut, setSigningOut] = useState(false)

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
      <Stack gap={2}>
        <Title order={2}>Settings</Title>
        <Text c="dimmed" size="sm">
          Alerts, appearance and your account.
        </Text>
      </Stack>

      <Card withBorder padding="lg">
        <Stack gap="md">
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
            <Button
              variant="default"
              color="red"
              leftSection={<LogoutIcon />}
              onClick={signOut}
              loading={signingOut}
            >
              Logout
            </Button>
          </Group>
          <Divider />
          <Group justify="space-between" align="center" wrap="nowrap">
            <Stack gap={2}>
              <Text fw={500}>System Settings</Text>
              <Text size="sm" c="dimmed">
                Monitor name and stream address.
              </Text>
            </Stack>
            <Anchor component={RouterLink} to="/system-settings">
              Open
            </Anchor>
          </Group>
        </Stack>
      </Card>

      <Card withBorder padding="lg">
        <Stack gap="md">
          <Stack gap={2}>
            <Text fw={600}>Alerts</Text>
            <Text size="sm" c="dimmed">
              What gets your attention, and what it sounds like.
            </Text>
          </Stack>
          <NotificationSound />
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
    </Stack>
  )
}
