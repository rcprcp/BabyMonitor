import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Center,
  Divider,
  Group,
  Loader,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useOnboarding } from '../onboarding'
import { notifySettingsChanged } from '../settingsEvents'

// Application settings. Every row in babymonitor.settings is editable;
// systemName titles the bar.
//
// The table stores raw tags. The page shows a readable name and keeps the tag
// beside it: support and the API still speak tags, but a parent should not have
// to read one. A tag with no entry here falls back to the tag itself.
const SETTING_LABELS = {
  systemName: 'Monitor name',
  stream_url: 'Stream address',
}

// Settings the app owns are not hand-editable rows; they get a control below
// instead of a text field (the setup wizard owns onboardingComplete).
const INTERNAL_TAGS = new Set(['onboardingComplete'])

function settingLabel(tag) {
  return SETTING_LABELS[tag] ?? tag
}

export default function SettingsPage() {
  const navigate = useNavigate()
  const { restart } = useOnboarding()
  const [settings, setSettings] = useState([])
  const [drafts, setDrafts] = useState({})
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)
  const [savingTag, setSavingTag] = useState(null)
  const [savedTag, setSavedTag] = useState(null)
  const [setupError, setSetupError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const list = (await api.listSettings()).filter((row) => !INTERNAL_TAGS.has(row.tag))
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
      notifySettingsChanged()
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
              label={
                <>
                  {settingLabel(setting.tag)}
                  {settingLabel(setting.tag) !== setting.tag && (
                    <Text component="span" size="xs" c="dimmed" ml={6}>
                      {setting.tag}
                    </Text>
                  )}
                </>
              }
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

      <Divider />

      <Group justify="space-between" align="center" wrap="nowrap">
        <Stack gap={2}>
          <Text fw={600}>Setup wizard</Text>
          <Text size="sm" c="dimmed">
            Walk through the monitor name, camera devices and a stream test again.
          </Text>
        </Stack>
        <Button
          variant="default"
          onClick={async () => {
            setSetupError(null)
            try {
              await restart()
              navigate('/welcome')
            } catch (err) {
              setSetupError(err.message)
            }
          }}
        >
          Run setup again
        </Button>
      </Group>
      {setupError && (
        <Alert color="red" variant="light">
          {setupError}
        </Alert>
      )}
    </Stack>
  )
}
