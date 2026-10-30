import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Card,
  Center,
  Group,
  Loader,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { notifySettingsChanged } from '../settingsEvents'
import { useOnboarding } from '../onboarding'

// Settings the app owns are not hand-editable rows; they get a control
// elsewhere (the setup wizard owns onboardingComplete).
const INTERNAL_TAGS = new Set(['onboardingComplete'])

// Settings with a dedicated control. Anything else the table holds still gets
// an editor at the bottom, so a future tag is never unreachable.
const SYSTEM_TAGS = ['systemName', 'stream_url']

/** Trim and drop trailing slashes: StreamPage treats the value as a host base. */
export function normaliseStreamHost(value) {
  return (value ?? '').trim().replace(/\/+$/, '')
}

/**
 * A stream host is the base the *browser* uses, without a port or path: each
 * camera's stream_port is appended by the Live stream page. Returns the error
 * text, or null when the value is usable.
 */
export function streamHostError(value) {
  const host = normaliseStreamHost(value)
  if (!host) {
    return 'Enter the address browsers use to reach this monitor.'
  }
  let url
  try {
    url = new URL(host)
  } catch {
    return 'Enter a full address starting with http:// or https://.'
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    return 'Use http:// or https://.'
  }
  if (url.username || url.password) {
    return 'Leave credentials out of the address.'
  }
  if (url.port) {
    return 'Leave the port out; each camera has its own stream port.'
  }
  if (url.pathname !== '/' || url.search || url.hash) {
    return 'Use only the host, with no path or query.'
  }
  return null
}

