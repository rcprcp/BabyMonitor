import { useCallback, useContext, createContext, useEffect, useMemo, useState } from 'react'
import { api, clearSession, getToken, getName, setSession, setUnauthorizedHandler } from './api'
import {
  DEFAULT_COLOR_SCHEME,
  DEFAULT_THEME_COLOR,
  readPrefsHint,
  writePrefsHint,
} from './theme'

// Auth state is just "is there a valid token in localStorage". We do not trust
// the stored name for anything security-relevant; it is only a display hint.
//
// The UI preferences (light/dark and accent color) belong to the account
// (babymonitor.users) and are cached here for the lifetime of the session.
// Deliberately free of Mantine imports: App.jsx builds the Mantine theme from
// this state, so this provider has to sit above MantineProvider.
const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => getToken())
  const [user, setUser] = useState(() => getName())
  const [colorScheme, setColorScheme] = useState(() => readPrefsHint().colorScheme)
  const [themeColor, setThemeColor] = useState(() => readPrefsHint().themeColor)

  // Remember the preferences so the next page load starts in the right colors.
  // It is a hint only: the account is what actually decides.
  useEffect(() => {
    writePrefsHint({ colorScheme, themeColor })
  }, [colorScheme, themeColor])

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
      setColorScheme(DEFAULT_COLOR_SCHEME)
      setThemeColor(DEFAULT_THEME_COLOR)
    }
  }, [])

  // If any API call comes back 401, drop the session so the UI redirects.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setToken(null)
      setUser(null)
      setColorScheme(DEFAULT_COLOR_SCHEME)
      setThemeColor(DEFAULT_THEME_COLOR)
    })
  }, [])

  const login = useCallback(async (name, password) => {
    const result = await api.login(name, password)
    setSession(result.token, result.name)
    setToken(result.token)
    setUser(result.name)
    // The login response carries the preferences so the app does not repaint
    // after the first request.
    setColorScheme(result.theme ?? DEFAULT_COLOR_SCHEME)
    setThemeColor(result.themeColor ?? DEFAULT_THEME_COLOR)
    return result
  }, [])

  // Both preferences are written straight to the account. The UI switches
  // immediately and is put back if the server rejects the value.
  const saveColorScheme = useCallback(
    async (next) => {
      const previous = colorScheme
      setColorScheme(next)
      try {
        const updated = await api.updateTheme(next)
        setColorScheme(updated.theme ?? next)
        return updated
      } catch (error) {
        setColorScheme(previous)
        throw error
      }
    },
    [colorScheme],
  )

  const saveThemeColor = useCallback(
    async (next) => {
      const previous = themeColor
      setThemeColor(next)
      try {
        const updated = await api.updateThemeColor(next)
        setThemeColor(updated.themeColor ?? next)
        return updated
      } catch (error) {
        setThemeColor(previous)
        throw error
      }
    },
    [themeColor],
  )

  // Confirm the stored token is still accepted once on startup, and pick up the
  // account's name and preferences in the same call.
  useEffect(() => {
    if (!token) {
      return
    }
    let cancelled = false
    api
      .getProfile()
      .then((profile) => {
        if (cancelled) {
          return
        }
        setUser(profile.name)
        setColorScheme(profile.theme ?? DEFAULT_COLOR_SCHEME)
        setThemeColor(profile.themeColor ?? DEFAULT_THEME_COLOR)
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
    () => ({
      token,
      user,
      colorScheme,
      themeColor,
      login,
      logout,
      saveColorScheme,
      saveThemeColor,
      isAuthenticated: Boolean(token),
    }),
    [token, user, colorScheme, themeColor, login, logout, saveColorScheme, saveThemeColor],
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
