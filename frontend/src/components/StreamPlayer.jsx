import { useCallback, useEffect, useRef, useState } from 'react'
import { Badge, Box, Button, Code, Group, Stack, Text } from '@mantine/core'

// Plays the configured stream. Two shapes are supported:
//
//   * HLS (.m3u8)  -> <video> driven by hls.js (native HLS on Safari)
//   * anything else -> <img>, which is how browsers render MJPEG
//                      (multipart/x-mixed-replace) camera feeds.
//
// A monitor must never look live while it is frozen, so this also watches the
// last frame. A stream that has not produced a frame for STALL_MS is marked
// stale and reconnected; the same watchdog catches a stream that never starts.
// The Retry control reconnects immediately, and a failed stream keeps retrying
// every ERROR_RETRY_MS instead of waiting for a page reload.
const STALL_MS = 15_000
const ERROR_RETRY_MS = 15_000
const TICK_MS = 1_000
const QUICK_RETRY_INTERVALS_MS = [1_000, 2_000, 3_000, 5_000]

function isHls(url) {
  return /\.m3u8(\?|#|$)/i.test(url)
}

function formatAge(ageMs) {
  if (ageMs < 1_000) {
    return 'just now'
  }
  if (ageMs < 60_000) {
    return `${Math.floor(ageMs / 1_000)}s ago`
  }
  return `${Math.floor(ageMs / 60_000)}m ${Math.floor((ageMs % 60_000) / 1_000)}s ago`
}

function cacheBust(url, attempt) {
  if (attempt === 0) {
    return url
  }
  return `${url}${url.includes('?') ? '&' : '?'}_cache=${Date.now()}-${attempt}`
}

function HlsVideo({ url, attempt, onFrame, onError }) {
  const videoRef = useRef(null)

  useEffect(() => {
    const video = videoRef.current
    if (!video) {
      return undefined
    }

    // A playing video emits these often; any of them is proof of a new frame.
    const frameEvents = ['loadeddata', 'timeupdate', 'playing', 'progress']
    const markFrame = () => onFrame()
    frameEvents.forEach((event) => video.addEventListener(event, markFrame))
    const markError = () => onError()
    video.addEventListener('error', markError)

    // Safari and some browsers can play HLS natively.
    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = url
      video.play().catch(() => {})
      return () => {
        frameEvents.forEach((event) => video.removeEventListener(event, markFrame))
        video.removeEventListener('error', markError)
        video.removeAttribute('src')
      }
    }

    let hls
    let cancelled = false
    import('hls.js')
      .then(({ default: Hls }) => {
        if (cancelled) {
          return
        }
        if (!Hls.isSupported()) {
          onError()
          return
        }
        hls = new Hls()
        hls.on(Hls.Events.ERROR, (_event, data) => {
          if (data.fatal) {
            onError()
          }
        })
        hls.loadSource(url)
        hls.attachMedia(video)
      })
      .catch(() => {
        if (!cancelled) {
          onError()
        }
      })

    return () => {
      cancelled = true
      frameEvents.forEach((event) => video.removeEventListener(event, markFrame))
      video.removeEventListener('error', markError)
      if (hls) {
        hls.destroy()
      }
      video.removeAttribute('src')
    }
    // `attempt` remounts the source after an automatic or manual retry.
  }, [url, attempt, onFrame, onError])

  return <video ref={videoRef} className="stream-media" controls autoPlay muted playsInline />
}

