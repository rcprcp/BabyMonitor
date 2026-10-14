import { createTheme } from '@mantine/core'

// The three choices the Theme selector offers. The values are used verbatim as
// Mantine colour-scheme names and as the `theme` column value in
// babymonitor.users, so the two can never drift apart. `auto` follows the OS.
export const THEMES = [
  { label: 'Light', value: 'light' },
  { label: 'Dark', value: 'dark' },
  { label: 'Auto', value: 'auto' },
]

export const DEFAULT_THEME = 'auto'

export const theme = createTheme({
  primaryColor: 'blue',
  defaultRadius: 'md',
})

// The database is the source of truth for a user's theme, so Mantine is told
// not to keep a copy of its own (two stores would fight, and a stale
// localStorage value would beat the account setting).
export const noPersistenceColorSchemeManager = {
  get: (defaultValue) => defaultValue,
  set: () => {},
  subscribe: () => {},
  unsubscribe: () => {},
  clear: () => {},
}

// A throw-away hint, written whenever the account's theme is applied and read
// once at startup: it only stops the app from flashing the wrong colours while
// /api/users/me is in flight.
const HINT_KEY = 'babymonitor.theme'

export function readThemeHint() {
  const value = localStorage.getItem(HINT_KEY)
  return THEMES.some((option) => option.value === value) ? value : DEFAULT_THEME
}

export function writeThemeHint(value) {
  localStorage.setItem(HINT_KEY, value)
}
