import { useCallback, useEffect, useState } from 'react'
import StreamPlayer from '../components/StreamPlayer'
import { api } from '../api'

// The landing page: the live stream pulled from the streaming_url setting.
export default function StreamPage() {
  const [url, setUrl] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const setting = await api.getSetting('streaming_url')
      setUrl(setting.value)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  return (
    <section className="page">
      <div className="page-header">
        <h2>Live stream</h2>
        <button type="button" className="button-secondary" onClick={load} disabled={loading}>
          {loading ? 'Loading…' : 'Reload stream'}
        </button>
      </div>

      {error && <p className="form-error">{error}</p>}

      <div className="stream-frame">
        {loading ? <div className="stream-placeholder">Connecting…</div> : <StreamPlayer url={url} />}
      </div>

      {url && (
        <p className="muted stream-source">
          Source: <code>{url}</code>
        </p>
      )}
    </section>
  )
}
