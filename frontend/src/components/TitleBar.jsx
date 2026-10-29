import { useEffect, useState } from 'react'
import { Avatar, Group, Title } from '@mantine/core'
import { NavLink } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'

// Shown until the systemName setting has been read (and if it is missing).
const DEFAULT_TITLE = 'BabyMonitor'

// First letter of the user's name, shown in the avatar (falls back to '?').
function initialOf(name) {
  const trimmed = (name ?? '').trim()
  return trimmed ? trimmed.charAt(0).toUpperCase() : '?'
}

// The blue title bar: the system name on the left and the account's initial on
// the right. Navigation lives in the sidebar (desktop) or tab bar (phone), so
// the bar keeps only what is identity, not wayfinding.
export default function TitleBar() {
  const [systemName, setSystemName] = useState(DEFAULT_TITLE)
  const { user } = useAuth()

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

  return (
    <Group h="100%" px="md" justify="space-between" wrap="nowrap">
      <Title order={1} fz="1.35rem" fw={600} className="bm-title">
        {systemName}
      </Title>

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
  )
}
