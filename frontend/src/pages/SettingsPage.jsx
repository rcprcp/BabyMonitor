import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Center,
  Group,
  Loader,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core'
import { api } from '../api'

// Application settings. Every row in babymonitor.settings is editable;
// systemName titles the bar.
export default function SettingsPage() {
  const [settings, setSettings] = useState([])
  const [drafts, setDrafts] = useState({})
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [savingTag, setSavingTag] = useState(null)
  const [savedTag, setSavedTag] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const list = await api.listSettings()
      setSettings(list)
      setDrafts(Object.fromEntries(list.map((s) => [s.tag, s.value])))
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const save = async (tag) => {
    setError(null)
    setSavingTag(tag)
    setSavedTag(null)
    try {
      const updated = await api.updateSetting(tag, drafts[tag])
      setSettings((current) => current.map((s) => (s.tag === tag ? updated : s)))
      setSavedTag(tag)
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingTag(null)
    }
  }

  return (
    <Stack>
      <Title order={2}>Settings</Title>

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

      {!loading && settings.length === 0 && <Text c="dimmed">No settings found.</Text>}

      <Stack gap="md">
        {settings.map((setting) => (
          <Group key={setting.tag} align="flex-end" gap="sm" wrap="nowrap">
            <TextInput
              id={`setting-${setting.tag}`}
              label={setting.tag}
              style={{ flex: 1 }}
              value={drafts[setting.tag] ?? ''}
              onChange={(event) => {
                // Read the value before calling setDrafts: the updater runs
                // later, during the render phase, by which time React has reset
                // the synthetic event's currentTarget (and reading it throws).
                const { value } = event.currentTarget
                setDrafts((current) => ({ ...current, [setting.tag]: value }))
              }}
            />
            <Button
              onClick={() => save(setting.tag)}
              loading={savingTag === setting.tag}
              disabled={drafts[setting.tag] === setting.value}
            >
              Save
            </Button>
            {savedTag === setting.tag && (
              <Badge color="green" variant="light" mb={6}>
                Saved
              </Badge>
            )}
          </Group>
        ))}
      </Stack>
    </Stack>
  )
}
