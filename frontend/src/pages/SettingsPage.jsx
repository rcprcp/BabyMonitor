import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'

// Application settings. Every row in babymonitor.settings is editable; the
// streaming_url one drives the Live stream page.
export default function SettingsPage() {
  const [settings, setSettings] = useState([])
  const [drafts, setDrafts] = useState({})
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [savingTag, setSavingTag] = useState(null)
  const [savedTag, setSavedTag] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const list = await api.listSettings()
      setSettings(list)
      setDrafts(Object.fromEntries(list.map((s) => [s.tag, s.value])))
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const save = async (tag) => {
    setError(null)
    setSavingTag(tag)
    setSavedTag(null)
    try {
      const updated = await api.updateSetting(tag, drafts[tag])
      setSettings((current) => current.map((s) => (s.tag === tag ? updated : s)))
      setSavedTag(tag)
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingTag(null)
    }
  }

  return (
    <section className="page">
      <div className="page-header">
        <h2>Settings</h2>
        <button type="button" className="button-secondary" onClick={load} disabled={loading}>
          {loading ? 'Loading…' : 'Refresh'}
        </button>
      </div>

      {error && <p className="form-error">{error}</p>}
      {loading && <p className="muted">Loading…</p>}
      {!loading && settings.length === 0 && <p className="muted">No settings found.</p>}

      <div className="settings-list">
        {settings.map((setting) => (
          <div className="setting-row" key={setting.tag}>
            <label htmlFor={`setting-${setting.tag}`}>{setting.tag}</label>
            <div className="setting-input">
              <input
                id={`setting-${setting.tag}`}
                type="text"
                value={drafts[setting.tag] ?? ''}
                onChange={(event) =>
                  setDrafts((current) => ({ ...current, [setting.tag]: event.target.value }))
                }
              />
              <button
                type="button"
                className="button-primary"
                onClick={() => save(setting.tag)}
                disabled={savingTag === setting.tag || drafts[setting.tag] === setting.value}
              >
                {savingTag === setting.tag ? 'Saving…' : 'Save'}
              </button>
            </div>
            {savedTag === setting.tag && <span className="saved-note">Saved</span>}
          </div>
        ))}
      </div>
    </section>
  )
}
