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
import { NOTIFICATIONS_ARRIVED_EVENT, hideAlert, hideAllAlerts } from '../alerting'

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
    typeof link === 'string' && link.startsWith('/api/cameras/') && link.includes('/recordings/')
  )
}

function badgeColor(eventType) {
  if (eventType === 'Video') {
    return 'blue'
  }
  if (eventType === 'Audio') {
    return 'grape'
  }
  if (eventType === 'Environment') {
    return 'teal'
  }
  return 'orange'
}

// Merge a fetched or pushed batch into the current feed: de-duplicate by id,
// drop anything dismissed in this session, and keep newest event first (id
// breaks ties). A new live row therefore appears at the top immediately.
function mergeNotifications(current, incoming, dismissed) {
  const byId = new Map()
  for (const notification of [...incoming, ...current]) {
    if (!notification || dismissed.has(notification.id)) {
      continue
    }
    if (!byId.has(notification.id)) {
      byId.set(notification.id, notification)
    }
  }
  return [...byId.values()].sort(
    (a, b) => new Date(b.eventTime).getTime() - new Date(a.eventTime).getTime() || b.id - a.id,
  )
}

// How long a newly arrived card keeps its "New" badge and accent.
const NEW_HIGHLIGHT_MS = 10000

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [dismissingAll, setDismissingAll] = useState(false)

  // Ids this tab has dismissed, so a late event/poll cannot bring one back.
  const dismissedIds = useRef(new Set())
  // Coalesces the event refresh and the fallback poll into one request.
  const refreshing = useRef(false)
  // Rows that arrived while the page was open, highlighted for a few seconds.
  const [newIds, setNewIds] = useState(() => new Set())
  const notificationsRef = useRef([])
  const hasLoaded = useRef(false)
  const highlightTimers = useRef(new Map())

  // The in-app player. The object URL of the clip currently loaded is kept in a
  // ref as well so leaving the page revokes it.
  const [playerOpen, setPlayerOpen] = useState(false)
  const [playing, setPlaying] = useState(null)
  const [playerError, setPlayerError] = useState(null)
  const [loadingLink, setLoadingLink] = useState(null)
  const objectUrl = useRef(null)

  // Keep a ref of the rendered list so a fetch can tell which rows are new
  // without treating the whole initial feed as new.
  useEffect(() => {
    notificationsRef.current = notifications
  }, [notifications])

  const highlight = useCallback((ids) => {
    if (ids.length === 0) {
      return
    }
    setNewIds((current) => {
      const next = new Set(current)
      ids.forEach((id) => next.add(id))
      return next
    })
    ids.forEach((id) => {
      const existing = highlightTimers.current.get(id)
      if (existing) {
        clearTimeout(existing)
      }
      const timer = setTimeout(() => {
        highlightTimers.current.delete(id)
        setNewIds((current) => {
          if (!current.has(id)) {
            return current
          }
          const next = new Set(current)
          next.delete(id)
          return next
        })
      }, NEW_HIGHLIGHT_MS)
      highlightTimers.current.set(id, timer)
    })
  }, [])

  const applyRows = useCallback(
    (rows, { highlightNew = true, replace = false } = {}) => {
      const incoming = rows ?? []
      const known = new Set(notificationsRef.current.map((notification) => notification.id))
      const added = incoming
        .filter((notification) => !known.has(notification.id))
        .map((notification) => notification.id)
      if (replace) {
        setNotifications(
          incoming.filter((notification) => !dismissedIds.current.has(notification.id)),
        )
      } else {
        setNotifications((current) => mergeNotifications(current, incoming, dismissedIds.current))
      }
      if (highlightNew && hasLoaded.current && added.length > 0) {
        highlight(added)
      }
    },
    [highlight],
  )

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const rows = await api.listNotifications()
      applyRows(rows, { highlightNew: false, replace: true })
      hasLoaded.current = true
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [applyRows])

  // A new notification arrived (event or poll): merge it in place, without the
  // loading spinner flashing over the page. Rows that were not already in the
  // list get the "New" highlight for a few seconds.
  const refresh = useCallback(async () => {
    if (refreshing.current) {
      return
    }
    refreshing.current = true
    try {
      const rows = await api.listNotifications()
      applyRows(rows)
      hasLoaded.current = true
    } catch {
      // Leave the list as-is; the next event, poll or Refresh retries.
    } finally {
      refreshing.current = false
    }
  }, [applyRows])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    const onArrived = (event) => {
      const incoming = event?.detail?.notifications
      if (!Array.isArray(incoming) || incoming.length === 0) {
        // A bare signal (or an older emitter): fall back to a full refresh.
        refresh()
        return
      }
      applyRows(incoming)
    }
    window.addEventListener(NOTIFICATIONS_ARRIVED_EVENT, onArrived)
    return () => window.removeEventListener(NOTIFICATIONS_ARRIVED_EVENT, onArrived)
  }, [applyRows, refresh])

  // The global alert hook also polls, but a dropped event must not leave this
  // page stale; a light poll while it is visible is the safety net.
  useEffect(() => {
    const id = setInterval(() => {
      if (!document.hidden) {
        refresh()
      }
    }, 10000)
    return () => clearInterval(id)
  }, [refresh])

  // Clear the "New" highlight timers if the page goes away.
  useEffect(
    () => () => {
      highlightTimers.current.forEach((timer) => clearTimeout(timer))
      highlightTimers.current.clear()
    },
    [],
  )

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
    try {
      await api.dismissNotification(id)
      dismissedIds.current.add(id)
      hideAlert(id)
      setNotifications((current) => current.filter((n) => n.id !== id))
    } catch (err) {
      setError(err.message)
    }
  }

  const dismissAll = async () => {
    if (notifications.length === 0) {
      return
    }
    if (!window.confirm('Clear all of your notifications? This cannot be undone.')) {
      return
    }
    setDismissingAll(true)
    setError(null)
    try {
      await api.dismissAllNotifications()
      notifications.forEach((notification) => dismissedIds.current.add(notification.id))
      hideAllAlerts()
      highlightTimers.current.forEach((timer) => clearTimeout(timer))
      highlightTimers.current.clear()
      setNewIds(new Set())
      setNotifications([])
    } catch (err) {
      setError(err.message)
    } finally {
      setDismissingAll(false)
    }
  }

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Title order={2}>Alerts</Title>
        <Group gap="sm">
          <Button
            variant="light"
            color="red"
            onClick={dismissAll}
            loading={dismissingAll}
            disabled={loading || dismissingAll || notifications.length === 0}
          >
            Clear my notifications
          </Button>
          <Button variant="default" onClick={load} loading={loading}>
            Refresh
          </Button>
        </Group>
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
          <Card
            key={notification.id}
            withBorder
            padding="md"
            style={
              newIds.has(notification.id)
                ? { borderLeft: '4px solid var(--mantine-color-blue-5)' }
                : undefined
            }
          >
            <Group justify="space-between" align="flex-start" wrap="nowrap">
              <Stack gap="xs">
                <Group gap="xs">
                  <Badge variant="light" color={badgeColor(notification.eventType)}>
                    {notification.eventType}
                  </Badge>
                  {newIds.has(notification.id) && (
                    <Badge color="blue" variant="filled" size="xs">
                      New
                    </Badge>
                  )}
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
        {playing && <video className="stream-media" src={playing} controls autoPlay playsInline />}
      </Modal>
    </Stack>
  )
}
