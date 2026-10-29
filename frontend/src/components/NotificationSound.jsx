import { useState } from 'react'
import { Alert, Badge, Box, Button, Group, Popover, ScrollArea, Stack, Text } from '@mantine/core'
import { PROMPT_SOUNDS, useAuth } from '../auth'

/** Plays the selected sound once so the user can audition it. */
function previewSound(name) {
  const audio = new Audio(`/sounds/${name}.mp3`)
  audio.play().catch(() => {})
}

// The alert tone is stored per account (users.prompt_sound) and follows the
// user across browsers. Picking a sound writes it immediately, so the button
// label is also the saved state.
export default function NotificationSound() {
  const { promptSound, savePromptSound } = useAuth()
  const [saved, setSaved] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const change = async (next) => {
    setError(null)
    setSaved(false)
    setSaving(true)
    try {
      await savePromptSound(next)
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Stack gap="xs" align="flex-start">
      <Text fw={600}>Notification sound</Text>
      <Text c="dimmed" size="sm">
        Select the tone played when an alert arrives. Saved on your account, so it follows you
        across browsers.
      </Text>
      <Popover position="bottom" withArrow p={0} width={320}>
        <Popover.Target>
          <Box w="100%">
            <Button variant="outline" fullWidth radius="md">
              {PROMPT_SOUNDS.find((sound) => sound.id === promptSound)?.label ?? 'Select a sound'}
            </Button>
          </Box>
        </Popover.Target>
        <Popover.Dropdown mah={300} style={{ overflow: 'hidden' }}>
          <ScrollArea.Autosize mah={300}>
            {PROMPT_SOUNDS.map((sound) => (
              <Group
                key={sound.id}
                gap="sm"
                justify="space-between"
                wrap="nowrap"
                px="xs"
                py="xs"
                radius="md"
                bg={sound.id === promptSound ? 'var(--mantine-primary-color-light)' : undefined}
                onClick={() => {
                  previewSound(sound.prefix)
                  change(sound.id)
                }}
                style={{ cursor: saving ? 'wait' : 'pointer', userSelect: 'none' }}
              >
                <Group gap="xs" wrap="nowrap">
                  <Button
                    variant="light"
                    size="xs"
                    px={8}
                    fw={600}
                    c="blue"
                    radius="md"
                    onClick={(event) => {
                      event.stopPropagation()
                      previewSound(sound.prefix)
                    }}
                  >
                    Play ▶
                  </Button>{' '}
                  <Text>{sound.label}</Text>
                </Group>
              </Group>
            ))}
          </ScrollArea.Autosize>
        </Popover.Dropdown>
      </Popover>
      {saved && (
        <Badge color="green" variant="light">
          Saved
        </Badge>
      )}
      {error && (
        <Alert color="red" variant="light">
          {error}
        </Alert>
      )}
    </Stack>
  )
}
