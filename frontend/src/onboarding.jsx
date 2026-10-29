import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from './api'
import { useAuth } from './auth'

// First-run state for the setup wizard. `onboardingComplete` is a settings row
// created by the V14 migration: false on an untouched install, true on a
// deployment that already looks configured. Missing/unreadable is treated as
// complete so a broken setting can never lock the app behind a wizard.
const OnboardingContext = createContext(null)

export function OnboardingProvider({ children }) {
  const { isAuthenticated } = useAuth()
  const [complete, setComplete] = useState(true)

  // Only meaningful once signed in: the setting is behind the bearer gate, and a
  // 401 on the login screen must not be mistaken for "already set up".
  useEffect(() => {
    if (!isAuthenticated) {
      setComplete(true)
      return undefined
    }
    let cancelled = false
    api
      .getSetting('onboardingComplete')
      .then((setting) => {
        if (!cancelled) {
          setComplete(setting?.value !== 'false')
        }
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [isAuthenticated])

  const markComplete = useCallback(async () => {
    await api.updateSetting('onboardingComplete', 'true')
    setComplete(true)
  }, [])

  const restart = useCallback(async () => {
    await api.updateSetting('onboardingComplete', 'false')
    setComplete(false)
  }, [])

  const value = useMemo(
    () => ({ complete, markComplete, restart }),
    [complete, markComplete, restart],
  )

  return <OnboardingContext.Provider value={value}>{children}</OnboardingContext.Provider>
}

export function useOnboarding() {
  return useContext(OnboardingContext)
}
