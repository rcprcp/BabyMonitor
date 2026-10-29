import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Card,
  Center,
  Group,
  Loader,
  NumberInput,
  Paper,
  SegmentedControl,
  Select,
  SimpleGrid,
  Stack,
  Switch,
  Table,
  Text,
  Title,
} from '@mantine/core'
import { LineChart } from '@mantine/charts'
import { api } from '../api'

// How much of the time series to show. The sensor samples hourly, so the values
// are hours.
const RANGES = [
  { label: '24 hours', value: '24' },
  { label: '3 days', value: '72' },
  { label: '7 days', value: '168' },
  { label: '14 days', value: '336' },
]

const DEFAULT_RANGE = '168'

// Temperature and humidity share the left axis (comparable 0-100 ranges);
// brightness is in lux and gets its own right-hand axis.
const SERIES = [
  { name: 'temperature', label: 'Temperature (°F)', color: 'orange.6', yAxisId: 'left' },
  { name: 'humidity', label: 'Humidity (%)', color: 'blue.6', yAxisId: 'left' },
  {
    name: 'brightness',
    label: 'Brightness',
    color: 'yellow.6',
    yAxisId: 'right',
    curveType: 'stepAfter',
  },
]

function tickLabel(value) {
  return new Date(value).toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
  })
}

// A reading outside its saved bound is called out in the table; the temperature
// comparison converts the stored Celsius reading to the Fahrenheit bound.
const OUT_OF_BOUNDS = { color: 'var(--mantine-color-red-6)', fontWeight: 600 }

function outside(value, low, high) {
  return value != null && low != null && high != null && (value < low || value > high)
}

function fahrenheit(celsius) {
  return (Number(celsius) * 9) / 5 + 32
}

// The six calibration rows. The key is the field in the camera-light API; the
// band is the key the probe returns once calibrated. Capturing a row takes one
// probe at the current fixed exposure and stores its luma.
const LIGHT_BANDS = [
  { key: 'darkLuma', band: 'dark', number: 1, label: 'Dark' },
  { key: 'veryDimLuma', band: 'very-dim', number: 2, label: 'Very dim' },
  { key: 'dimLuma', band: 'dim', number: 3, label: 'Dim' },
  { key: 'moderateLuma', band: 'moderate', number: 4, label: 'Moderate' },
  { key: 'brightLuma', band: 'bright', number: 5, label: 'Bright' },
  { key: 'veryBrightLuma', band: 'very-bright', number: 6, label: 'Very bright' },
]

const BAND_BY_NUMBER = Object.fromEntries(LIGHT_BANDS.map((band) => [band.number, band]))

const BAND_COLORS = {
  dark: 'gray',
  'very-dim': 'gray',
  dim: 'blue',
  moderate: 'teal',
  bright: 'yellow',
  'very-bright': 'orange',
}

// The environment chart's right axis is the stored band number (1-6), not a
// lux value.
function bandTickLabel(value) {
  const band = BAND_BY_NUMBER[Math.round(Number(value))]
  return band?.label ?? ''
}

// The camera-light history is bucketed by UTC hour; the environment readings
// are hourly too, so the same key lines them up.
function hourKey(value) {
  return `${new Date(value).toISOString().slice(0, 13)}:00:00Z`
}

// Recharts tooltip content: temperature and humidity stay numeric, brightness
// is shown as its band name instead of the stored band number.
function EnvironmentTooltip({ label, payload }) {
  if (!payload || payload.length === 0) {
    return null
  }
  return (
    <Paper withBorder p="xs" shadow="sm">
      <Text size="xs" c="dimmed" mb={4}>
        {label ? tickLabel(label) : ''}
      </Text>
      {payload.map((item) => {
        let value = item.value
        if (value == null) {
          value = '—'
        } else if (item.dataKey === 'brightness') {
          value = BAND_BY_NUMBER[Math.round(Number(value))]?.label ?? '—'
        } else if (item.dataKey === 'temperature') {
          value = Number(value).toFixed(1)
        } else {
          value = Number(value).toFixed(0)
        }
        return (
          <Text key={item.dataKey} size="sm">
            {item.name}: {value}
          </Text>
        )
      })}
    </Paper>
  )
}

