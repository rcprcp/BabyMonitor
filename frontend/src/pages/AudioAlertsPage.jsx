import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Badge,
  Box,
  Button,
  Center,
  Checkbox,
  Group,
  Loader,
  Paper,
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  Title,
  UnstyledButton,
} from '@mantine/core'
import { api } from '../api'
import { PRESETS, matchPreset } from '../audioPresets'

// Every YAMNet class is scored on every window; this page chooses which ones
// may raise an Audio notification. A preset is the starting point, and the six
// sound sections underneath stay available (closed) so a preset can be tuned.
export default function AudioAlertsPage() {
  const [config, setConfig] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [filter, setFilter] = useState('')
  // The preset the user most recently applied. "Set to Default" restores this,
  // not the shipped default, so tune -> reset works for whichever preset was
  // chosen. Null when a saved set matched no preset and none was chosen yet.
  const [lastPresetId, setLastPresetId] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const next = await api.getAudioAlertClasses()
      setConfig(next)
      const matched = matchPreset(enabledIn(next.classes))
      setLastPresetId(matched === 'custom' ? null : matched)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const classes = useMemo(() => config?.classes ?? [], [config])
  const groups = useMemo(() => config?.groups ?? [], [config])
  const enabledCount = classes.filter((c) => c.enabled).length
  const enabledIndices = useMemo(() => enabledIn(classes), [classes])
  const active = matchPreset(enabledIndices)
  // With no remembered preset (a custom set from an older page load) reset
  // falls back to the shipped default rather than doing nothing.
  const restorePreset =
    PRESETS.find((preset) => preset.id === lastPresetId) ??
    PRESETS.find((preset) => preset.isDefault)

  const filtered = useMemo(() => {
    const needle = filter.trim().toLowerCase()
    if (!needle) {
      return classes
    }
    return classes.filter((c) => c.name.toLowerCase().includes(needle))
  }, [classes, filter])

  const allFor = (group) => classes.filter((c) => c.groupId === group.id)
  const shownFor = (group) => filtered.filter((c) => c.groupId === group.id)

  // A preset replaces the whole set in one call, the same endpoint the group
  // macros use.
  const applyPreset = async (preset) => {
    setError(null)
    setSaving(true)
    const wanted = new Set(preset.indices)
    setConfig((current) => ({
      ...current,
      classes: current.classes.map((candidate) => ({
        ...candidate,
        enabled: wanted.has(candidate.index),
      })),
    }))
    try {
      await api.setAudioAlertClasses(preset.indices)
      setLastPresetId(preset.id)
    } catch (err) {
      setError(err.message)
      await load()
    } finally {
      setSaving(false)
    }
  }

  // A single checkbox saves just that class; a group macro saves the whole set.
  const toggle = async (cls, enabled) => {
    setError(null)
    setSaving(true)
    setConfig((current) => ({
      ...current,
      classes: current.classes.map((candidate) =>
        candidate.index === cls.index ? { ...candidate, enabled } : candidate,
      ),
    }))
    try {
      await api.setAudioAlertClass(cls.index, enabled)
    } catch (err) {
      setError(err.message)
      await load()
    } finally {
      setSaving(false)
    }
  }

  const setGroup = async (group, enabled) => {
    setError(null)
    setSaving(true)
    const next = classes.map((candidate) =>
      candidate.groupId === group.id ? { ...candidate, enabled } : candidate,
    )
    setConfig((current) => ({ ...current, classes: next }))
    try {
      await api.setAudioAlertClasses(
        next.filter((candidate) => candidate.enabled).map((c) => c.index),
      )
    } catch (err) {
      setError(err.message)
      await load()
    } finally {
      setSaving(false)
    }
  }

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Title order={2}>Audio Configuration</Title>
        <Group gap="sm" align="center">
          {saving && (
            <Badge color="blue" variant="light">
              Saving…
            </Badge>
          )}
          <Badge color="gray" variant="light">
            {enabledCount} of {classes.length} on
          </Badge>
        </Group>
      </Group>

      <Text c="dimmed" size="sm">
        Start from a preset, then open a sound section below to tailor it. Set to Default puts the
        preset you chose back exactly as it was.
      </Text>

      {enabledCount === 0 && !loading && (
        <Alert color="yellow" variant="light" title="No sounds are enabled">
          An audio event can never notify you. Choose a preset, or enable at least one sound below.
        </Alert>
      )}

      {error && (
        <Alert color="red" variant="light">
          {error}
        </Alert>
      )}

      {loading ? (
        <Center>
          <Loader size="sm" />
        </Center>
      ) : (
        <>
          <SimpleGrid cols={{ base: 1, sm: 3 }} spacing="sm">
            {PRESETS.map((preset) => (
              <PresetCard
                key={preset.id}
                preset={preset}
                selected={active === preset.id}
                disabled={saving}
                onClick={() => applyPreset(preset)}
              />
            ))}
          </SimpleGrid>

          <Stack gap="sm">
            <Group justify="space-between" align="center">
              <Group gap="sm" align="center">
                <Text fw={600}>Sounds</Text>
                {active === 'custom' && (
                  <Badge color="yellow" variant="light">
                    Custom
                  </Badge>
                )}
              </Group>
              <Group gap="sm" align="center">
                <Text size="xs" c="dimmed">
                  Restores {restorePreset.label}
                </Text>
                <Button
                  size="xs"
                  variant="default"
                  onClick={() => applyPreset(restorePreset)}
                  disabled={saving}
                >
                  Set to Default
                </Button>
              </Group>
            </Group>

            <TextInput
              placeholder="Filter sounds…"
              aria-label="Filter sounds"
              value={filter}
              onChange={(event) => setFilter(event.currentTarget.value)}
              disabled={loading}
              w={280}
            />

            {groups.map((group) => {
              const all = allFor(group)
              const shown = shownFor(group)
              const on = all.filter((candidate) => candidate.enabled).length
              return (
                <Paper key={group.id} withBorder component="details">
                  <Box component="summary" p="md" style={{ cursor: 'pointer', listStyle: 'none' }}>
                    <Group justify="space-between" pr="md">
                      <Text fw={600}>{group.name}</Text>
                      <Badge color={on > 0 ? 'green' : 'gray'} variant="light">
                        {on} of {all.length} on
                      </Badge>
                    </Group>
                  </Box>
                  <Box px="md" pb="md">
                    <Stack gap="sm">
                      <Group gap="sm">
                        <Button
                          size="xs"
                          variant="default"
                          onClick={() => setGroup(group, true)}
                          disabled={saving}
                        >
                          Enable all
                        </Button>
                        <Button
                          size="xs"
                          variant="default"
                          onClick={() => setGroup(group, false)}
                          disabled={saving}
                        >
                          Disable all
                        </Button>
                      </Group>

                      {shown.length === 0 ? (
                        <Text c="dimmed" size="sm">
                          No sounds match the filter.
                        </Text>
                      ) : (
                        <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }} spacing="xs">
                          {shown.map((cls) => (
                            <Checkbox
                              key={cls.index}
                              label={`${cls.index} · ${cls.name}`}
                              checked={cls.enabled}
                              onChange={(event) => toggle(cls, event.currentTarget.checked)}
                              disabled={saving}
                            />
                          ))}
                        </SimpleGrid>
                      )}
                    </Stack>
                  </Box>
                </Paper>
              )
            })}
          </Stack>
        </>
      )}
    </Stack>
  )
}

function enabledIn(classes) {
  return classes.filter((cls) => cls.enabled).map((cls) => cls.index)
}

function PresetCard({ preset, selected, disabled, onClick }) {
  return (
    <UnstyledButton
      onClick={onClick}
      disabled={disabled}
      aria-pressed={selected}
      style={{ height: '100%' }}
    >
      <Paper
        withBorder
        p="md"
        h="100%"
        style={{
          borderColor: selected ? 'var(--mantine-primary-color-filled)' : undefined,
          background: selected ? 'var(--mantine-primary-color-light)' : undefined,
        }}
      >
        <Group justify="space-between" align="flex-start" wrap="nowrap" gap="xs">
          <Text fw={600}>{preset.label}</Text>
          {preset.isDefault && (
            <Badge size="xs" variant="light">
              Default
            </Badge>
          )}
        </Group>
        <Text size="xs" c="dimmed" mt={4}>
          {preset.summary}
        </Text>
        <Text size="xs" c="dimmed" mt={8}>
          {preset.indices.length} sounds
        </Text>
      </Paper>
    </UnstyledButton>
  )
}
