import { useCallback, useEffect, useState } from 'react'
import { Avatar, Burger, Drawer, Title } from '@mantine/core'
import { NavLink } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import { SETTINGS_CHANGED_EVENT } from '../settingsEvents'
import { SideNavigation } from './Navigation'

// Shown until the systemName setting has been read (and if it is missing).
const DEFAULT_TITLE = 'BabyMonitor'

// First letter of the user's name, shown in the avatar (falls back to '?').
function initialOf(name) {
  const trimmed = (name ?? '').trim()
  return trimmed ? trimmed.charAt(0).toUpperCase() : '?'
}

// The blue title bar: the system name in the middle and the account's initial
// on the right. On phones a hamburger opens the full destination list, which
// the four-tab bottom bar deliberately leaves out.
export default function TitleBar() {
  const [systemName, setSystemName] = useState(DEFAULT_TITLE)
  const [menuOpen, setMenuOpen] = useState(false)
  const { user } = useAuth()

  // The title comes from the systemName setting, so the system can be renamed
  // from the Settings page or the setup wizard. The default is kept if the tag
  // is missing.
  const loadTitle = useCallback(async () => {
    try {
      const setting = await api.getSetting('systemName')
      if (setting?.value) {
        setSystemName(setting.value)
        document.title = setting.value
      }
    } catch {
      // Keep the current title if the setting cannot be read.
    }
  }, [])

  useEffect(() => {
    loadTitle()
    window.addEventListener(SETTINGS_CHANGED_EVENT, loadTitle)
    return () => window.removeEventListener(SETTINGS_CHANGED_EVENT, loadTitle)
  }, [loadTitle])

  return (
    <div className="bm-titlebar-inner">
      <Burger
        className="bm-menu-button"
        hiddenFrom="sm"
        opened={menuOpen}
        onClick={() => setMenuOpen((open) => !open)}
        aria-label={menuOpen ? 'Close menu' : 'Open menu'}
        color="var(--mantine-primary-color-contrast)"
        size="sm"
      />

      <Title order={1} fz="1.35rem" fw={600} className="bm-title">
        {systemName}
      </Title>

      <Avatar
        component={NavLink}
        to="/settings"
        radius="xl"
        size="md"
        className="bm-avatar"
        aria-label="Settings"
        title={user ? `Settings — ${user}` : 'Settings'}
      >
        {initialOf(user)}
      </Avatar>

      <Drawer
        opened={menuOpen}
        onClose={() => setMenuOpen(false)}
        position="left"
        size="xs"
        title="Menu"
        transitionProps={{ duration: 0 }}
      >
        <SideNavigation onNavigate={() => setMenuOpen(false)} />
      </Drawer>
    </div>
  )
}
