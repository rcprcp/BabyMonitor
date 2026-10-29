import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Accordion,
  Alert,
  Badge,
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
// may raise an Audio notification. Day to day that is one of three presets.
// The full per-class list still exists, but only under Custom: 521 checkboxes
// are a tuning screen, not a setting.
export default function AudioAlertsPage() {
  const [config, setConfig] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [filter, setFilter] = useState('')
  // Open by default only when the saved set is not a preset: the page should
  // never hide which sounds are actually on.
  const [editorOpen, setEditorOpen] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const next = await api.getAudioAlertClasses()
      setConfig(next)
      setEditorOpen(matchPreset(enabledIn(next.classes)) === 'custom')
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
    setEditorOpen(false)
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

  const reset = async () => {
    setError(null)
    setSaving(true)
    try {
      const next = await api.resetAudioAlertClasses()
      setConfig(next)
      setEditorOpen(matchPreset(enabledIn(next.classes)) === 'custom')
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Title order={2}>Audio notifications</Title>
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
        Choose which sounds may raise an Audio notification. A preset replaces the whole set and is
        saved on the monitor straight away, so it survives a reload. Custom keeps a hand-picked
        list.
      </Text>

      {enabledCount === 0 && !loading && (
        <Alert color="yellow" variant="light" title="No sounds are enabled">
          An audio event can never notify you. Choose a preset, or open Custom and enable at least
          one sound.
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
        <SimpleGrid cols={{ base: 1, sm: 2, md: 4 }} spacing="sm">
          {PRESETS.map((preset) => (
            <PresetCard
              key={preset.id}
              preset={preset}
              selected={active === preset.id}
              disabled={saving}
              onClick={() => applyPreset(preset)}
            />
          ))}
          <CustomCard
            open={editorOpen}
            current={active === 'custom'}
            disabled={saving}
            onClick={() => setEditorOpen((open) => !open)}
          />
        </SimpleGrid>
      )}

      {editorOpen && (
        <Stack gap="sm" id="custom-sounds">
          <Group justify="space-between" align="center">
            <Text fw={600}>Custom sounds</Text>
            <Badge color="gray" variant="light">
              {enabledCount} of {classes.length} on
            </Badge>
          </Group>

          <TextInput
            placeholder="Filter sounds…"
            aria-label="Filter sounds"
            value={filter}
            onChange={(event) => setFilter(event.currentTarget.value)}
            disabled={loading}
            w={280}
          />

          <Accordion
            multiple
            defaultValue={groups.length > 0 ? [String(groups[0].id)] : []}
            variant="separated"
          >
            {groups.map((group) => {
              const all = allFor(group)
              const shown = shownFor(group)
              const on = all.filter((candidate) => candidate.enabled).length
              return (
                <Accordion.Item key={group.id} value={String(group.id)}>
                  <Accordion.Control>
                    <Group justify="space-between" pr="md">
                      <Text fw={600}>{group.name}</Text>
                      <Badge color={on > 0 ? 'green' : 'gray'} variant="light">
                        {on} of {all.length} on
                      </Badge>
                    </Group>
                  </Accordion.Control>
                  <Accordion.Panel>
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
                  </Accordion.Panel>
                </Accordion.Item>
              )
            })}
          </Accordion>
        </Stack>
      )}

      <Group justify="space-between" align="center">
        <Text size="xs" c="dimmed">
          Anything outside a preset stays reachable under Custom.
        </Text>
        <Button variant="subtle" size="xs" onClick={reset} loading={saving} disabled={loading}>
          Reset to default
        </Button>
      </Group>
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

function CustomCard({ open, current, disabled, onClick }) {
  return (
    <UnstyledButton
      onClick={onClick}
      disabled={disabled}
      aria-expanded={open}
      aria-controls="custom-sounds"
      style={{ height: '100%' }}
    >
      <Paper
        withBorder
        p="md"
        h="100%"
        style={{
          borderColor: open || current ? 'var(--mantine-primary-color-filled)' : undefined,
          background: open || current ? 'var(--mantine-primary-color-light)' : undefined,
        }}
      >
        <Group justify="space-between" align="flex-start" wrap="nowrap" gap="xs">
          <Text fw={600}>Custom</Text>
          {current && (
            <Badge size="xs" variant="light">
              Current
            </Badge>
          )}
        </Group>
        <Text size="xs" c="dimmed" mt={4}>
          Pick individual sounds, groups or a filter.
        </Text>
        <Text size="xs" c="dimmed" mt={8}>
          {open ? 'Hide the full list' : 'Show the full list'}
        </Text>
      </Paper>
    </UnstyledButton>
  )
}
