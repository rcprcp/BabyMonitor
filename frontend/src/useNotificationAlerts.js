// Keeps the parent alerted to new notifications, from anywhere in the app.
//
// Two delivery paths run at once: a server-sent event stream gives near-instant
// push, and polling is the safety net that always works (a backgrounded tab, a
// dropped connection, or a browser without EventSource). Whichever notices a new
// notification first alerts; the second sees it is already past the watermark.
// Alerting is unconditional: there is no per-device opt-out, and every event
// except a System notice is audible.

import { useEffect, useRef } from 'react'
import { api, getToken } from './api'
import { useAuth } from './auth'
import {
  announceNotifications,
  playAlertSound,
  selectAlerts,
  showAlert,
  withAlertLock,
} from './alerting'

// With the stream healthy (or the tab hidden) we can poll lazily; otherwise a
// few seconds keeps alerts prompt without hammering the LAN.
const POLL_MS = 5000
const POLL_HIDDEN_MS = 20000
const POLL_WITH_STREAM_MS = 60000

/** Map a stored sound key to the actual file prefix used in /sounds/. */
function soundPrefix(id) {
  switch (id) {
    case 'alarm-loud':
      return 'alarm-loud'
    case 'doorbell':
      return 'doorbell-ding-dong'
    case 'beep':
      return 'beep-short'
    case 'chime':
      return 'notification-chime'
    default:
      return id ?? 'chime'
  }
}

export default function useNotificationAlerts() {
  const { isAuthenticated, user, promptSound } = useAuth()
  const timer = useRef(null)
  const source = useRef(null)
  const reconnectTimer = useRef(null)
  const reconnectDelay = useRef(1000)
  const inFlight = useRef(false)
  const streamHealthy = useRef(false)
  // Highest notification id this session has already alerted for. In-memory
  // only: it de-duplicates SSE against polling while the app runs, and resets
  // on reload so a recent event is alerted again rather than silently skipped.
  const watermark = useRef(0)
  // Captures the latest prefix so alertFor always uses it without becoming a hook.
  const prefixRef = useRef('')

  // Keep prefixRef in sync.
  useEffect(() => {
    prefixRef.current = soundPrefix(promptSound)
  }, [promptSound])

  // Alert a single notification — captures prompt via ref so it is cheap and
  // does not need to be re-created each effect run.
  const alertFor = (notification) => {
    withAlertLock(notification.id, () => {
      showAlert(notification)
      // Every real alert is audible, whatever kind it is: the production
      // pipeline only ever produces Video (motion) events, so scoping the
      // chime to Audio left every genuine alert silent. System alerts stay
      // quiet: the supervisor already logged the restart, so a chime would
      // only startle a sleeping parent for an automatic recovery.
      if (notification.eventType !== 'System') {
        playAlertSound(prefixRef.current)
      }
    })
  }

  useEffect(() => {
    if (!isAuthenticated) {
      return undefined
    }
    let cancelled = false

    const process = (rows) => {
      const { maxId, alert, fresh } = selectAlerts(rows, watermark.current)
      if (maxId !== watermark.current) {
        watermark.current = maxId
      }
      if (fresh.length > 0) {
        // The rows ride along so the Notifications page can prepend them
        // immediately, and a missed event is still caught by the poll below.
        announceNotifications(fresh)
      }
      if (alert) {
        alertFor(alert)
      }
    }

    const poll = async () => {
      if (cancelled || inFlight.current) return
      inFlight.current = true
      try {
        process(await api.listNotifications())
      } catch {
        // A 401 is handled globally by api.js: the session is cleared and the
        // UI redirects to login.
      } finally {
        inFlight.current = false
      }
    }

    const nextDelay = () => {
      if (document.hidden) return POLL_HIDDEN_MS
      return streamHealthy.current ? POLL_WITH_STREAM_MS : POLL_MS
    }

    const schedule = () => {
      if (cancelled) return
      timer.current = setTimeout(async () => {
        await poll()
        schedule()
      }, nextDelay())
    }

    const openStream = () => {
      const token = getToken()
      if (!token || cancelled || source.current) return
      // EventSource cannot set an Authorization header. lastId lets a reconnect
      // catch up on what it missed.
      const stream = new EventSource(
        `/api/notifications/stream?token=${encodeURIComponent(token)}&lastId=${watermark.current}`,
      )
      source.current = stream
      stream.onopen = () => {
        streamHealthy.current = true
        reconnectDelay.current = 1000
      }
      stream.addEventListener('notification', (event) => {
        try {
          process([JSON.parse(event.data)])
        } catch {
          // Ignore malformed frames; polling will still catch the event.
        }
      })
      stream.onerror = () => {
        streamHealthy.current = false
        stream.close()
        source.current = null
        // Reconnect with bounded backoff rather than silently falling back to
        // the slow poll until the tab is next shown. The next stream's lastId
        // is the watermark, so it catches up on anything missed.
        if (cancelled || reconnectTimer.current) return
        const delay = reconnectDelay.current
        reconnectDelay.current = Math.min(delay * 2, 30000)
        reconnectTimer.current = setTimeout(() => {
          reconnectTimer.current = null
          openStream()
        }, delay)
      }
    }

    const refresh = () => {
      clearTimeout(timer.current)
      if (reconnectTimer.current) {
        clearTimeout(reconnectTimer.current)
        reconnectTimer.current = null
      }
      reconnectDelay.current = 1000
      openStream()
      poll().then(schedule)
    }

    // Prime the watermark from the existing feed before opening the stream, so
    // a restart does not replay the backlog as if it were new. This fetch only
    // seeds the watermark (no announce, no alert); the stream's lastId catches
    // up on anything that arrived while it ran.
    const prime = async () => {
      watermark.current = 0
      try {
        const rows = await api.listNotifications()
        watermark.current = rows.reduce(
          (highest, notification) => Math.max(highest, notification.id ?? 0),
          watermark.current,
        )
      } catch {
        // Leave the watermark at 0; the stream and poll will recover.
      }
      if (cancelled) {
        return
      }
      openStream()
      schedule()
    }

    prime()
    document.addEventListener('visibilitychange', refresh)
    window.addEventListener('online', refresh)

    return () => {
      cancelled = true
      clearTimeout(timer.current)
      clearTimeout(reconnectTimer.current)
      reconnectTimer.current = null
      if (source.current) {
        source.current.close()
        source.current = null
      }
      document.removeEventListener('visibilitychange', refresh)
      window.removeEventListener('online', refresh)
    }
  }, [isAuthenticated, user])
}
