import { useEffect, useRef, useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'

// Shown until the systemName setting has been read (and if it is missing).
const DEFAULT_TITLE = 'BabyMonitor'

// First letter of the user's name, shown in the avatar (falls back to '?').
function initialOf(name) {
  const trimmed = (name ?? '').trim()
  return trimmed ? trimmed.charAt(0).toUpperCase() : '?'
}

// Blue title bar with a hamburger menu on the upper left and the user's
// initial on the upper right. The menu is a dropdown that closes on
// navigation, outside click, or Escape.
export default function TitleBar() {
  const [open, setOpen] = useState(false)
  const [systemName, setSystemName] = useState(DEFAULT_TITLE)
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const menuRef = useRef(null)

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

  // Close the menu whenever the route changes.
  useEffect(() => {
    setOpen(false)
  }, [location.pathname])

  // Close on outside click / Escape.
  useEffect(() => {
    if (!open) {
      return undefined
    }
    const onClick = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setOpen(false)
      }
    }
    const onKey = (event) => {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  const linkClass = ({ isActive }) => (isActive ? 'menu-link active' : 'menu-link')
  const avatarClass = ({ isActive }) => (isActive ? 'avatar-button active' : 'avatar-button')

  return (
    <header className="titlebar">
      <div className="titlebar-left" ref={menuRef}>
        <button
          type="button"
          className="hamburger"
          aria-label="Open menu"
          aria-expanded={open}
          aria-haspopup="true"
          onClick={() => setOpen((value) => !value)}
        >
          <span />
          <span />
          <span />
        </button>

        {open && (
          <nav className="menu" role="menu">
            <NavLink to="/" end className={linkClass} role="menuitem">
              Live stream
            </NavLink>
            <NavLink to="/notifications" className={linkClass} role="menuitem">
              Notifications
            </NavLink>
            <NavLink to="/settings" className={linkClass} role="menuitem">
              Settings
            </NavLink>
            <NavLink to="/profile" className={linkClass} role="menuitem">
              Personal settings
            </NavLink>
            <button type="button" className="menu-link menu-signout" role="menuitem" onClick={handleLogout}>
              Sign out{user ? ` (${user})` : ''}
            </button>
          </nav>
        )}
      </div>

      <h1 className="titlebar-title">{systemName}</h1>

      <div className="titlebar-right">
        <NavLink
          to="/profile"
          className={avatarClass}
          aria-label="Personal settings"
          title={user ? `Personal settings — ${user}` : 'Personal settings'}
        >
          {initialOf(user)}
        </NavLink>
      </div>
    </header>
  )
}
