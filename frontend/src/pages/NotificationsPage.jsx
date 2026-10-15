import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Alert,
  Anchor,
  Badge,
  Button,
  Card,
  Center,
  Group,
  Loader,
  Modal,
  Stack,
  Text,
  Title,
} from '@mantine/core'
import { api } from '../api'

// DB-backed feed: rows come from babymonitor.notifications and are written by
// the device webhooks, so video events link to the relevant clip. The feed is
// per account: the backend leaves out the notifications this account has
// dismissed, so dismissing only takes the row out of this list.
//
// A clip Motion recorded links to the app's own recordings route (see
// motion-notify.sh), which is behind the bearer gate; a plain <a href> cannot
// send the token, so those clips are fetched with it and played in an in-app
// video. Any other link (an older or external URL) still opens in a new tab.
function isAppRecording(link) {
  return (
    typeof link === 'string' &&
    link.startsWith('/api/cameras/') &&
    link.includes('/recordings/')
  )
}

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  // The in-app player. The object URL of the clip currently loaded is kept in a
  // ref as well so leaving the page revokes it.
  const [playerOpen, setPlayerOpen] = useState(false)
  const [playing, setPlaying] = useState(null)
  const [playerError, setPlayerError] = useState(null)
  const [loadingLink, setLoadingLink] = useState(null)
  const objectUrl = useRef(null)

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

  // Revoke the last object URL when the page goes away.
  useEffect(
    () => () => {
      if (objectUrl.current) {
        URL.revokeObjectURL(objectUrl.current)
        objectUrl.current = null
      }
    },
    [],
  )

  const setClip = (url) => {
    if (objectUrl.current) {
      URL.revokeObjectURL(objectUrl.current)
    }
    objectUrl.current = url
    setPlaying(url)
  }

  const play = async (path) => {
    setPlayerOpen(true)
    setPlayerError(null)
    setClip(null)
    setLoadingLink(path)
    try {
      setClip(URL.createObjectURL(await api.fetchRecording(path)))
    } catch (err) {
      setPlayerError(err.message)
    } finally {
      setLoadingLink(null)
    }
  }

  const closePlayer = () => {
    setPlayerOpen(false)
    setPlayerError(null)
    setClip(null)
  }

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
                {notification.videoLink && isAppRecording(notification.videoLink) && (
                  <Button
                    variant="subtle"
                    size="compact-sm"
                    loading={loadingLink === notification.videoLink}
                    onClick={() => play(notification.videoLink)}
                  >
                    Watch video
                  </Button>
                )}
                {notification.videoLink && !isAppRecording(notification.videoLink) && (
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

      <Modal opened={playerOpen} onClose={closePlayer} title="Recording" size="xl" centered>
        {playerError && (
          <Alert color="red" variant="light">
            {playerError}
          </Alert>
        )}
        {!playerError && !playing && (
          <Center>
            <Loader size="sm" />
          </Center>
        )}
        {playing && (
          // eslint-disable-next-line jsx-a11y/media-has-caption
          <video className="stream-media" src={playing} controls autoPlay playsInline />
        )}
      </Modal>
    </Stack>
  )
}
