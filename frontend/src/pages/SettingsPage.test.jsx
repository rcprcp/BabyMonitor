import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SettingsPage from './SettingsPage'
import { renderWithProviders } from '../test/render'

const { restart } = vi.hoisted(() => ({ restart: vi.fn() }))

const auth = vi.hoisted(() => ({
  logout: vi.fn(),
  saveColorScheme: vi.fn(),
  saveThemeColor: vi.fn(),
  savePromptSound: vi.fn(),
}))

vi.mock('../onboarding', () => ({
  useOnboarding: () => ({ complete: true, markComplete: vi.fn(), restart }),
}))
vi.mock('../auth', () => ({
  useAuth: () => ({
    user: 'Bob',
    colorScheme: 'auto',
    themeColor: 'blue',
    promptSound: 'chime',
    logout: auth.logout,
    saveColorScheme: auth.saveColorScheme,
    saveThemeColor: auth.saveThemeColor,
    savePromptSound: auth.savePromptSound,
  }),
  PROMPT_SOUNDS: [
    { id: 'chime', label: 'Chime (default)', prefix: 'notification-chime' },
    { id: 'soft', label: 'Soft tone', prefix: 'soft' },
  ],
}))

beforeEach(() => {
  restart.mockResolvedValue(undefined)
  auth.logout.mockResolvedValue(null)
  auth.saveColorScheme.mockResolvedValue({})
  auth.saveThemeColor.mockResolvedValue({})
  auth.savePromptSound.mockResolvedValue('soft')
})

describe('SettingsPage', () => {
  it('shows the account settings and links to System Settings', async () => {
    renderWithProviders(<SettingsPage />)

    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeInTheDocument()
    const openLinks = screen.getAllByRole('link', { name: 'Open' })
    expect(openLinks).toHaveLength(4)
    expect(openLinks.some((link) => link.getAttribute('href') === '/system-settings')).toBe(true)
    expect(screen.getByText('Audio Configuration')).toBeInTheDocument()
    expect(screen.getByText('Environment')).toBeInTheDocument()
    expect(screen.getByText('Environment Configuration')).toBeInTheDocument()
    expect(screen.getByText('Appearance')).toBeInTheDocument()
    expect(screen.getByText('Bob')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Run setup again' })).toBeInTheDocument()
    // System-wide fields moved to their own page.
    expect(screen.queryByLabelText(/Monitor name/)).not.toBeInTheDocument()
  })

  it('changes the appearance on the account', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SettingsPage />)

    await user.click(await screen.findByRole('radio', { name: 'Dark' }))

    expect(auth.saveColorScheme).toHaveBeenCalledWith('dark')
  })

  it('shows the current alert sound next to the other alert settings', async () => {
    renderWithProviders(<SettingsPage />)

    expect(await screen.findByRole('button', { name: 'Chime (default)' })).toBeInTheDocument()
  })

  it('logs out from the header', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SettingsPage />)

    const logout = await screen.findByRole('button', { name: 'Logout' })
    expect(logout.querySelector('svg')).not.toBeNull()
    await user.click(logout)

    expect(auth.logout).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
  })

  it('re-runs the setup wizard', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SettingsPage />)

    await user.click(await screen.findByRole('button', { name: 'Run setup again' }))

    expect(restart).toHaveBeenCalledTimes(1)
  })
})
