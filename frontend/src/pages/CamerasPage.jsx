import {
  Alert,
  Center,
  Group,
  Loader,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  Title,
  UnstyledButton,
} from '@mantine/core'
import { Link as RouterLink } from 'react-router-dom'
import { cameraLabel, useCameras } from '../useCameras'

// The Camera Configuration destination: a list of cameras by name. Each card
// opens that camera's page, which is where its name, device, stream port and
// Motion options live.
export default function CamerasPage() {
  const { cameras, loading, error } = useCameras()

  return (
    <Stack>
      <Title order={2}>Camera Configuration</Title>
      <Text c="dimmed" size="sm">
        Each camera has its own page for its name, device, stream port and recording options.
      </Text>

      {error && (
        <Alert color="red" variant="light">
          {error}
        </Alert>
      )}

      {loading ? (
        <Center>
          <Loader size="sm" />
        </Center>
      ) : cameras.length === 0 ? (
        <Text c="dimmed">No cameras found.</Text>
      ) : (
        <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="md">
          {cameras.map((camera) => (
            <UnstyledButton
              key={camera.cameraNumber}
              component={RouterLink}
              to={`/cameras/${camera.cameraNumber}`}
            >
              <Paper withBorder p="lg" h="100%">
                <Group justify="space-between" align="center" wrap="nowrap">
                  <Stack gap={2}>
                    <Text fw={600} size="lg">
                      {cameraLabel(camera)}
                    </Text>
                    <Text size="sm" c="dimmed">
                      {camera.name?.trim()
                        ? `Camera ${camera.cameraNumber}`
                        : 'No name set on this camera'}
                    </Text>
                  </Stack>
                  <Text size="sm" c="dimmed">
                    Open →
                  </Text>
                </Group>
              </Paper>
            </UnstyledButton>
          ))}
        </SimpleGrid>
      )}
    </Stack>
  )
}
