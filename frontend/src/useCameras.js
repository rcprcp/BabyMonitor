import { useCallback, useEffect, useState } from 'react'
import { api } from './api'

// Fired after a camera parameter is added, edited or deleted, so the
// navigation picks up a renamed camera without a page reload.
export const CAMERAS_CHANGED_EVENT = 'babymonitor:cameras-changed'

/** Announce that the camera rows changed; listeners re-read them. */
export function notifyCamerasChanged() {
  window.dispatchEvent(new Event(CAMERAS_CHANGED_EVENT))
}

/**
 * The cameras, rolled up from the parameter rows (the API returns one row per
 * key). Only the number and display name are kept: that is all a navigation
 * link needs.
 */
export function useCameras() {
  const [cameras, setCameras] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    try {
      setCameras(summarise(await api.listCameras()))
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
    window.addEventListener(CAMERAS_CHANGED_EVENT, load)
    return () => window.removeEventListener(CAMERAS_CHANGED_EVENT, load)
  }, [load])

  return { cameras, loading, error }
}

/** A camera's name, falling back to its number. */
export function cameraLabel(camera) {
  return camera.name?.trim() || `Camera ${camera.cameraNumber}`
}

/** Collapse the parameter rows into one `{cameraNumber, name}` per camera. */
export function summarise(rows) {
  const map = new Map()
  for (const row of rows ?? []) {
    const entry = map.get(row.cameraNumber) ?? { cameraNumber: row.cameraNumber, name: null }
    if (row.key === 'camera_name') {
      entry.name = row.value
    }
    map.set(row.cameraNumber, entry)
  }
  return [...map.values()].sort((a, b) => a.cameraNumber - b.cameraNumber)
}
