import { useEffect, useRef, useState } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'

// Blue title bar with a hamburger menu on the upper left. The menu is a
// dropdown that closes on navigation, outside click, or Escape.
export default function TitleBar() {
  const [open, setOpen] = useState(false)
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const menuRef = useRef(null)

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
            <button type="button" className="menu-link menu-signout" role="menuitem" onClick={handleLogout}>
              Sign out{user ? ` (${user})` : ''}
            </button>
          </nav>
        )}
      </div>

      <h1 className="titlebar-title">BabyMonitor</h1>

      <div className="titlebar-right" aria-hidden="true" />
    </header>
  )
}
