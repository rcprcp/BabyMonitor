import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'

// DB-backed feed: rows come from babymonitor.notifications and are written by
// the device webhooks, so video events link to the relevant clip.
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
    <section className="page">
      <div className="page-header">
        <h2>Notifications</h2>
        <button type="button" className="button-secondary" onClick={load} disabled={loading}>
          {loading ? 'Loading…' : 'Refresh'}
        </button>
      </div>

      {error && <p className="form-error">{error}</p>}

      {!loading && notifications.length === 0 && <p className="muted">No notifications.</p>}

      <ul className="notification-list">
        {notifications.map((notification) => {
          const kind = notification.eventType.toLowerCase()
          return (
            <li key={notification.id} className={`notification event-type-${kind}`}>
              <div className="notification-main">
                <div className="notification-head">
                  <span className={`tag tag-${kind}`}>{notification.eventType}</span>
                  <small className="muted">{new Date(notification.eventTime).toLocaleString()}</small>
                </div>
                <p className="notification-description">{notification.description}</p>
                {notification.videoLink && (
                  <a
                    className="notification-video"
                    href={notification.videoLink}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Watch video
                  </a>
                )}
              </div>
              <div className="notification-actions">
                <button type="button" className="button-danger" onClick={() => dismiss(notification.id)}>
                  Dismiss
                </button>
              </div>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
