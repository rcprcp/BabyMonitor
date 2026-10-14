import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'

const severityLabel = {
  info: 'Info',
  warning: 'Warning',
  critical: 'Critical',
}

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

  const markRead = async (id) => {
    await api.markNotificationRead(id)
    setNotifications((current) => current.map((n) => (n.id === id ? { ...n, read: true } : n)))
  }

  const dismiss = async (id) => {
    await api.dismissNotification(id)
    setNotifications((current) => current.filter((n) => n.id !== id))
  }

  const unread = notifications.filter((n) => !n.read).length

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

      {notifications.length > 0 && (
        <p className="muted">
          {notifications.length} total, {unread} unread
        </p>
      )}

      <ul className="notification-list">
        {notifications.map((notification) => (
          <li
            key={notification.id}
            className={`notification ${notification.read ? 'read' : 'unread'} severity-${notification.severity}`}
          >
            <div className="notification-main">
              <div className="notification-head">
                <span className={`badge badge-${notification.severity}`}>
                  {severityLabel[notification.severity] || notification.severity}
                </span>
                <strong>{notification.title}</strong>
                {!notification.read && <span className="dot" aria-label="unread" />}
              </div>
              <p>{notification.message}</p>
              <small className="muted">{new Date(notification.timestamp).toLocaleString()}</small>
            </div>
            <div className="notification-actions">
              {!notification.read && (
                <button type="button" className="button-secondary" onClick={() => markRead(notification.id)}>
                  Mark read
                </button>
              )}
              <button type="button" className="button-danger" onClick={() => dismiss(notification.id)}>
                Dismiss
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