// Ambient readings from the nursery sensor (babymonitor.environment) and the
// camera light band history (camera_light_samples), drawn as a chart above the
// raw rows. The chart plots oldest to newest, while the table lists the newest
// sample first.
export default function EnvironmentPage() {
  const [readings, setReadings] = useState([])
  const [bands, setBands] = useState([])
  const [range, setRange] = useState(DEFAULT_RANGE)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [bounds, setBounds] = useState(null)
  const [draft, setDraft] = useState(null)
  const [saving, setSaving] = useState(false)
  const [light, setLight] = useState(null)
  const [lightDraft, setLightDraft] = useState(null)
  const [lightError, setLightError] = useState(null)
  const [sampling, setSampling] = useState(null)
  const [savingLight, setSavingLight] = useState(false)
  const [cameras, setCameras] = useState([])
  const [cameraError, setCameraError] = useState(null)

  const loadReadings = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [nextReadings, nextBands] = await Promise.all([
        api.listEnvironment(Number(range)),
        api.listCameraLightBands(Number(range)),
      ])
      setReadings(nextReadings)
      setBands(nextBands)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [range])

  const loadThresholds = useCallback(async () => {
    try {
      const thresholds = await api.getEnvironmentThresholds()
      setBounds(thresholds)
      setDraft(thresholds)
    } catch (err) {
      setError(err.message)
    }
  }, [])

  const loadLight = useCallback(async () => {
    try {
      const config = await api.getCameraLight()
      setLight(config)
      setLightDraft(config)
    } catch (err) {
      setLightError(err.message)
    }
  }, [])

  useEffect(() => {
    loadReadings()
  }, [loadReadings])

  useEffect(() => {
    loadThresholds()
  }, [loadThresholds])

  useEffect(() => {
    loadLight()
    // The scheduled sampler updates the last band in the background; refresh it
    // without clobbering a calibration the user is editing.
    const timer = setInterval(() => {
      api
        .getCameraLight()
        .then(setLight)
        .catch(() => {})
    }, 10000)
    return () => clearInterval(timer)
  }, [loadLight])

  // The picker is the camera table, not a hard-coded pair. The probe reads the
  // camera's MJPEG stream, so also track whether Motion is actually running and
  // block a capture that could only fail with a 409.
  const loadCameras = useCallback(async () => {
    try {
      const parameters = await api.listCameras()
      const numbers = [...new Set(parameters.map((parameter) => parameter.cameraNumber))].sort(
        (left, right) => left - right,
      )
      const running = await Promise.all(
        numbers.map(async (cameraNumber) => {
          try {
            const status = await api.getCameraMotion(cameraNumber)
            return status.running
          } catch {
            // Keep the camera selectable; the probe still reports a real error.
            return null
          }
        }),
      )
      setCameras(
        numbers.map((cameraNumber, index) => ({
          value: String(cameraNumber),
          label: `Camera ${cameraNumber}`,
          running: running[index],
        })),
      )
      setCameraError(null)
    } catch (err) {
      setCameraError(err.message)
    }
  }, [])

  useEffect(() => {
    loadCameras()
    const timer = setInterval(loadCameras, 15000)
    return () => clearInterval(timer)
  }, [loadCameras])

  // Keep the chart's brightness band in step with the sampler. The environment
  // readings are hourly and do not need a poll; the bands can change within the
  // current hour, so refresh just the band history once a minute.
  useEffect(() => {
    const timer = setInterval(() => {
      api
        .listCameraLightBands(Number(range))
        .then(setBands)
        .catch(() => {})
    }, 60000)
    return () => clearInterval(timer)
  }, [range])

  // The API and database keep Celsius; the page presents Fahrenheit. The
  // environment reading's lux brightness is replaced by the camera light band
  // for the same hour: `brightness` is the band index for the chart and
  // `brightnessBand` is its label for the table.
  const bandByHour = useMemo(() => {
    const map = new Map()
    for (const band of bands) {
      map.set(band.hour, band.band)
    }
    return map
  }, [bands])

  const displayReadings = useMemo(
    () =>
      readings.map((reading) => {
        const band = bandByHour.get(hourKey(reading.eventTime)) ?? null
        return {
          ...reading,
          temperature: fahrenheit(reading.temperature),
          brightness: band,
          brightnessBand: band == null ? null : (BAND_BY_NUMBER[band]?.label ?? `Band ${band}`),
        }
      }),
    [readings, bandByHour],
  )

  // The API returns newest first; the chart wants the other way round.
  const chartData = useMemo(() => [...displayReadings].reverse(), [displayReadings])

  const boundsValid = useMemo(() => {
    if (!draft) {
      return false
    }
    const pairs = [
      [draft.humidityLow, draft.humidityHigh, 0, 100],
      [draft.temperatureLow, draft.temperatureHigh, 32, 100],
      [draft.brightnessLow, draft.brightnessHigh, 0, 3000],
    ]
    return pairs.every(
      ([low, high, min, max]) =>
        Number.isFinite(low) && Number.isFinite(high) && low >= min && high <= max && low < high,
    )
  }, [draft])

  const setBound = (key, value) => {
    setDraft((current) => ({
      ...current,
      [key]: value === '' || value === undefined ? null : value,
    }))
  }

  const saveBounds = async () => {
    if (!boundsValid) {
      return
    }
    setSaving(true)
    setError(null)
    try {
      const updated = await api.updateEnvironmentThresholds(draft)
      setBounds(updated)
      setDraft(updated)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  const lightCalibrated = useMemo(() => {
    if (!lightDraft) {
      return false
    }
    const values = LIGHT_BANDS.map((band) => lightDraft[band.key])
    if (values.some((value) => !Number.isFinite(value) || value < 0 || value > 255)) {
      return false
    }
    return values.every((value, index) => index === 0 || value > values[index - 1])
  }, [lightDraft])

  const lightValid = useMemo(
    () =>
      Boolean(lightDraft) &&
      Number.isFinite(lightDraft.cameraNumber) &&
      Number.isFinite(lightDraft.exposureMs) &&
      lightDraft.exposureMs >= 1 &&
      lightDraft.exposureMs <= 150 &&
      lightCalibrated,
    [lightDraft, lightCalibrated],
  )

  // Merge the saved camera into the picker even if its rows have been deleted,
  // and label a stopped camera so the disabled probe buttons make sense.
  const cameraOptions = useMemo(() => {
    const options = cameras.map((camera) => ({
      value: camera.value,
      label: camera.running === false ? `${camera.label} (stopped)` : camera.label,
    }))
    const current = lightDraft?.cameraNumber
    if (current != null && !options.some((option) => option.value === String(current))) {
      options.push({ value: String(current), label: `Camera ${current}` })
    }
    return options
  }, [cameras, lightDraft])

  const selectedCamera = useMemo(
    () => cameras.find((camera) => camera.value === String(lightDraft?.cameraNumber)) ?? null,
    [cameras, lightDraft],
  )

  // Unknown status (the call failed) does not block sampling; a stopped camera
  // does, because the probe could only return a 409.
  const cameraStopped = selectedCamera?.running === false

  const saveLight = async () => {
    if (!lightValid) {
      return
    }
    setSavingLight(true)
    setLightError(null)
    try {
      const payload = {
        cameraNumber: lightDraft.cameraNumber,
        exposureMs: lightDraft.exposureMs,
        enabled: lightDraft.enabled,
        darkLuma: lightDraft.darkLuma,
        veryDimLuma: lightDraft.veryDimLuma,
        dimLuma: lightDraft.dimLuma,
        moderateLuma: lightDraft.moderateLuma,
        brightLuma: lightDraft.brightLuma,
        veryBrightLuma: lightDraft.veryBrightLuma,
      }
      const saved = await api.updateCameraLight(payload)
      setLight(saved)
      setLightDraft(saved)
      // A recalibration drops the old band history; refresh the chart/table.
      loadReadings()
    } catch (err) {
      setLightError(err.message)
    } finally {
      setSavingLight(false)
    }
  }

  const sampleLight = async (key) => {
    setSampling(key)
    setLightError(null)
    try {
      // Calibration captures must not enter the Environment history; the
      // manual "Sample now" does. Pass the draft camera and exposure so a
      // capture probes exactly the values on screen, saved or not.
      const sample = await api.sampleCameraLight(
        key === 'now' || key == null,
        lightDraft?.cameraNumber,
        lightDraft?.exposureMs,
      )
      setLight((current) =>
        current
          ? {
              ...current,
              lastLuma: sample.luma,
              lastBand: sample.band,
              lastLux: sample.lux,
              lastSampleAt: sample.sampledAt,
            }
          : current,
      )
      if (key && key !== 'now') {
        setLightDraft((current) =>
          current
            ? {
                ...current,
                [key]: Number(sample.luma.toFixed(1)),
              }
            : current,
        )
      } else {
        loadReadings()
      }
    } catch (err) {
      setLightError(err.message)
    } finally {
      setSampling(null)
    }
  }

  return (
    <Stack>
      <Title order={2}>Environment</Title>

      <Group justify="space-between" align="center" wrap="wrap">
        <SegmentedControl value={range} onChange={setRange} data={RANGES} />
        <Text size="sm" c="dimmed">
          {readings.length} readings
        </Text>
      </Group>

      {draft && (
        <Card withBorder padding="md">
          <Stack gap="sm">
            <Group justify="space-between" align="center">
              <Title order={4}>Alert bounds</Title>
              <Group gap="sm">
                <Button variant="default" onClick={() => setDraft(bounds)} disabled={saving}>
                  Reset
                </Button>
                <Button onClick={saveBounds} loading={saving} disabled={!boundsValid}>
                  Save bounds
                </Button>
              </Group>
            </Group>

            <Text size="sm" c="dimmed">
              Humidity and temperature outside these bounds are shown in red and raise an
              Environment alert. Brightness is shown as a band from the camera light calibration,
              and its alert compares the band&apos;s representative lux with these bounds.
              Temperature is shown in °F throughout the page.
            </Text>

            <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="md">
              <Stack gap="xs">
                <Text fw={600}>Humidity (%)</Text>
                <Group grow>
                  <NumberInput
                    label="Low"
                    min={0}
                    max={100}
                    value={draft.humidityLow}
                    onChange={(value) => setBound('humidityLow', value)}
                  />
                  <NumberInput
                    label="High"
                    min={0}
                    max={100}
                    value={draft.humidityHigh}
                    onChange={(value) => setBound('humidityHigh', value)}
                  />
                </Group>
              </Stack>

              <Stack gap="xs">
                <Text fw={600}>Temperature (°F)</Text>
                <Group grow>
                  <NumberInput
                    label="Low"
                    min={32}
                    max={100}
                    value={draft.temperatureLow}
                    onChange={(value) => setBound('temperatureLow', value)}
                  />
                  <NumberInput
                    label="High"
                    min={32}
                    max={100}
                    value={draft.temperatureHigh}
                    onChange={(value) => setBound('temperatureHigh', value)}
                  />
                </Group>
              </Stack>

              <Stack gap="xs">
                <Text fw={600}>Brightness (proxy lux)</Text>
                <Group grow>
                  <NumberInput
                    label="Low"
                    min={0}
                    max={3000}
                    allowDecimal={false}
                    value={draft.brightnessLow}
                    onChange={(value) => setBound('brightnessLow', value)}
                  />
                  <NumberInput
                    label="High"
                    min={0}
                    max={3000}
                    allowDecimal={false}
                    value={draft.brightnessHigh}
                    onChange={(value) => setBound('brightnessHigh', value)}
                  />
                </Group>
              </Stack>
            </SimpleGrid>

            {!boundsValid && (
              <Text size="sm" c="red">
                Low must be below high and inside the allowed range.
              </Text>
            )}
          </Stack>
        </Card>
      )}

      <Card withBorder padding="md">
        <Stack gap="sm">
          <Group justify="space-between" align="center">
            <Title order={4}>Camera light</Title>
            <Group gap="sm">
              <Button
                variant="default"
                onClick={() => setLightDraft(light)}
                disabled={savingLight || !light}
              >
                Reset
              </Button>
              <Button onClick={saveLight} loading={savingLight} disabled={!lightValid}>
                Save camera light
              </Button>
            </Group>
          </Group>

          <Text size="sm" c="dimmed">
            Plug in: choose a running camera. Calibrate: put the room in each lighting and capture
            its luma. Play: enable the sensor; a probe runs every minute and raises an Environment
            alert when the band is outside the brightness bounds.
          </Text>

          {!lightDraft && (
            <Center>
              <Loader size="sm" />
            </Center>
          )}

          {lightDraft && (
            <>
              <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="md">
                <Select
                  label="Camera"
                  data={cameraOptions}
                  value={lightDraft.cameraNumber == null ? null : String(lightDraft.cameraNumber)}
                  placeholder="Choose a camera"
                  onChange={(value) =>
                    value &&
                    setLightDraft((current) => ({
                      ...current,
                      cameraNumber: Number(value),
                    }))
                  }
                />
                <NumberInput
                  label="Exposure (ms)"
                  min={1}
                  max={150}
                  allowDecimal={false}
                  value={lightDraft.exposureMs}
                  onChange={(value) =>
                    setLightDraft((current) => ({
                      ...current,
                      exposureMs: value === '' || value === undefined ? null : value,
                    }))
                  }
                />
                <Switch
                  label="Sample every minute"
                  checked={lightDraft.enabled}
                  disabled={!lightCalibrated}
                  onChange={(event) =>
                    setLightDraft((current) => ({
                      ...current,
                      enabled: event.currentTarget.checked,
                    }))
                  }
                />
              </SimpleGrid>

              {cameraStopped && (
                <Alert color="yellow" variant="light">
                  {selectedCamera?.label} is not running. Start it from its Camera page before
                  sampling or capturing a calibration.
                </Alert>
              )}

              {cameraError && (
                <Alert color="red" variant="light">
                  Could not load the cameras: {cameraError}
                </Alert>
              )}

              <Group gap="sm" wrap="wrap">
                <Button
                  variant="light"
                  onClick={() => sampleLight('now')}
                  loading={sampling === 'now'}
                  disabled={sampling !== null || cameraStopped}
                >
                  Sample now
                </Button>
                {light?.lastBand != null ? (
                  <Badge
                    color={BAND_COLORS[BAND_BY_NUMBER[light.lastBand]?.band] ?? 'gray'}
                    variant="light"
                  >
                    {BAND_BY_NUMBER[light.lastBand]?.label ?? `Band ${light.lastBand}`}
                  </Badge>
                ) : (
                  <Badge color="gray" variant="light">
                    No band
                  </Badge>
                )}
                {light?.lastLuma != null && (
                  <Text size="sm">luma {Number(light.lastLuma).toFixed(1)}</Text>
                )}
                {light?.lastLux != null && <Text size="sm">≈ {light.lastLux} lux</Text>}
                {light?.lastSampleAt && (
                  <Text size="sm" c="dimmed">
                    {new Date(light.lastSampleAt).toLocaleString()}
                  </Text>
                )}
              </Group>

              <Table withTableBorder>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Band</Table.Th>
                    <Table.Th>Captured luma</Table.Th>
                    <Table.Th />
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {LIGHT_BANDS.map((band) => (
                    <Table.Tr key={band.key}>
                      <Table.Td>{band.label}</Table.Td>
                      <Table.Td>
                        {lightDraft[band.key] != null
                          ? Number(lightDraft[band.key]).toFixed(1)
                          : '—'}
                      </Table.Td>
                      <Table.Td>
                        <Button
                          size="compact-sm"
                          variant="light"
                          onClick={() => sampleLight(band.key)}
                          loading={sampling === band.key}
                          disabled={sampling !== null || cameraStopped}
                        >
                          Capture
                        </Button>
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>

              {!lightCalibrated && (
                <Text size="sm" c="dimmed">
                  Capture all six bands in increasing light before enabling the sensor. A probe
                  briefly changes the live exposure, so the camera must be running.
                </Text>
              )}
            </>
          )}

          {lightError && (
            <Alert color="red" variant="light">
              {lightError}
            </Alert>
          )}
        </Stack>
      </Card>

      {error && (
        <Alert color="red" variant="light">
          {error}
        </Alert>
      )}

      {loading && (
        <Center>
          <Loader size="sm" />
        </Center>
      )}

      {!loading && readings.length === 0 && <Text c="dimmed">No readings found.</Text>}

      {!loading && readings.length > 0 && (
        <>
          <Card withBorder padding="md">
            <LineChart
              h={320}
              data={chartData}
              dataKey="eventTime"
              series={SERIES}
              curveType="monotone"
              connectNulls={false}
              withDots={false}
              withLegend
              withTooltip
              tooltipAnimationDuration={150}
              tooltipProps={{ content: EnvironmentTooltip }}
              xAxisProps={{ tickFormatter: tickLabel, minTickGap: 48 }}
              yAxisProps={{ width: 48 }}
              withRightYAxis
              rightYAxisLabel="Band"
              rightYAxisProps={{
                width: 90,
                domain: [1, 6],
                ticks: [1, 2, 3, 4, 5, 6],
                tickFormatter: bandTickLabel,
              }}
            />
          </Card>

          <Table.ScrollContainer minWidth={560}>
            <Table striped highlightOnHover withTableBorder>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Time</Table.Th>
                  <Table.Th>Temperature (°F)</Table.Th>
                  <Table.Th>Humidity (%)</Table.Th>
                  <Table.Th>Brightness</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {displayReadings.map((reading) => {
                  const temperatureOut = outside(
                    reading.temperature,
                    bounds?.temperatureLow,
                    bounds?.temperatureHigh,
                  )
                  const humidityOut = outside(
                    reading.humidity,
                    bounds?.humidityLow,
                    bounds?.humidityHigh,
                  )
                  return (
                    <Table.Tr key={reading.eventTime}>
                      <Table.Td>{new Date(reading.eventTime).toLocaleString()}</Table.Td>
                      <Table.Td style={temperatureOut ? OUT_OF_BOUNDS : undefined}>
                        {Number(reading.temperature).toFixed(1)}
                      </Table.Td>
                      <Table.Td style={humidityOut ? OUT_OF_BOUNDS : undefined}>
                        {reading.humidity}
                      </Table.Td>
                      <Table.Td>{reading.brightnessBand ?? '—'}</Table.Td>
                    </Table.Tr>
                  )
                })}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        </>
      )}
    </Stack>
  )
}
