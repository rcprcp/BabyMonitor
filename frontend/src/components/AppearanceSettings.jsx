import { useState } from 'react'
import { Alert, Badge, SegmentedControl, Stack, Text } from '@mantine/core'
import { useAuth } from '../auth'
import { COLOR_SCHEMES } from '../theme'
import ThemePalette from './ThemePalette'

// Per-account appearance: the Mantine accent palette and light / dark / auto.
// Both live in babymonitor.users, so they follow the account to any browser.
export default function AppearanceSettings() {
  const { colorScheme, themeColor, saveColorScheme, saveThemeColor } = useAuth()

  const [savingColor, setSavingColor] = useState(false)
  const [colorSaved, setColorSaved] = useState(false)
  const [colorError, setColorError] = useState(null)

  const [savingScheme, setSavingScheme] = useState(false)
  const [schemeSaved, setSchemeSaved] = useState(false)
  const [schemeError, setSchemeError] = useState(null)

  const changeThemeColor = async (next) => {
    setColorError(null)
    setColorSaved(false)
    setSavingColor(true)
    try {
      await saveThemeColor(next)
      setColorSaved(true)
    } catch (err) {
      setColorError(err.message)
    } finally {
      setSavingColor(false)
    }
  }

  const changeColorScheme = async (next) => {
    setSchemeError(null)
    setSchemeSaved(false)
    setSavingScheme(true)
    try {
      await saveColorScheme(next)
      setSchemeSaved(true)
    } catch (err) {
      setSchemeError(err.message)
    } finally {
      setSavingScheme(false)
    }
  }

  return (
    <Stack gap="lg">
      <Stack gap="xs" align="flex-start">
        <Text fw={600}>Accent color</Text>
        <Text c="dimmed" size="sm">
          The color used for buttons, links and highlights.
        </Text>
        <ThemePalette value={themeColor} onChange={changeThemeColor} disabled={savingColor} />
        {colorSaved && (
          <Badge color="green" variant="light">
            Saved
          </Badge>
        )}
        {colorError && (
          <Alert color="red" variant="light">
            {colorError}
          </Alert>
        )}
      </Stack>

      <Stack gap="xs" align="flex-start">
        <Text fw={600}>Light or dark</Text>
        <Text c="dimmed" size="sm">
          Stored with your account, so it follows you to any browser you sign in from.
        </Text>
        <SegmentedControl
          value={colorScheme}
          onChange={changeColorScheme}
          data={COLOR_SCHEMES}
          disabled={savingScheme}
        />
        {schemeSaved && (
          <Badge color="green" variant="light">
            Saved
          </Badge>
        )}
        {schemeError && (
          <Alert color="red" variant="light">
            {schemeError}
          </Alert>
        )}
      </Stack>
    </Stack>
  )
}
