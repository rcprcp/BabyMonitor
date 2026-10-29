// The presets the Audio notifications page offers. Each one is a plain index
// set, because the saved set of enabled classes is the single source of truth:
// the page derives which preset is active instead of storing a name that could
// drift from the switches in `babymonitor.audio_alert_classes`.
//
// 0-61 is the shipped default (migrations V6/V7 and
// AudioAlertSettings.defaultIndices). The cry classes are 11 Screaming,
// 19 Crying/sobbing, 20 Baby cry and 21 Whimper.

function range(first, last) {
  return Array.from({ length: last - first + 1 }, (_, offset) => first + offset)
}

export const PRESETS = [
  {
    id: 'cry',
    label: 'Cry only',
    summary: 'Screaming, crying, baby cry and whimper.',
    indices: [11, 19, 20, 21],
  },
  {
    id: 'voices',
    label: 'Voices and cries',
    summary: 'Speech and laughter as well as the cry sounds.',
    indices: range(0, 23),
  },
  {
    id: 'human',
    label: 'Human sounds',
    summary: 'Adds singing, breathing, coughing and footsteps.',
    indices: range(0, 61),
    isDefault: true,
  },
]

/**
 * The id of the preset whose index set is exactly `enabled`, or `"custom"` when
 * the saved set matches none of them.
 */
export function matchPreset(enabled) {
  const set = enabled instanceof Set ? enabled : new Set(enabled)
  const match = PRESETS.find(
    (preset) =>
      preset.indices.length === set.size && preset.indices.every((index) => set.has(index)),
  )
  return match ? match.id : 'custom'
}