// System-wide settings: the name in the title bar and the address browsers use
// for the video streams. Kept apart from the account's alert/appearance
// preferences so the installer surface has its own page.
export default function SystemSettingsPage() {
  const navigate = useNavigate()
  const { restart } = useOnboarding()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [setupError, setSetupError] = useState(null)
  const [system, setSystem] = useState({ name: '', host: '' })
  const [original, setOriginal] = useState({ name: '', host: '' })
  const [touched, setTouched] = useState({ name: false, host: false })
  const [savingSystem, setSavingSystem] = useState(false)
  const [systemSaved, setSystemSaved] = useState(false)

  const [extras, setExtras] = useState([])
  const [extraDrafts, setExtraDrafts] = useState({})
  const [savingTag, setSavingTag] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const list = (await api.listSettings()).filter((row) => !INTERNAL_TAGS.has(row.tag))
      const valueOf = (tag) => list.find((row) => row.tag === tag)?.value ?? ''
      setSystem({ name: valueOf('systemName'), host: valueOf('stream_url') })
      setOriginal({ name: valueOf('systemName'), host: normaliseStreamHost(valueOf('stream_url')) })
      const other = list.filter((row) => !SYSTEM_TAGS.includes(row.tag))
      setExtras(other)
      setExtraDrafts(Object.fromEntries(other.map((row) => [row.tag, row.value])))
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const nameError = touched.name && !system.name.trim() ? 'Give the monitor a name.' : null
  const hostError = touched.host ? streamHostError(system.host) : null
  const changed =
    system.name.trim() !== original.name || normaliseStreamHost(system.host) !== original.host
  const invalid = !system.name.trim() || Boolean(streamHostError(system.host))

  const saveSystem = async () => {
    setTouched({ name: true, host: true })
    setError(null)
    if (invalid) {
      return
    }
    const name = system.name.trim()
    const host = normaliseStreamHost(system.host)
    const changes = []
    if (name !== original.name) {
      changes.push(['systemName', name])
    }
    if (host !== original.host) {
      changes.push(['stream_url', host])
    }
    if (changes.length === 0) {
      setSystemSaved(true)
      setTimeout(() => setSystemSaved(false), 3000)
      return
    }
    setSavingSystem(true)
    try {
      for (const [tag, value] of changes) {
        await api.updateSetting(tag, value)
      }
      setSystem({ name, host })
      setOriginal({ name, host })
      notifySettingsChanged()
      setSystemSaved(true)
      setTimeout(() => setSystemSaved(false), 3000)
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingSystem(false)
    }
  }

  const saveExtra = async (tag) => {
    setError(null)
    setSavingTag(tag)
    try {
      const updated = await api.updateSetting(tag, extraDrafts[tag])
      setExtras((current) => current.map((row) => (row.tag === tag ? updated : row)))
      notifySettingsChanged()
    } catch (err) {
      setError(err.message)
    } finally {
      setSavingTag(null)
    }
  }

  const runSetup = async () => {
    setSetupError(null)
    try {
      await restart()
      navigate('/welcome')
    } catch (err) {
      setSetupError(err.message)
    }
  }

  return (
    <Stack>
      <Stack gap={2}>
        <Title order={2}>System Settings</Title>
        <Text c="dimmed" size="sm">
          The monitor's name and the address browsers use for its video streams.
        </Text>
      </Stack>

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
          <Card withBorder padding="lg">
            <Stack gap="md">
              <TextInput
                label={
                  <>
                    Monitor name
                    <Text component="span" size="xs" c="dimmed" ml={6}>
                      systemName
                    </Text>
                  </>
                }
                description="Shown in the title bar and the browser tab."
                value={system.name}
                onChange={(event) => {
                  // Capture before setState: the updater can run after React
                  // clears currentTarget.
                  const { value } = event.currentTarget
                  setSystem((current) => ({ ...current, name: value }))
                }}
                onBlur={() => setTouched((current) => ({ ...current, name: true }))}
                error={nameError}
                maxLength={120}
              />
              <TextInput
                label={
                  <>
                    Stream address
                    <Text component="span" size="xs" c="dimmed" ml={6}>
                      stream_url
                    </Text>
                  </>
                }
                description="The address phones and laptops use to reach this monitor, for example http://192.168.1.20. No port or path."
                placeholder="http://192.168.1.20"
                value={system.host}
                onChange={(event) => {
                  const { value } = event.currentTarget
                  setSystem((current) => ({ ...current, host: value }))
                }}
                onBlur={() => setTouched((current) => ({ ...current, host: true }))}
                error={hostError}
              />
              <Group justify="flex-end" gap="sm">
                {systemSaved && (
                  <Badge color="green" variant="light">
                    Saved
                  </Badge>
                )}
                <Button onClick={saveSystem} loading={savingSystem} disabled={!changed || invalid}>
                  Save changes
                </Button>
              </Group>
            </Stack>
          </Card>

          {extras.length > 0 && (
            <Card withBorder padding="lg">
              <Stack gap="md">
                <Stack gap={2}>
                  <Text fw={600}>Other settings</Text>
                  <Text size="sm" c="dimmed">
                    Stored as raw tags. Leave them alone unless support asks you to change one.
                  </Text>
                </Stack>
                {extras.map((setting) => (
                  <Group key={setting.tag} align="flex-end" gap="sm" wrap="nowrap">
                    <TextInput
                      id={`setting-${setting.tag}`}
                      label={setting.tag}
                      style={{ flex: 1 }}
                      value={extraDrafts[setting.tag] ?? ''}
                      onChange={(event) => {
                        // Read the value before setState: the synthetic event is
                        // reset by the time the updater runs.
                        const { value } = event.currentTarget
                        setExtraDrafts((current) => ({ ...current, [setting.tag]: value }))
                      }}
                    />
                    <Button
                      onClick={() => saveExtra(setting.tag)}
                      loading={savingTag === setting.tag}
                      disabled={extraDrafts[setting.tag] === setting.value}
                    >
                      Save
                    </Button>
                  </Group>
                ))}
              </Stack>
            </Card>
          )}

          <Card withBorder padding="lg">
            <Group justify="space-between" align="center" wrap="nowrap">
              <Stack gap={2}>
                <Text fw={600}>Setup wizard</Text>
                <Text size="sm" c="dimmed">
                  Walk through the monitor name, camera devices and a stream test again.
                </Text>
              </Stack>
              <Button variant="default" onClick={runSetup}>
                Run setup again
              </Button>
            </Group>
            {setupError && (
              <Alert color="red" variant="light" mt="sm">
                {setupError}
              </Alert>
            )}
          </Card>
        </>
      )}
    </Stack>
  )
}
