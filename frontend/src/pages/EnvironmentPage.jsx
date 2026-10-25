import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Center,
  Group,
  Loader,
  NumberInput,
  SegmentedControl,
  SimpleGrid,
  Stack,
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
  { name: 'brightness', label: 'Brightness (lux)', color: 'yellow.6', yAxisId: 'right' },
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

// Ambient readings from the nursery sensor: a time series kept in
// babymonitor.environment and drawn as a chart above the raw rows. The chart
// plots oldest to newest, while the table lists the newest sample first.
export default function EnvironmentPage() {
  const [readings, setReadings] = useState([])
  const [range, setRange] = useState(DEFAULT_RANGE)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [bounds, setBounds] = useState(null)
  const [draft, setDraft] = useState(null)
  const [saving, setSaving] = useState(false)

  const loadReadings = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setReadings(await api.listEnvironment(Number(range)))
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

  useEffect(() => {
    loadReadings()
  }, [loadReadings])

  useEffect(() => {
    loadThresholds()
  }, [loadThresholds])

  // The API and database keep Celsius; the page presents Fahrenheit.
  const displayReadings = useMemo(
    () =>
      readings.map((reading) => ({
        ...reading,
        temperature: fahrenheit(reading.temperature),
      })),
    [readings],
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
        Number.isFinite(low) &&
        Number.isFinite(high) &&
        low >= min &&
        high <= max &&
        low < high,
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
                <Button
                  variant="default"
                  onClick={() => setDraft(bounds)}
                  disabled={saving}
                >
                  Reset
                </Button>
                <Button onClick={saveBounds} loading={saving} disabled={!boundsValid}>
                  Save bounds
                </Button>
              </Group>
            </Group>

            <Text size="sm" c="dimmed">
              Values outside these bounds are shown in red and raise an Environment alert.
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
                <Text fw={600}>Brightness (lux)</Text>
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
              withDots={false}
              withLegend
              withTooltip
              tooltipAnimationDuration={150}
              xAxisProps={{ tickFormatter: tickLabel, minTickGap: 48 }}
              yAxisProps={{ width: 48 }}
              withRightYAxis
              rightYAxisLabel="lux"
              rightYAxisProps={{ width: 56 }}
            />
          </Card>

          <Table.ScrollContainer minWidth={560}>
            <Table striped highlightOnHover withTableBorder>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Time</Table.Th>
                  <Table.Th>Temperature (°F)</Table.Th>
                  <Table.Th>Humidity (%)</Table.Th>
                  <Table.Th>Brightness (lux)</Table.Th>
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
                  const brightnessOut = outside(
                    reading.brightness,
                    bounds?.brightnessLow,
                    bounds?.brightnessHigh,
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
                      <Table.Td style={brightnessOut ? OUT_OF_BOUNDS : undefined}>
                        {reading.brightness}
                      </Table.Td>
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
