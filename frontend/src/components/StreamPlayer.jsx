import { useEffect, useRef, useState } from 'react'

// Plays the configured stream. Two shapes are supported:
//
//   * HLS (.m3u8)  -> <video> driven by hls.js (native HLS on Safari)
//   * anything else -> <img>, which is how browsers render MJPEG
//                      (multipart/x-mixed-replace) camera feeds.
//
// If neither works the component shows the URL plus an error, rather than a
// silently blank box.
function isHls(url) {
  return /\.m3u8(\?|#|$)/i.test(url)
}

function HlsVideo({ url }) {
  const videoRef = useRef(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    const video = videoRef.current
    if (!video) {
      return undefined
    }
    setError(null)

    // Safari and some browsers can play HLS natively.
    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = url
      return undefined
    }

    let hls
    let cancelled = false
    import('hls.js')
      .then(({ default: Hls }) => {
        if (cancelled) {
          return
        }
        if (!Hls.isSupported()) {
          setError('This browser cannot play HLS streams.')
          return
        }
        hls = new Hls()
        hls.on(Hls.Events.ERROR, (_event, data) => {
          if (data.fatal) {
            setError('Could not load the HLS stream.')
          }
        })
        hls.loadSource(url)
        hls.attachMedia(video)
      })
      .catch(() => setError('Could not load the HLS player.'))

    return () => {
      cancelled = true
      if (hls) {
        hls.destroy()
      }
      video.removeAttribute('src')
    }
  }, [url])

  return (
    <video ref={videoRef} className="stream-media" controls autoPlay muted playsInline>
      {error && <p>{error}</p>}
    </video>
  )
}

export default function StreamPlayer({ url }) {
  const [imgError, setImgError] = useState(false)

  if (!url) {
    return <div className="stream-placeholder">No stream URL is configured.</div>
  }

  if (isHls(url)) {
    return <HlsVideo url={url} />
  }

  if (imgError) {
    return (
      <div className="stream-placeholder stream-error">
        <p>Could not load the stream at:</p>
        <code>{url}</code>
        <p className="muted">
          The camera may be offline, or the feed may not be MJPEG/HLS. Update the
          <strong> streaming_url</strong> setting on the Settings page if it has moved.
        </p>
      </div>
    )
  }

  return (
    <img
      className="stream-media"
      src={url}
      alt="Live baby monitor stream"
      onError={() => setImgError(true)}
    />
  )
}
