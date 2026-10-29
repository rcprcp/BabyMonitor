import { useCallback, useEffect, useRef, useState } from 'react'
import { Badge, Button, Group, Slider, Text } from '@mantine/core'
import LevelMeter from './LevelMeter'
import { api } from '../api'

const VOLUME_KEY = 'babymonitor.listen.volume'
const POLL_MS = 600
const RETRY_MS = 2000

function storedVolume() {
  const value = Number(localStorage.getItem(VOLUME_KEY))
  return Number.isFinite(value) && value >= 0 && value <= 1 ? value : 0.7
}

// Plays the selected camera's live audio.
//
// The capture's PCM arrives as an endless chunked response (signed 16-bit
// little-endian mono at the sample rate in the response header), which an
// <audio> element cannot authenticate or decode, so this reads it with fetch()
// and schedules AudioBuffers through the Web Audio API. Browsers only allow a
// page to make sound after a user gesture, hence the Listen button.
export default function AudioListener({ cameraNumber }) {
  const [listening, setListening] = useState(false)
  const [volume, setVolume] = useState(storedVolume)
  const [captureRunning, setCaptureRunning] = useState(false)
  const [lastRms, setLastRms] = useState(0)
  const [analyser, setAnalyser] = useState(null)
  const [status, setStatus] = useState('idle') // idle|connecting|playing|reconnecting|off

  const contextRef = useRef(null)
  const gainRef = useRef(null)
  const analyserRef = useRef(null)
  const volumeRef = useRef(volume)
  volumeRef.current = volume

  const ensureAudio = useCallback(() => {
    if (!contextRef.current) {
      const AudioContextClass = window.AudioContext || window.webkitAudioContext
      const context = new AudioContextClass()
      const gain = context.createGain()
      gain.gain.value = volumeRef.current
      // Tap the signal before the volume control: the meter should show what
      // the microphone hears, not how loud the user chose to listen.
      const meter = context.createAnalyser()
      meter.fftSize = 2048
      meter.connect(gain)
      gain.connect(context.destination)
      contextRef.current = context
      gainRef.current = gain
      analyserRef.current = meter
      setAnalyser(meter)
    }
    if (contextRef.current.state === 'suspended') {
      contextRef.current.resume().catch(() => {})
    }
    return contextRef.current
  }, [])

  // Whether the selected camera is actually capturing: a stopped camera has no
  // audio to play, and the supervisor may bring one back.
  useEffect(() => {
    let cancelled = false
    const poll = async () => {
      try {
        const audio = await api.getCameraAudio(cameraNumber)
        if (!cancelled) {
          setCaptureRunning(Boolean(audio?.running))
          setLastRms(Number(audio?.lastRms) || 0)
        }
      } catch {
        if (!cancelled) {
          setCaptureRunning(false)
          setLastRms(0)
        }
      }
    }
    poll()
    const id = setInterval(poll, POLL_MS)
    return () => {
      cancelled = true
      clearInterval(id)
    }
  }, [cameraNumber])

  // Volume changes apply to the live gain node and are remembered.
  useEffect(() => {
    localStorage.setItem(VOLUME_KEY, String(volume))
    if (gainRef.current) {
      gainRef.current.gain.value = volume
    }
  }, [volume])

  // Close the audio graph when the page goes away.
  useEffect(
    () => () => {
      if (contextRef.current) {
        contextRef.current.close().catch(() => {})
      }
    },
    [],
  )

  // The streaming loop: fetch the endless PCM response and schedule it.
  useEffect(() => {
    if (!listening || !captureRunning) {
      setStatus(captureRunning ? 'idle' : 'off')
      return undefined
    }

    let cancelled = false
    const controller = new AbortController()
    const context = ensureAudio()
    const gain = gainRef.current
    let nextStart = 0
    let carry = new Uint8Array(0)

    const play = (bytes, sampleRate) => {
      if (!gain || bytes.length < 2) {
        return
      }
      const samples = Math.floor(bytes.length / 2)
      const buffer = context.createBuffer(1, samples, sampleRate)
      const data = buffer.getChannelData(0)
      const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
      for (let i = 0; i < samples; i += 1) {
        data[i] = view.getInt16(i * 2, true) / 32768
      }
      const source = context.createBufferSource()
      source.buffer = buffer
      // Into the analyser (which feeds the gain), so the meter sees the raw
      // capture level regardless of the listener's volume.
      source.connect(analyserRef.current || gain)
      const now = context.currentTime
      // Keep a small jitter buffer and recover from an underrun rather than
      // scheduling in the past.
      if (nextStart < now + 0.05) {
        nextStart = now + 0.2
      }
      source.start(nextStart)
      nextStart += buffer.duration
      source.onended = () => source.disconnect()
    }

    // A read may split a 16-bit sample; carry the odd byte to the next chunk.
    const wholeSamples = (chunk) => {
      let bytes = chunk
      if (carry.length > 0) {
        const joined = new Uint8Array(carry.length + bytes.length)
        joined.set(carry, 0)
        joined.set(bytes, carry.length)
        bytes = joined
      }
      if (bytes.length % 2 === 1) {
        carry = bytes.slice(-1)
        return bytes.subarray(0, bytes.length - 1)
      }
      carry = new Uint8Array(0)
      return bytes
    }

    const run = async () => {
      setStatus('connecting')
      while (!cancelled) {
        try {
          const response = await api.openCameraAudioStream(cameraNumber, controller.signal)
          if (!response.ok) {
            throw new Error(`Audio stream failed (${response.status})`)
          }
          const sampleRate = Number(response.headers.get('X-Audio-Sample-Rate')) || 16000
          carry = new Uint8Array(0)
          nextStart = context.currentTime + 0.2
          setStatus('playing')
          const reader = response.body.getReader()
          while (!cancelled) {
            const { done, value } = await reader.read()
            if (done) {
              break
            }
            play(wholeSamples(value), sampleRate)
          }
        } catch (error) {
          if (cancelled || controller.signal.aborted) {
            return
          }
        }
        if (cancelled) {
          return
        }
        setStatus('reconnecting')
        await new Promise((resolve) => setTimeout(resolve, RETRY_MS))
      }
    }

    run()

    return () => {
      cancelled = true
      controller.abort()
    }
  }, [cameraNumber, captureRunning, listening, ensureAudio])

  const toggle = () => {
    if (listening) {
      setListening(false)
      if (contextRef.current) {
        contextRef.current.close().catch(() => {})
        contextRef.current = null
        gainRef.current = null
        analyserRef.current = null
        setAnalyser(null)
      }
    } else {
      ensureAudio() // the click is the gesture browsers require
      setListening(true)
    }
  }

  const resume = () => {
    if (contextRef.current && contextRef.current.state === 'suspended') {
      contextRef.current.resume().catch(() => {})
    }
  }

  const label = !captureRunning
    ? 'Camera audio off'
    : status === 'playing'
      ? 'Listening'
      : status === 'reconnecting'
        ? 'Reconnecting…'
        : status === 'connecting'
          ? 'Connecting…'
          : 'Paused'

  return (
    <Group gap="sm" align="center" onClick={resume} wrap="wrap">
      <Button
        variant={listening ? 'filled' : 'default'}
        onClick={toggle}
        disabled={!listening && !captureRunning}
      >
        {listening ? 'Stop listening' : 'Start listening'}
      </Button>
      <Badge
        color={status === 'playing' ? 'green' : captureRunning ? 'yellow' : 'gray'}
        variant="light"
      >
        {label}
      </Badge>
      <LevelMeter rms={lastRms} analyser={analyser} active={captureRunning} />
      <Text size="sm" c="dimmed">
        Volume
      </Text>
      <Slider
        aria-label="Volume"
        value={Math.round(volume * 100)}
        onChange={(value) => setVolume(value / 100)}
        min={0}
        max={100}
        w={140}
        disabled={!listening}
      />
    </Group>
  )
}
