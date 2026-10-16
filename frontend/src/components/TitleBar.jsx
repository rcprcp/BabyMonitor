import { useEffect, useRef, useState } from 'react'
import { Avatar, Burger, Button, Group, Menu, Title } from '@mantine/core'
import { NavLink, useNavigate } from 'react-router-dom'
import { api } from '../api'
import { playAlertSound, unlockAudio } from '../alerting'
import { useAuth } from '../auth'

// Shown until the systemName setting has been read (and if it is missing).
const DEFAULT_TITLE = 'BabyMonitor'

// First letter of the user's name, shown in the avatar (falls back to '?').
function initialOf(name) {
  const trimmed = (name ?? '').trim()
  return trimmed ? trimmed.charAt(0).toUpperCase() : '?'
}

// The blue title bar: a hamburger menu on the left, the system name in the
// middle and the user's initial on the right. Mantine's Menu handles closing on
// navigation, outside click and Escape.
export default function TitleBar() {
  const [opened, setOpened] = useState(false)
  const [systemName, setSystemName] = useState(DEFAULT_TITLE)
  // Feedback for the Test alert button: idle -> sending -> sent / error -> idle.
  const [alertState, setAlertState] = useState('idle')
  const resetAlert = useRef(null)
  const { user, logout, promptSound } = useAuth()
  const navigate = useNavigate()

  // The title comes from the systemName setting, so the system can be renamed
  // from the Settings page. The default is kept if the tag is missing.
  useEffect(() => {
    let cancelled = false
    api
      .getSetting('systemName')
      .then((setting) => {
        if (cancelled || !setting?.value) {
          return
        }
        setSystemName(setting.value)
        document.title = setting.value
      })
      .catch(() => {
        // Keep the default title if the setting cannot be read.
      })
    return () => {
      cancelled = true
    }
  }, [])

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  // A testing convenience: fire a real audio alert through the webhook so the
  // whole path (stream push, chime, in-app notification) can be exercised from
  // any page. The audio element is primed inside this click's gesture.
  const sendTestAlert = async () => {
    clearTimeout(resetAlert.current)
    setAlertState('sending')
    try {
      unlockAudio()
      await api.sendTestAlert(`Test alert at ${new Date().toLocaleTimeString()}`)
      setAlertState('sent')
    } catch {
      setAlertState('error')
    }
    resetAlert.current = setTimeout(() => setAlertState('idle'), 3000)
  }

  // Play a tone immediately inside the user gesture (so browsers allow autoplay).
  const soundPrefixFor = key => {
    switch (key) {
      case 'alarm-loud':   return 'alarm-loud'
      case 'doorbell':     return 'doorbell-ding-dong'
      case 'beep':         return 'beep-short'
      case 'chime':        return 'notification-chime'
      default:             return key ?? 'chime'
    }
  }

  const playDirectSound = () => {
    unlockAudio()
    playAlertSound(soundPrefixFor(promptSound))
  }

  useEffect(() => () => clearTimeout(resetAlert.current), [])

  const alertLabel =
    alertState === 'sent' ? 'Alert sent' : alertState === 'error' ? 'Alert failed' : 'Test alert'

  return (
    <Group h="100%" px="md" justify="space-between" wrap="nowrap">
      <Menu opened={opened} onChange={setOpened} shadow="md" width={230} position="bottom-start">
        <Menu.Target>
          <Burger
            opened={opened}
            color="var(--mantine-primary-color-contrast)"
            size="sm"
            aria-label="Open the menu"
            aria-expanded={opened}
          />
        </Menu.Target>

        <Menu.Dropdown>
          <Menu.Item component={NavLink} to="/" end>
            Live stream
          </Menu.Item>
          <Menu.Item component={NavLink} to="/notifications">
            Notifications
          </Menu.Item>
          <Menu.Item component={NavLink} to="/environment">
            Environment
          </Menu.Item>
          <Menu.Item component={NavLink} to="/cameras/1">
            Camera1
          </Menu.Item>
          <Menu.Item component={NavLink} to="/cameras/2">
            Camera2
          </Menu.Item>
          <Menu.Item component={NavLink} to="/settings">
            Settings
          </Menu.Item>
          <Menu.Item component={NavLink} to="/profile">
            Personal settings
          </Menu.Item>
          <Menu.Divider />
          <Menu.Item color="red" onClick={handleLogout}>
            Sign out{user ? ` (${user})` : ''}
          </Menu.Item>
        </Menu.Dropdown>
      </Menu>

      <Title order={1} fz="1.35rem" fw={600} className="bm-title">
        {systemName}
      </Title>

      <Group gap="sm" wrap="nowrap">
        <Button
          variant="transparent"
          size="compact-sm"
          className="bm-test-alert"
          loading={alertState === 'sending'}
          onClick={sendTestAlert}
        >
          {alertLabel}
        </Button>

        {/* Direct sound play, no network trip — plays the current alert tone immediately */}
        <Button
          variant="transparent"
          size="compact-sm"
          color="var(--mantine-primary-color-contrast)"
          onClick={playDirectSound}
        >
          Play sound 🔊
        </Button>

        <Avatar
          component={NavLink}
          to="/profile"
          radius="xl"
          size="md"
          className="bm-avatar"
          aria-label="Personal settings"
          title={user ? `Personal settings — ${user}` : 'Personal settings'}
        >
          {initialOf(user)}
        </Avatar>
      </Group>
    </Group>
  )
}
