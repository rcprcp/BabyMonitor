import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Center,
  Group,
  Loader,
  Paper,
  SegmentedControl,
  Stack,
  Table,
  Text,
  Title,
} from '@mantine/core'
import { LineChart } from '@mantine/charts'
import { Link as RouterLink } from 'react-router-dom'
import { api } from '../api'
import { BAND_BY_NUMBER } from '../lightBands'

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
// brightness is the camera-light band on its own right-hand axis.
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
// raw rows. Alert bounds and the light-sensor calibration live on
// /environment-config; the bounds are read here only to highlight bad rows.
export default function EnvironmentPage() {
  const [readings, setReadings] = useState([])
  const [bands, setBands] = useState([])
  const [range, setRange] = useState(DEFAULT_RANGE)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [bounds, setBounds] = useState(null)

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

  useEffect(() => {
    loadReadings()
  }, [loadReadings])

  // Read-only here: the controls live on the configuration page.
  useEffect(() => {
    api
      .getEnvironmentThresholds()
      .then(setBounds)
      .catch(() => {})
  }, [])

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

  return (
    <Stack>
      <Group justify="space-between" align="center" wrap="wrap">
        <Title order={2}>Environment</Title>
        <Button component={RouterLink} to="/environment-config" variant="default">
          Environment Configuration
        </Button>
      </Group>

      <Group justify="space-between" align="center" wrap="wrap">
        <SegmentedControl value={range} onChange={setRange} data={RANGES} />
        <Text size="sm" c="dimmed">
          {readings.length} readings
        </Text>
      </Group>

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
