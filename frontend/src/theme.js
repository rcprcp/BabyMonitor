import { createTheme } from '@mantine/core'

// Light / dark, stored per account in babymonitor.users.theme. `auto` follows
// the operating system. The values are also Mantine's own color-scheme names.
export const COLOR_SCHEMES = [
  { label: 'Light', value: 'light' },
  { label: 'Dark', value: 'dark' },
  { label: 'Auto', value: 'auto' },
]

export const DEFAULT_COLOR_SCHEME = 'auto'

// Mantine's own palette (https://mantine.dev/theming/colors/), in the order the
// Mantine docs list it. `dark` is left out: it is Mantine's surface color
// rather than a sensible accent. One of these names is stored in
// babymonitor.users.theme_color and used as the app's primary color.
export const PALETTE = [
  'gray',
  'red',
  'pink',
  'grape',
  'violet',
  'indigo',
  'blue',
  'cyan',
  'teal',
  'green',
  'lime',
  'yellow',
  'orange',
]

export const DEFAULT_THEME_COLOR = 'blue'

/** The Mantine theme, with the account's palette color as its primary. */
export function createAppTheme(primaryColor) {
  return createTheme({
    primaryColor,
    defaultRadius: 'md',
    // Without this Mantine answers white for every contrast color, so a pale
    // palette (yellow, lime, gray) would put white text on its own light shade.
    // With it the text color is picked per palette: dark on pale colors, white
    // on dark ones.
    autoContrast: true,
  })
}

// The database is the source of truth for a user's preferences, so Mantine is
// told not to keep a copy of its own (two stores would fight, and a stale
// localStorage value would beat the account setting).
export const noPersistenceColorSchemeManager = {
  get: (defaultValue) => defaultValue,
  set: () => {},
  subscribe: () => {},
  unsubscribe: () => {},
  clear: () => {},
}

// A throw-away hint, written whenever the account's preferences are applied and
// read once at startup: it only stops the app from flashing the wrong colors
// while /api/users/me is in flight.
const HINT_KEY = 'babymonitor.prefs'

export function readPrefsHint() {
  let stored = null
  try {
    stored = JSON.parse(localStorage.getItem(HINT_KEY))
  } catch {
    // Unreadable or malformed: fall through to the defaults.
  }
  return {
    colorScheme: COLOR_SCHEMES.some((option) => option.value === stored?.colorScheme)
      ? stored.colorScheme
      : DEFAULT_COLOR_SCHEME,
    themeColor: PALETTE.includes(stored?.themeColor) ? stored.themeColor : DEFAULT_THEME_COLOR,
  }
}

export function writePrefsHint({ colorScheme, themeColor }) {
  try {
    localStorage.setItem(HINT_KEY, JSON.stringify({ colorScheme, themeColor }))
  } catch {
    // Storage can be unavailable (private browsing); the hint is optional.
  }
}
