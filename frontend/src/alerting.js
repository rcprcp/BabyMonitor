// Night-time alerting: deciding when a new notification is worth waking the
// parent, plus the browser notification and audible tone that do it.
//
// Everything here is plain JavaScript (no React, no Mantine) so the decision
// logic stays testable and can run from anywhere. State that must survive a
// reload (whether alerts are on, and the last notification already alerted for)
// lives in localStorage, per account.

import { getName } from './api'

const ENABLED_KEY = 'babymonitor.alertsEnabled'

// Name of the window event the listener fires when new notifications arrive, so
// the Notifications page can refresh its list without polling itself.
export const NOTIFICATIONS_ARRIVED_EVENT = 'babymonitor:notifications'

// An event older than this is treated as stale: after a long disconnect the
// catch-up can dump a backlog, and waking a parent for something that happened
// last night would be worse than useless.
export const MAX_ALERT_AGE_MS = 5 * 60 * 1000

function watermarkKey() {
  return `babymonitor.alertWatermark.${getName() || 'anon'}`
}

/** Whether the parent has opted in to alerts on this device. */
export function alertsEnabled() {
  return localStorage.getItem(ENABLED_KEY) === 'true'
}

export function setAlertsEnabled(enabled) {
  if (enabled) {
    localStorage.setItem(ENABLED_KEY, 'true')
  } else {
    localStorage.removeItem(ENABLED_KEY)
  }
}

/** Highest notification id already alerted for, or 0 when nothing has been. */
export function readWatermark() {
  const value = Number(localStorage.getItem(watermarkKey()))
  return Number.isFinite(value) ? value : 0
}

export function writeWatermark(id) {
  localStorage.setItem(watermarkKey(), String(id))
}

/**
 * Picks what, if anything, to alert for out of the newest-first feed.
 *
 * Pure: reads no storage and touches no browser API, so it can be unit-tested.
 * The caller is responsible for persisting the returned {@code maxId}.
 *
 * @param rows      notifications, newest event first
 * @param watermark highest id already alerted for
 * @param now       current time in ms, injectable for tests
 * @returns {{maxId: number, alert: object|null}} the new watermark, and the
 *          notification to alert for (the most recent one, when it is fresh)
 */
export function selectAlerts(rows, watermark, now = Date.now()) {
  const fresh = (rows ?? []).filter((n) => typeof n?.id === 'number' && n.id > watermark)
  if (fresh.length === 0) {
    return { maxId: watermark, alert: null }
  }
  const maxId = fresh.reduce((highest, n) => Math.max(highest, n.id), watermark)
  // The feed is newest-first, so fresh[0] is the most recent event.
  const newest = fresh[0]
  const age = now - new Date(newest.eventTime).getTime()
  return { maxId, alert: age <= MAX_ALERT_AGE_MS ? newest : null }
}

/** Tells any interested listener that new notifications have arrived. */
export function announceNotifications() {
  window.dispatchEvent(new Event(NOTIFICATIONS_ARRIVED_EVENT))
}

// --- Browser notifications -------------------------------------------------

export function notificationPermission() {
  if (typeof window === 'undefined' || !('Notification' in window)) {
    return 'unsupported'
  }
  return Notification.permission
}

/** Asks for permission. Must be called from a user gesture in most browsers. */
export async function requestNotificationPermission() {
  if (!('Notification' in window)) {
    return 'unsupported'
  }
  if (Notification.permission !== 'default') {
    return Notification.permission
  }
  try {
    return await Notification.requestPermission()
  } catch {
    return Notification.permission
  }
}

export function showBrowserNotification(notification) {
  if (!('Notification' in window) || Notification.permission !== 'granted') {
    return
  }
  try {
    const shown = new Notification('BabyMonitor', {
      body: `${notification.eventType}: ${notification.description}`,
      // Same tag per notification: two tabs racing cannot stack duplicates.
      tag: `bm-notification-${notification.id}`,
      requireInteraction: notification.eventType === 'Audio',
    })
    shown.onclick = () => {
      window.focus()
      shown.close()
    }
  } catch {
    // Some browsers (notably Android Chrome) only allow notifications from a
    // service worker; the in-app tone still fires.
  }
}

// --- Audible tone ----------------------------------------------------------

// Created and resumed from a user gesture (the enable toggle), otherwise the
// browser blocks playback.
let audioContext = null

export function unlockAudio() {
  const Ctx = window.AudioContext || window.webkitAudioContext
  if (!Ctx) {
    return
  }
  if (!audioContext) {
    audioContext = new Ctx()
  }
  if (audioContext.state === 'suspended') {
    audioContext.resume()
  }
}

/** A short two-tone chime, audible but not alarming. */
export function playAlertTone() {
  if (!audioContext || audioContext.state !== 'running') {
    return
  }
  const start = audioContext.currentTime
  for (const [offset, frequency] of [
    [0, 880],
    [0.35, 660],
  ]) {
    const oscillator = audioContext.createOscillator()
    const gain = audioContext.createGain()
    oscillator.type = 'sine'
    oscillator.frequency.value = frequency
    gain.gain.setValueAtTime(0.0001, start + offset)
    gain.gain.exponentialRampToValueAtTime(0.3, start + offset + 0.02)
    gain.gain.exponentialRampToValueAtTime(0.0001, start + offset + 0.3)
    oscillator.connect(gain).connect(audioContext.destination)
    oscillator.start(start + offset)
    oscillator.stop(start + offset + 0.32)
  }
}

// --- Cross-tab de-duplication ----------------------------------------------

/**
 * Runs {@code action} only if no other tab is already handling this notification
 * id. Falls back to running it when the Web Locks API is unavailable; the
 * notification's {@code tag} still collapses any OS-level duplicates.
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