export default function StreamPlayer({ url, reloadKey = 0 }) {
  const [attempt, setAttempt] = useState(0)
  const [now, setNow] = useState(() => Date.now())
  const [hasFrame, setHasFrame] = useState(false)
  const [stalled, setStalled] = useState(false)
  const [failed, setFailed] = useState(false)

  const lastFrameRef = useRef(Date.now())
  const lastStallRetryRef = useRef(Date.now())
  const quickRetryRef = useRef(null)
  const retryCountRef = useRef(0)
  const hasFrameRef = useRef(false)
  const stalledRef = useRef(false)
  const failedRef = useRef(false)

  const clearQuickRetry = useCallback(() => {
    if (quickRetryRef.current) {
      clearTimeout(quickRetryRef.current)
      quickRetryRef.current = null
    }
  }, [])

  const markFrame = useCallback(() => {
    const at = Date.now()
    lastFrameRef.current = at
    lastStallRetryRef.current = at
    retryCountRef.current = 0
    clearQuickRetry()

    // A live MJPEG feed can call this per frame; only touch React state when
    // the visible status actually changes.
    if (!hasFrameRef.current) {
      hasFrameRef.current = true
      setHasFrame(true)
      setNow(at)
    }
    if (stalledRef.current) {
      stalledRef.current = false
      setStalled(false)
    }
    if (failedRef.current) {
      failedRef.current = false
      setFailed(false)
    }
  }, [clearQuickRetry])

  const fail = useCallback(() => {
    clearQuickRetry()
    failedRef.current = true
    setFailed(true)
    setNow(Date.now())
  }, [clearQuickRetry])

  // MJPEG errors get a few quick retries before the error state; the error
  // state itself keeps retrying on a slower timer.
  const handleMediaError = useCallback(() => {
    const delay = QUICK_RETRY_INTERVALS_MS[retryCountRef.current]
    if (delay === undefined) {
      fail()
      return
    }
    retryCountRef.current += 1
    clearQuickRetry()
    quickRetryRef.current = setTimeout(() => {
      quickRetryRef.current = null
      lastFrameRef.current = Date.now()
      setAttempt((value) => value + 1)
    }, delay)
  }, [clearQuickRetry, fail])

  const requestRetry = useCallback(() => {
    clearQuickRetry()
    retryCountRef.current = 0
    failedRef.current = false
    stalledRef.current = false
    hasFrameRef.current = false
    lastFrameRef.current = Date.now()
    lastStallRetryRef.current = Date.now()
    setFailed(false)
    setStalled(false)
    setHasFrame(false)
    setNow(Date.now())
    setAttempt((value) => value + 1)
  }, [clearQuickRetry])

  // A new URL (or a page-level reload) gets a fresh chance. Without this the
  // old status would stick around after the camera comes back.
  useEffect(() => {
    clearQuickRetry()
    retryCountRef.current = 0
    failedRef.current = false
    stalledRef.current = false
    hasFrameRef.current = false
    lastFrameRef.current = Date.now()
    lastStallRetryRef.current = Date.now()
    setAttempt(0)
    setFailed(false)
    setStalled(false)
    setHasFrame(false)
    setNow(Date.now())
  }, [url, reloadKey, clearQuickRetry])

  // Retry a failed stream forever, slowly, so a camera that comes back does
  // not need a page reload. Manual Retry uses the same path immediately.
  useEffect(() => {
    if (!failed || !url) {
      return undefined
    }
    const id = setTimeout(() => requestRetry(), ERROR_RETRY_MS)
    return () => clearTimeout(id)
  }, [failed, url, requestRetry])

  // The stall watchdog: no new frame for STALL_MS means reconnect. It also
  // covers a stream that never produces its first frame.
  useEffect(() => {
    if (!url) {
      return undefined
    }
    const id = setInterval(() => {
      const at = Date.now()
      setNow(at)
      if (failedRef.current) {
        return
      }
      if (at - lastFrameRef.current < STALL_MS) {
        return
      }
      if (!stalledRef.current) {
        stalledRef.current = true
        setStalled(true)
      }
      // Keep retrying while stale: once the first reconnect has not helped,
      // try again every STALL_MS rather than giving up on the frozen frame.
      if (at - lastStallRetryRef.current >= STALL_MS) {
        lastStallRetryRef.current = at
        retryCountRef.current = 0
        setAttempt((value) => value + 1)
      }
    }, TICK_MS)
    return () => clearInterval(id)
  }, [url])

  useEffect(
    () => () => {
      clearQuickRetry()
    },
    [clearQuickRetry],
  )

  if (!url) {
    return <Text c="gray.3">No stream URL is configured.</Text>
  }

  const age = Math.max(0, now - lastFrameRef.current)
  const status = failed ? 'error' : stalled ? 'stalled' : hasFrame ? 'live' : 'connecting'
  const statusLabel = {
    live: 'Live',
    connecting: 'Connecting…',
    stalled: hasFrame ? 'Stream stalled' : 'No frames yet',
    error: 'Stream error',
  }[status]
  const statusColor = {
    live: 'green',
    connecting: 'gray',
    stalled: 'yellow',
    error: 'red',
  }[status]
  const frameTime = new Date(lastFrameRef.current).toLocaleTimeString()
  const detail =
    status === 'live'
      ? `Last frame ${frameTime} (${formatAge(age)})`
      : status === 'connecting'
        ? 'Waiting for the first frame…'
        : status === 'stalled'
          ? hasFrame
            ? `Last frame ${frameTime} (${formatAge(age)}) · reconnecting automatically`
            : 'No frame received yet · reconnecting automatically'
          : `Reconnecting automatically every ${ERROR_RETRY_MS / 1_000} seconds.`

  return (
    <Stack gap="xs">
      <Group justify="space-between" align="center" gap="sm" wrap="wrap">
        <Group gap="xs" align="center">
          <Badge color={statusColor} variant="light">
            {statusLabel}
          </Badge>
          <Text size="xs" c="dimmed">
            {detail}
          </Text>
        </Group>
        <Button size="xs" variant="light" onClick={requestRetry}>
          Retry
        </Button>
      </Group>

      <Box className="stream-frame">
        {failed ? (
          <Stack align="center" gap="xs" p="md">
            <Text c="gray.3">Could not load the stream at:</Text>
            <Code>{url}</Code>
            <Text size="sm" c="dimmed" ta="center">
              The camera may be offline, or the feed may not be MJPEG/HLS. Check the stream port on
              its Camera page, then press <strong>Restart camera</strong> there. This page keeps
              trying, or press <strong>Retry</strong>.
            </Text>
          </Stack>
        ) : (
          <>
            {isHls(url) ? (
              <HlsVideo
                url={url}
                attempt={attempt}
                onFrame={markFrame}
                onError={handleMediaError}
              />
            ) : (
              <img
                className="stream-media"
                src={cacheBust(url, attempt)}
                alt="Live baby monitor stream"
                onError={handleMediaError}
                onLoad={markFrame}
              />
            )}
            {(!hasFrame || stalled) && (
              <Box className="stream-overlay">
                <Stack align="center" gap={4}>
                  <Text fw={600} c="white">
                    {status === 'stalled'
                      ? hasFrame
                        ? 'Stream stalled'
                        : 'No frames yet'
                      : 'Connecting…'}
                  </Text>
                  <Text size="sm" c="gray.4" ta="center">
                    {status === 'stalled'
                      ? hasFrame
                        ? `No new frame for ${formatAge(age).replace(' ago', '')}. Reconnecting…`
                        : 'No frame has arrived. Reconnecting…'
                      : 'Waiting for the camera to send a frame.'}
                  </Text>
                </Stack>
              </Box>
            )}
          </>
        )}
      </Box>
    </Stack>
  )
}
