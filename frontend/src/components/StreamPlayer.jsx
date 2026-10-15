import { useEffect, useRef, useState } from 'react'
import { Code, Stack, Text } from '@mantine/core'

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

  // A new URL gets a fresh chance: without this the error box would stick
  // around after the camera comes back.
  useEffect(() => {
    setImgError(false)
  }, [url])

  if (!url) {
    return <Text c="gray.3">No stream URL is configured.</Text>
  }

  if (isHls(url)) {
    return <HlsVideo url={url} />
  }

  if (imgError) {
    return (
      <Stack align="center" gap="xs" p="md">
        <Text c="gray.3">Could not load the stream at:</Text>
        <Code>{url}</Code>
        <Text size="sm" c="dimmed" ta="center">
          The camera may be offline, or the feed may not be MJPEG/HLS. Check the
          camera&apos;s <strong>stream_port</strong> on its Camera page, or press{' '}
          <strong>Apply</strong> there to restart Motion.
        </Text>
      </Stack>
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
