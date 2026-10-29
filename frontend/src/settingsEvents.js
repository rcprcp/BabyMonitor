// Fired after a settings row is written, so the title bar can pick up a new
// systemName without a reload (the setup wizard and the Settings page both
// rename the monitor).
export const SETTINGS_CHANGED_EVENT = 'babymonitor:settings-changed'

export function notifySettingsChanged() {
  window.dispatchEvent(new Event(SETTINGS_CHANGED_EVENT))
}
