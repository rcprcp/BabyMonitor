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
  SimpleGrid,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core'
import { api } from '../api'

// Every YAMNet class is scored on every window; this page only chooses which
// ones may raise an Audio notification. The class list and its six categories
// come from the backend's bundled class map; the switches live in the database.
export default function AudioAlertsPage() {
  const [config, setConfig] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [filter, setFilter] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setConfig(await api.getAudioAlertClasses())
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const classes = config?.classes ?? []
  const groups = config?.groups ?? []
  const enabledCount = classes.filter((c) => c.enabled).length

  const filtered = useMemo(() => {
    const needle = filter.trim().toLowerCase()
    if (!needle) {
      return classes
    }
    return classes.filter((c) => c.name.toLowerCase().includes(needle))
  }, [classes, filter])

  const allFor = (group) => classes.filter((c) => c.groupId === group.id)
  const shownFor = (group) => filtered.filter((c) => c.groupId === group.id)

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
      await api.setAudioAlertClasses(next.filter((candidate) => candidate.enabled).map((c) => c.index))
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
      setConfig(await api.resetAudioAlertClasses())
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Stack>
      <Group justify="space-between" align="center">
        <Title order={2}>Audio Notification Config</Title>
        <Group gap="sm" align="center">
          {saving && (
            <Badge color="blue" variant="light">
              Saving…
            </Badge>
          )}
          <Badge color="gray" variant="light">
            {enabledCount} of {classes.length} on
          </Badge>
          <Button variant="default" onClick={reset} loading={saving} disabled={loading}>
            Reset to default
          </Button>
        </Group>
      </Group>

      <Text c="dimmed" size="sm">
        YAMNet scores every sound below, but only the checked ones may raise an Audio
        notification. The default is everything off except classes <strong>0–61</strong>
        (Speech &amp; Human Sounds plus the human-sound start of Singing &amp; Music Genres).
      </Text>

      <TextInput
        placeholder="Filter sounds…"
        aria-label="Filter sounds"
        value={filter}
        onChange={(event) => setFilter(event.currentTarget.value)}
        disabled={loading}
        w={280}
      />

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
      )}
    </Stack>
  )
}
