// The six camera-light calibration rows, shared by the Environment page (band
// labels) and Environment Configuration (the calibration table).
export const LIGHT_BANDS = [
  { key: 'darkLuma', band: 'dark', number: 1, label: 'Dark' },
  { key: 'veryDimLuma', band: 'very-dim', number: 2, label: 'Very dim' },
  { key: 'dimLuma', band: 'dim', number: 3, label: 'Dim' },
  { key: 'moderateLuma', band: 'moderate', number: 4, label: 'Moderate' },
  { key: 'brightLuma', band: 'bright', number: 5, label: 'Bright' },
  { key: 'veryBrightLuma', band: 'very-bright', number: 6, label: 'Very bright' },
]

export const BAND_BY_NUMBER = Object.fromEntries(LIGHT_BANDS.map((band) => [band.number, band]))

export const BAND_COLORS = {
  dark: 'gray',
  'very-dim': 'gray',
  dim: 'blue',
  moderate: 'teal',
  bright: 'yellow',
  'very-bright': 'orange',
}
