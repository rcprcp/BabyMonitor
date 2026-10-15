import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'
import { useAuth } from '../auth'

// The signed-in user's own record, pulled from babymonitor.users and shown
// in the main content area. Reached by clicking the initial in the title bar.
function initialOf(name) {
  const trimmed = (name ?? '').trim()
  return trimmed ? trimmed.charAt(0).toUpperCase() : '?'
}

export default function ProfilePage() {
  const { user } = useAuth()
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setProfile(await api.getProfile())
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  // Prefer the freshly loaded record, but fall back to the stored name while
  // the request is in flight.
  const displayName = profile?.name ?? user ?? ''

  return (
    <section className="page">
      <div className="page-header">
        <h2>Personal settings</h2>
        <button type="button" className="button-secondary" onClick={load} disabled={loading}>
          {loading ? 'Loading…' : 'Refresh'}
        </button>
      </div>

      {error && <p className="form-error">{error}</p>}
      {loading && <p className="muted">Loading…</p>}

      {!loading && profile && (
        <div className="profile-card">
          <div className="profile-avatar" aria-hidden="true">
            {initialOf(displayName)}
          </div>
          <div className="profile-body">
            <h3 className="profile-name">{profile.name}</h3>
            <p className="muted">Your account record, read from the database.</p>
            <dl className="profile-fields">
              <div className="profile-field">
                <dt>Name</dt>
                <dd>{profile.name}</dd>
              </div>
              <div className="profile-field">
                <dt>User ID</dt>
                <dd>{profile.id}</dd>
              </div>
            </dl>
          </div>
        </div>
      )}
    </section>
  )
}
