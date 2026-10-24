import { useEffect, useRef } from 'react'
import { Box, Text } from '@mantine/core'

// The dB range the bar spans. Anything at or below the floor is silence; the
// old -60 dB floor let ordinary room noise sit a third of the way up and
// twitch, so the floor now sits just above the capture's own silence gate.
const FLOOR_DB = -42
// Below this fraction of the bar the meter snaps to empty, so a quiet room
// settles at zero instead of flickering around a few percent.
const DEADBAND = 0.04
// How fast the bar falls, in full-scale units per second. Slow enough that the
// server's ~2 Hz updates look like a meter rather than steps.
const RELEASE_PER_SECOND = 0.8
const WIDTH = 120
const HEIGHT = 10

function levelOf(rms) {
  if (!rms || rms <= 0) {
    return 0
  }
  const db = 20 * Math.log10(rms)
  if (db <= FLOOR_DB) {
    return 0
  }
  return Math.min(1, (db - FLOOR_DB) / -FLOOR_DB)
}

function rmsOf(analyser) {
  const samples = new Float32Array(analyser.fftSize)
  analyser.getFloatTimeDomainData(samples)
  let sum = 0
  for (let i = 0; i < samples.length; i += 1) {
    sum += samples[i] * samples[i]
  }
  return Math.sqrt(sum / samples.length)
}

// A small VU-style meter. `rms` is the server's latest analysis window (used
// while not listening, so the meter is alive before the user clicks Listen);
// `analyser` taps the live Web Audio graph once it is. Decay and peak hold run
// on requestAnimationFrame and write straight to the DOM, so a 60 fps meter
// does not re-render React.
export default function LevelMeter({ rms = 0, analyser = null, active = false }) {
  const trackRef = useRef(null)
  const fillRef = useRef(null)
  const peakRef = useRef(null)
  const labelRef = useRef(null)
  const rmsRef = useRef(rms)
  const analyserRef = useRef(analyser)
  const activeRef = useRef(active)
  rmsRef.current = rms
  analyserRef.current = analyser
  activeRef.current = active

  useEffect(() => {
    let frame
    let shown = 0
    let peak = 0
    let peakAt = 0
    let smoothed = 0
    let last = performance.now()

    const tick = (now) => {
      const dt = Math.min(0.1, Math.max(0, (now - last) / 1000))
      last = now
      const live = analyserRef.current ? rmsOf(analyserRef.current) : rmsRef.current
      // Smooth the raw RMS before mapping it: the idle value updates only about
      // twice a second and room noise wobbles, which made the bar twitch.
      smoothed += (live - smoothed) * Math.min(1, dt * 6)
      let target = activeRef.current ? levelOf(smoothed) : 0
      if (target < DEADBAND) {
        target = 0
      }

      // Fast attack, slow release, so the server's ~2 Hz value still looks like
      // a meter rather than a set of steps.
      shown = target >= shown ? target : Math.max(target, shown - dt * RELEASE_PER_SECOND)
      if (shown >= peak) {
        peak = shown
        peakAt = now
      } else if (now - peakAt > 1200) {
        peak = Math.max(shown, peak - dt * 0.8)
      }

      if (fillRef.current) {
        fillRef.current.style.width = `${(shown * 100).toFixed(1)}%`
      }
      if (peakRef.current) {
        peakRef.current.style.left = `${(peak * 100).toFixed(1)}%`
        peakRef.current.style.opacity = peak > 0.02 ? '1' : '0'
      }
      if (trackRef.current) {
        trackRef.current.setAttribute('aria-valuenow', String(Math.round(shown * 100)))
      }
      if (labelRef.current) {
        labelRef.current.textContent =
          activeRef.current && target > 0 ? `${Math.round(20 * Math.log10(smoothed))} dB` : '—'
      }
      frame = requestAnimationFrame(tick)
    }

    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [])

  return (
    <Box style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <Text
        ref={labelRef}
        size="xs"
        c="dimmed"
        style={{ fontVariantNumeric: 'tabular-nums', minWidth: 42, textAlign: 'right' }}
      >
        —
      </Text>
      <Box
        ref={trackRef}
        role="meter"
        aria-label="Sound level"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={0}
        style={{
          position: 'relative',
          width: WIDTH,
          height: HEIGHT,
          borderRadius: 999,
          background: 'var(--mantine-color-gray-3)',
          overflow: 'hidden',
        }}
      >
        <Box
          ref={fillRef}
          style={{
            width: '0%',
            height: '100%',
            background:
              'linear-gradient(90deg, #2f9e44 0%, #2f9e44 55%, #f08c00 78%, #e03131 100%)',
            backgroundSize: `${WIDTH}px 100%`,
            backgroundRepeat: 'no-repeat',
          }}
        />
        <Box
          ref={peakRef}
          style={{
            position: 'absolute',
            top: 0,
            left: '0%',
            width: 2,
            height: '100%',
            background: 'var(--mantine-color-dark-8)',
            opacity: 0,
          }}
        />
      </Box>
    </Box>
  )
}
