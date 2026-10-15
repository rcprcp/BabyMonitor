import { Box, Stack, Title } from '@mantine/core'
import StreamPlayer from '../components/StreamPlayer'

// The landing page. The stream URL used to come from the streaming_url setting;
// that setting is gone, and each camera's stream_url parameter (on the Camera
// pages) is the source of truth now. Nothing is wired up here yet, so this page
// shows the player's "no stream URL" placeholder until it is pointed at a
// camera.
export default function StreamPage() {
  return (
    <Stack>
      <Title order={2}>Live stream</Title>

      <Box className="stream-frame">
        <StreamPlayer />
      </Box>
    </Stack>
  )
}
