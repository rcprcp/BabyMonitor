import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Card,
  Center,
  Group,
  Loader,
  SegmentedControl,
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
  { name: 'temperature', label: 'Temperature (°C)', color: 'orange.6', yAxisId: 'left' },
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

// Ambient readings from the nursery sensor: a time series kept in
// babymonitor.environment and drawn as a chart above the raw rows. The chart
// plots oldest to newest, while the table lists the newest sample first.
export default function EnvironmentPage() {
  const [readings, setReadings] = useState([])
  const [range, setRange] = useState(DEFAULT_RANGE)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
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

  useEffect(() => {
    load()
  }, [load])

  // The API returns newest first; the chart wants the other way round.
  const chartData = useMemo(() => [...readings].reverse(), [readings])

  return (
    <Stack>
      <Title order={2}>Environment</Title>

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
                  <Table.Th>Temperature (°C)</Table.Th>
                  <Table.Th>Humidity (%)</Table.Th>
                  <Table.Th>Brightness (lux)</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {readings.map((reading) => (
                  <Table.Tr key={reading.eventTime}>
                    <Table.Td>{new Date(reading.eventTime).toLocaleString()}</Table.Td>
                    <Table.Td>{reading.temperature}</Table.Td>
                    <Table.Td>{reading.humidity}</Table.Td>
                    <Table.Td>{reading.brightness}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        </>
      )}
    </Stack>
  )
}
