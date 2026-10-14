import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, clearSession, getToken, getName, setSession, setUnauthorizedHandler } from './api'

// Auth state is just "is there a valid token in localStorage". We do not trust
// the stored name for anything security-relevant; it is only a display hint.
const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => getToken())
  const [user, setUser] = useState(() => getName())

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
    }
  }, [])

  // If any API call comes back 401, drop the session so the UI redirects.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setToken(null)
      setUser(null)
    })
  }, [])

  const login = useCallback(async (name, password) => {
    const result = await api.login(name, password)
    setSession(result.token, result.name)
    setToken(result.token)
    setUser(result.name)
    return result
  }, [])

  // Confirm the stored token is still accepted once on startup.
  useEffect(() => {
    if (!token) {
      return
    }
    let cancelled = false
    api
      .me()
      .then((me) => {
        if (!cancelled) {
          setUser(me.name)
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
    () => ({ token, user, login, logout, isAuthenticated: Boolean(token) }),
    [token, user, login, logout],
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
