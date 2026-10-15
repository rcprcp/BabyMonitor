import { ColorSwatch, Group, Tooltip, useMantineTheme } from '@mantine/core'
import { PALETTE } from '../theme'

/**
 * Mantine's colour palette as clickable swatches
 * (https://mantine.dev/theming/colors/). The chosen name becomes the app's
 * primary colour — buttons, links and highlights — and is stored on the account.
 *
 * Every swatch shows shade 6, which is the shade Mantine treats as the colour's
 * own identity.
 */
export default function ThemePalette({ value, onChange, disabled }) {
  const theme = useMantineTheme()

  return (
    <Group gap="xs">
      {PALETTE.map((name) => {
        const color = theme.colors[name][6]
        const selected = name === value
        return (
          <Tooltip key={name} label={name} withArrow openDelay={200}>
            <ColorSwatch
              component="button"
              type="button"
              color={color}
              onClick={() => onChange(name)}
              disabled={disabled}
              aria-label={`Use the ${name} palette`}
              aria-pressed={selected}
              style={
                selected
                  ? // A gap in the page colour then a ring in the colour itself, so
                    // the selection is visible whatever the scheme.
                    { boxShadow: `0 0 0 2px var(--mantine-color-body), 0 0 0 4px ${color}` }
                  : undefined
              }
            >
              {selected && <span className="bm-swatch-check">✓</span>}
            </ColorSwatch>
          </Tooltip>
        )
      })}
    </Group>
  )
}
