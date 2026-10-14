import { useEffect, useState } from 'react'
import { Avatar, Burger, Group, Menu, Title } from '@mantine/core'
import { NavLink, useNavigate } from 'react-router-dom'
import { api } from '../api'
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
  const { user, logout } = useAuth()
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

  return (
    <Group h="100%" px="md" justify="space-between" wrap="nowrap">
      <Menu opened={opened} onChange={setOpened} shadow="md" width={230} position="bottom-start">
        <Menu.Target>
          <Burger
            opened={opened}
            color="white"
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

      <Title order={1} c="white" fz="1.35rem" fw={600} truncate>
        {systemName}
      </Title>

      <Avatar
        component={NavLink}
        to="/profile"
        radius="xl"
        size="md"
        bg="white"
        c="blue.8"
        fw={600}
        aria-label="Personal settings"
        title={user ? `Personal settings — ${user}` : 'Personal settings'}
      >
        {initialOf(user)}
      </Avatar>
    </Group>
  )
}
