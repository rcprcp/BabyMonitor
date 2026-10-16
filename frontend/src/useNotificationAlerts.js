// Keeps the parent alerted to new notifications, from anywhere in the app.
//
// Two delivery paths run at once: a server-sent event stream gives near-instant
// push, and polling is the safety net that always works (a backgrounded tab, a
// dropped connection, or a browser without EventSource). Whichever notices a new
// notification first alerts; the second sees it is already past the watermark.

import { useEffect, useRef } from 'react'
import { api, getToken } from './api'
import { useAuth } from './auth'
import {
  alertsEnabled,
  announceNotifications,
  playAlertTone,
  readWatermark,
  selectAlerts,
  showBrowserNotification,
  withAlertLock,
  writeWatermark,
} from './alerting'

// With the stream healthy (or the tab hidden) we can poll lazily; otherwise a
// few seconds keeps alerts prompt without hammering the LAN.
const POLL_MS = 5000
const POLL_HIDDEN_MS = 20000
const POLL_WITH_STREAM_MS = 60000

export default function useNotificationAlerts() {
  const { isAuthenticated, user } = useAuth()
  const timer = useRef(null)
  const source = useRef(null)
  const inFlight = useRef(false)
  const streamHealthy = useRef(false)

  useEffect(() => {
    if (!isAuthenticated) {
      return undefined
    }
    let cancelled = false
    const watermark = { current: readWatermark() }

    const alertFor = (notification) => {
      if (!alertsEnabled()) {
        return
      }
      // One tab wins per id, so several open tabs don't all alert at once.
      withAlertLock(notification.id, () => {
        if (notification.eventType === 'Audio') {
          playAlertTone()
        }
        showBrowserNotification(notification)
      })
    }

    const process = (rows) => {
      const before = watermark.current
      const { maxId, alert } = selectAlerts(rows, watermark.current)
      if (maxId !== watermark.current) {
        watermark.current = maxId
        writeWatermark(maxId)
      }
      if (maxId !== before) {
        announceNotifications()
      }
      if (alert) {
        alertFor(alert)
      }
    }

    const poll = async () => {
      if (cancelled || inFlight.current) {
        return
      }
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
      if (document.hidden) {
        return POLL_HIDDEN_MS
      }
      return streamHealthy.current ? POLL_WITH_STREAM_MS : POLL_MS
    }

    const schedule = () => {
      if (cancelled) {
        return
      }
      timer.current = setTimeout(async () => {
        await poll()
        schedule()
      }, nextDelay())
    }

    const openStream = () => {
      const token = getToken()
      if (!token || cancelled || source.current) {
        return
      }
      // EventSource cannot set an Authorization header, so the token goes in the
      // query string for this one endpoint (see AuthFilter). lastId lets a
      // reconnect (or first load) catch up on what it missed.
      const url =
        `/api/notifications/stream?token=${encodeURIComponent(token)}` +
        `&lastId=${watermark.current}`
      const stream = new EventSource(url)
      source.current = stream
      stream.onopen = () => {
        streamHealthy.current = true
      }
      stream.addEventListener('notification', (event) => {
        try {
          process([JSON.parse(event.data)])
        } catch {
          // Ignore a malformed frame; polling will still catch the event.
        }
      })
      stream.onerror = () => {
        // Close rather than let EventSource retry forever against a token that
        // may have expired. Polling carries on and surfaces a 401 normally.
        streamHealthy.current = false
        stream.close()
        source.current = null
      }
    }

    // An immediate poll (e.g. the tab became visible) also re-opens the stream.
    const refresh = () => {
      clearTimeout(timer.current)
      openStream()
      poll().then(schedule)
    }

    poll().then(schedule)
    openStream()
    document.addEventListener('visibilitychange', refresh)
    window.addEventListener('online', refresh)

    return () => {
      cancelled = true
      clearTimeout(timer.current)
      if (source.current) {
        source.current.close()
        source.current = null
      }
      document.removeEventListener('visibilitychange', refresh)
      window.removeEventListener('online', refresh)
    }
  }, [isAuthenticated, user])
}
