// Alert delivery: showing a Mantine toast and playing a chime for every event
// that is not a System notice. No opt-in toggle, no watermarks — every event is
// unconditionally alerted.

import { notifications } from '@mantine/notifications'

// Name of the window event the listener fires when new notifications arrive, so
// the Notifications page can refresh its list without polling itself.
export const NOTIFICATIONS_ARRIVED_EVENT = 'babymonitor:notifications'

// Fired when an alert toast is clicked. A component inside the router listens
// and navigates to the Notifications page, so alerting.js stays router-free.
export const OPEN_NOTIFICATIONS_EVENT = 'babymonitor:open-notifications'

// An event older than this is treated as stale: after a long disconnect the
// catch-up can dump a backlog, and waking a parent for something that happened
// last night would be worse than useless.
export const MAX_ALERT_AGE_MS = 5 * 60 * 1000

// A default chime, served from the app's static resources.
const DEFAULT_CHIME_URL = '/sounds/notification-chime.mp3'

/** Build an alert-sound URL for the given stored sound key. */
export function getSoundUrl(name) {
  switch (name) {
    case 'alarm-loud':
      return '/sounds/alarm-loud.mp3'
    case 'doorbell':
      return '/sounds/doorbell-ding-dong.mp3'
    case 'beep':
      return '/sounds/beep-short.mp3'
    case 'chime':
      return DEFAULT_CHIME_URL
    default:
      return `/sounds/${name}.mp3`
  }
}

/**
 * Picks what, if anything, to alert for out of the newest-first feed.
 *
 * Pure: reads no storage and touches no browser API, so it can be unit-tested.
 * The caller is responsible for persisting the returned {@code maxId}.
 *
 * @param rows      notifications, newest event first
 * @param watermark highest id already alerted for (tracks cross-tab dedup)
 * @param now       current time in ms, injectable for tests
 * @returns {{maxId: number, alert: object|null, fresh: object[]}} the new
 *          watermark, the notification to alert for (the most recent one, when
 *          it is fresh), and every row past the watermark, newest first
 */
export function selectAlerts(rows, watermark, now = Date.now()) {
  const fresh = (rows ?? []).filter((n) => typeof n?.id === 'number' && n.id > watermark)
  if (fresh.length === 0) {
    return { maxId: watermark, alert: null, fresh: [] }
  }
  const maxId = fresh.reduce((highest, n) => Math.max(highest, n.id), watermark)
  // The feed is newest-first, so fresh[0] is the most recent event.
  const newest = fresh[0]
  const age = now - new Date(newest.eventTime).getTime()
  return { maxId, alert: age <= MAX_ALERT_AGE_MS ? newest : null, fresh }
}

/**
 * Tells any interested listener that new notifications have arrived. The rows
 * ride on the event so the Notifications page can prepend them immediately
 * instead of making a second round-trip (and so a back-dated event still lands
 * at the top of the live list).
 *
 * @param {object[]} notifications the fresh rows, newest first
 */
export function announceNotifications(notifications = []) {
  window.dispatchEvent(
    new CustomEvent(NOTIFICATIONS_ARRIVED_EVENT, {
      detail: { notifications },
    }),
  )
}

// --- In-app alert ----------------------------------------------------------

/**
 * Shows an alert as a Mantine notification: a dismissible toast that stacks in
 * the corner of the app. The `id` is derived from the notification so the store
 * updates an existing toast rather than stacking a duplicate.
 *
 * Clicking the toast opens the Notifications page and closes the toast. The
 * close button is excluded: it should only dismiss, not navigate.
 */
export function showAlert(notification) {
  const openNotifications = (event) => {
    // The close button is a <button> inside the toast; a click there bubbles to
    // this root handler, but it should just close the toast.
    if (event?.target?.closest?.('button')) {
      return
    }
    hideAlert(notification.id)
    window.dispatchEvent(new Event(OPEN_NOTIFICATIONS_EVENT))
  }
  notifications.show({
    id: `bm-notification-${notification.id}`,
    title: titleFor(notification.eventType),
    message: notification.description,
    color: colorFor(notification.eventType),
    withBorder: true,
    autoClose: 10000,
    style: { cursor: 'pointer' },
    onClick: openNotifications,
  })
}

/** Closes the toast for one notification, if it is still showing. */
export function hideAlert(notificationId) {
  notifications.hide(`bm-notification-${notificationId}`)
}

/** Closes every app alert toast, e.g. after Dismiss All. */
export function hideAllAlerts() {
  notifications.clean()
}

function titleFor(eventType) {
  if (eventType === 'Video') {
    return 'Video alert'
  }
  if (eventType === 'Audio') {
    return 'Audio alert'
  }
  if (eventType === 'Environment') {
    return 'Environment alert'
  }
  return 'Monitor alert'
}

function colorFor(eventType) {
  if (eventType === 'Video') {
    return 'blue'
  }
  if (eventType === 'Audio') {
    return 'blue'
  }
  if (eventType === 'Environment') {
    return 'teal'
  }
  return 'orange'
}

// --- Audible chime ---------------------------------------------------------

// One shared element per URL, so overlapping alerts of the same sound restart
// rather than layering several copies. The cache key is the file path itself.
const audioCache = new Map()

function chime(url) {
  let audio = audioCache.get(url)
  if (!audio) {
    audio = new Audio(url)
    audio.preload = 'auto'
    audioCache.set(url, audio)
  }
  return audio
}

/** Plays the chosen sound. A blocked or failed play is not fatal. */
export function playAlertSound(name) {
  const url = getSoundUrl(name)
  const audio = chime(url)
  try {
    audio.currentTime = 0
  } catch {
    // Not seekable yet on some browsers; play it from wherever it is.
  }
  const played = audio.play()
  if (played && typeof played.catch === 'function') {
    played.catch(() => {})
  }
}

// --- Cross-tab de-duplication ----------------------------------------------

/**
 * Runs {@code action} only if no other tab is already handling this notification
 * id. Falls back to running it when the Web Locks API is unavailable; the
 * notification's {@code id} still collapses any in-store duplicates.
 */
export function withAlertLock(id, action) {
  if (typeof navigator === 'undefined' || !navigator.locks) {
    action()
    return
  }
  navigator.locks.request(`bm-alert-${id}`, { ifAvailable: true }, async (lock) => {
    if (lock) {
      action()
    }
  })
}
