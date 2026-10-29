import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Anchor,
  Avatar,
  Badge,
  Button,
  Card,
  Center,
  Divider,
  Group,
  Loader,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import { useOnboarding } from '../onboarding'
import { notifySettingsChanged } from '../settingsEvents'
import AppearanceSettings from '../components/AppearanceSettings'
import NotificationSound from '../components/NotificationSound'

// The settings the app owns are not hand-editable rows; they get a control
// elsewhere on this page (the setup wizard owns onboardingComplete).
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

function initialOf(name) {
  const trimmed = (name ?? '').trim()
  return trimmed ? trimmed.charAt(0).toUpperCase() : '?'
}

// One Settings page: the monitor's system settings, alert behaviour, the
// account's appearance and sound, and the account itself. The avatar in the
// title bar and More both land here.
export default function SettingsPage() {
  const navigate = useNavigate()
  const { restart } = useOnboarding()
  const { user, logout } = useAuth()

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [system, setSystem] = useState({ name: '', host: '' })
  const [original, setOriginal] = useState({ name: '', host: '' })
  const [touched, setTouched] = useState({ name: false, host: false })
  const [savingSystem, setSavingSystem] = useState(false)
  const [systemSaved, setSystemSaved] = useState(false)

  const [extras, setExtras] = useState([])
  const [extraDrafts, setExtraDrafts] = useState({})
  const [savingTag, setSavingTag] = useState(null)

  const [setupError, setSetupError] = useState(null)
  const [signingOut, setSigningOut] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const list = (await api.listSettings()).filter((row) => !INTERNAL_TAGS.has(row.tag))
      const valueOf = (tag) => list.find((row) => row.tag === tag)?.value ?? ''
      setSystem({ name: valueOf('systemName'), host: valueOf('stream_url') })
      setOriginal({ name: valueOf('systemName'), host: normaliseStreamHost(valueOf('stream_url')) })
      setExtras(list.filter((row) => !SYSTEM_TAGS.includes(row.tag)))
      setExtraDrafts(
        Object.fromEntries(
          list.filter((row) => !SYSTEM_TAGS.includes(row.tag)).map((row) => [row.tag, row.value]),
        ),
      )
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

  const signOut = async () => {
    setSigningOut(true)
    try {
      await logout()
      navigate('/login', { replace: true })
    } finally {
      setSigningOut(false)
    }
  }

  return (
    <Stack>
      <Stack gap={2}>
        <Title order={2}>Settings</Title>
        <Text c="dimmed" size="sm">
          The monitor, its alerts, how it looks, and your account.
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
              <Stack gap={2}>
                <Text fw={600}>System</Text>
                <Text size="sm" c="dimmed">
                  The name in the title bar and the address browsers use for the video streams.
                </Text>
              </Stack>
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
                onChange={(event) =>
                  setSystem((current) => ({ ...current, name: event.currentTarget.value }))
                }
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
                onChange={(event) =>
                  setSystem((current) => ({ ...current, host: event.currentTarget.value }))
                }
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

          <Card withBorder padding="lg">
            <Stack gap="md">
              <Stack gap={2}>
                <Text fw={600}>Alerts</Text>
                <Text size="sm" c="dimmed">
                  What gets your attention, and what it sounds like.
                </Text>
              </Stack>
              <NotificationSound />
              <Divider />
              <Group justify="space-between" align="center" wrap="nowrap">
                <Stack gap={2}>
                  <Text fw={500}>Audio notifications</Text>
                  <Text size="sm" c="dimmed">
                    Which sounds may raise an alert.
                  </Text>
                </Stack>
                <Anchor component={RouterLink} to="/audio-alerts">
                  Open
                </Anchor>
              </Group>
              <Group justify="space-between" align="center" wrap="nowrap">
                <Stack gap={2}>
                  <Text fw={500}>Environment</Text>
                  <Text size="sm" c="dimmed">
                    Temperature, humidity and brightness alert bounds.
                  </Text>
                </Stack>
                <Anchor component={RouterLink} to="/environment">
                  Open
                </Anchor>
              </Group>
            </Stack>
          </Card>

          <Card withBorder padding="lg">
            <Stack gap="md">
              <Stack gap={2}>
                <Text fw={600}>Appearance</Text>
                <Text size="sm" c="dimmed">
                  Stored with your account, so it follows you to any browser.
                </Text>
              </Stack>
              <AppearanceSettings />
            </Stack>
          </Card>

          <Card withBorder padding="lg">
            <Group justify="space-between" align="center" wrap="nowrap">
              <Group wrap="nowrap">
                <Avatar size={48} radius="xl" variant="light" fw={600}>
                  {initialOf(user)}
                </Avatar>
                <Stack gap={0}>
                  <Text fw={600}>{user || 'Signed in'}</Text>
                  <Text size="sm" c="dimmed">
                    Your account
                  </Text>
                </Stack>
              </Group>
              <Button variant="default" color="red" onClick={signOut} loading={signingOut}>
                Sign out
              </Button>
            </Group>
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
