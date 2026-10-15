import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { useMantineColorScheme } from '@mantine/core'
import { api, clearSession, getToken, getName, setSession, setUnauthorizedHandler } from './api'
import { DEFAULT_THEME, readThemeHint, writeThemeHint } from './theme'

// Auth state is just "is there a valid token in localStorage". We do not trust
// the stored name for anything security-relevant; it is only a display hint.
// The theme preference belongs to the account (babymonitor.users.theme) and is
// cached here for the lifetime of the session.
const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => getToken())
  const [user, setUser] = useState(() => getName())
  const [theme, setTheme] = useState(() => readThemeHint())
  const { setColorScheme } = useMantineColorScheme()

  // Hand the account's theme to Mantine. The hint is refreshed alongside it so
  // the next page load starts in the right colours.
  useEffect(() => {
    setColorScheme(theme)
    writeThemeHint(theme)
  }, [theme, setColorScheme])

  const logout = useCallback(async () => {
    try {
      if (getToken()) {
        await api.logout()
      }
    } catch {
      // Logging out is best-effort: even if the call fails, drop local state.
    } finally {
      clearSession()
      setToken(null)
      setUser(null)
      setTheme(DEFAULT_THEME)
    }
  }, [])

  // If any API call comes back 401, drop the session so the UI redirects.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setToken(null)
      setUser(null)
      setTheme(DEFAULT_THEME)
    })
  }, [])

  const login = useCallback(async (name, password) => {
    const result = await api.login(name, password)
    setSession(result.token, result.name)
    setToken(result.token)
    setUser(result.name)
    // The login response carries the theme so the app does not repaint after
    // the first request.
    setTheme(result.theme ?? DEFAULT_THEME)
    return result
  }, [])

  // Store the preference on the account. The UI switches immediately and is put
  // back if the server rejects the value.
  const saveTheme = useCallback(
    async (next) => {
      const previous = theme
      setTheme(next)
      try {
        const updated = await api.updateTheme(next)
        setTheme(updated.theme ?? next)
        return updated
      } catch (error) {
        setTheme(previous)
        throw error
      }
    },
    [theme],
  )

  // Confirm the stored token is still accepted once on startup, and pick up the
  // account's name and theme in the same call.
  useEffect(() => {
    if (!token) {
      return
    }
    let cancelled = false
    api
      .getProfile()
      .then((profile) => {
        if (!cancelled) {
          setUser(profile.name)
          setTheme(profile.theme ?? DEFAULT_THEME)
        }
      })
      .catch(() => {
        // 401 handling already cleared the session.
      })
    return () => {
      cancelled = true
    }
    // Only re-run when the token itself changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  const value = useMemo(
    () => ({ token, user, theme, login, logout, saveTheme, isAuthenticated: Boolean(token) }),
    [token, user, theme, login, logout, saveTheme],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}
