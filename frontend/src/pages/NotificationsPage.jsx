import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Anchor,
  Badge,
  Button,
  Card,
  Center,
  Group,
  Loader,
  Stack,
  Text,
  Title,
} from '@mantine/core'
import { api } from '../api'

// DB-backed feed: rows come from babymonitor.notifications and are written by
// the device webhooks, so video events link to the relevant clip. The feed is
// per account: the backend leaves out the notifications this account has
// dismissed, so dismissing only takes the row out of this list.
export default function NotificationsPage() {
  const [notifications, setNotifications] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setNotifications(await api.listNotifications())
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const dismiss = async (id) => {
    await api.dismissNotification(id)
    setNotifications((current) => current.filter((n) => n.id !== id))
  }

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Title order={2}>Notifications</Title>
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

      {!loading && notifications.length === 0 && <Text c="dimmed">No notifications.</Text>}

      <Stack gap="sm">
        {notifications.map((notification) => (
          <Card key={notification.id} withBorder padding="md">
            <Group justify="space-between" align="flex-start" wrap="nowrap">
              <Stack gap="xs">
                <Group gap="xs">
                  <Badge
                    variant="light"
                    color={notification.eventType === 'Video' ? 'blue' : 'grape'}
                  >
                    {notification.eventType}
                  </Badge>
                  <Text size="xs" c="dimmed">
                    {new Date(notification.eventTime).toLocaleString()}
                  </Text>
                </Group>
                <Text>{notification.description}</Text>
                {notification.videoLink && (
                  <Anchor href={notification.videoLink} target="_blank" rel="noreferrer" size="sm">
                    Watch video
                  </Anchor>
                )}
              </Stack>
              <Button
                variant="subtle"
                color="red"
                size="xs"
                onClick={() => dismiss(notification.id)}
              >
                Dismiss
              </Button>
            </Group>
          </Card>
        ))}
      </Stack>
    </Stack>
  )
}
