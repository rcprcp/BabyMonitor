import { Button, Group, Paper, Stack, Text, Title, UnstyledButton } from '@mantine/core'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'

// Everything outside the four destinations. Keeping it a list rather than a
// drawer means each entry is a plain link that works on a reload, and the page
// can describe what is behind it.
const LINKS = [
  {
    to: '/environment',
    label: 'Environment',
    summary: 'Temperature, humidity, brightness and the camera light.',
  },
  {
    to: '/audio-alerts',
    label: 'Audio notifications',
    summary: 'Which sounds may raise an alert.',
  },
  {
    to: '/settings',
    label: 'Settings',
    summary: 'System, alerts, appearance and your account.',
  },
]

export default function MorePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const signOut = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <Stack>
      <Title order={2}>More</Title>
      <Text c="dimmed" size="sm">
        Everything outside watching, alerts and the cameras.
      </Text>

      <Stack gap="sm">
        {LINKS.map((link) => (
          <UnstyledButton key={link.to} component={RouterLink} to={link.to}>
            <Paper withBorder p="md">
              <Group justify="space-between" align="center" wrap="nowrap">
                <Stack gap={2}>
                  <Text fw={600}>{link.label}</Text>
                  <Text size="sm" c="dimmed">
                    {link.summary}
                  </Text>
                </Stack>
                <Text size="sm" c="dimmed">
                  Open →
                </Text>
              </Group>
            </Paper>
          </UnstyledButton>
        ))}
      </Stack>

      <Group>
        <Button variant="default" color="red" onClick={signOut}>
          Sign out{user ? ` (${user})` : ''}
        </Button>
      </Group>
    </Stack>
  )
}
