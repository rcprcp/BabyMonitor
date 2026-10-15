import { useCallback, useEffect, useState } from 'react'
import { Alert, Box, Button, Code, Group, Stack, Text, Title } from '@mantine/core'
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
    <Stack>
      <Group justify="space-between" align="center">
        <Title order={2}>Live stream</Title>
        <Button variant="default" onClick={load} loading={loading}>
          Reload stream
        </Button>
      </Group>

      {error && (
        <Alert color="red" variant="light">
          {error}
        </Alert>
      )}

      <Box className="stream-frame">
        {loading ? (
          <Text c="gray.3">Connecting…</Text>
        ) : (
          <StreamPlayer url={url} />
        )}
      </Box>

      {url && (
        <Text size="sm" c="dimmed">
          Source: <Code>{url}</Code>
        </Text>
      )}
    </Stack>
  )
}
