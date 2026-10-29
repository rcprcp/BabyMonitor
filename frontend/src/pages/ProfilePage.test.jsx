import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ProfilePage from './ProfilePage'
import { renderWithProviders } from '../test/render'

const { api } = vi.hoisted(() => ({
  api: {
    getProfile: vi.fn(),
  },
}))

const { saveColorScheme, saveThemeColor, savePromptSound, logout } = vi.hoisted(() => ({
  saveColorScheme: vi.fn(),
  saveThemeColor: vi.fn(),
  savePromptSound: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('../api', () => ({ api }))

vi.mock('../auth', () => ({
  useAuth: () => ({
    user: 'Bob',
    colorScheme: 'auto',
    themeColor: 'blue',
    promptSound: 'chime',
    setPromptSound: vi.fn(),
    saveColorScheme,
    saveThemeColor,
    savePromptSound,
    logout,
  }),
  PROMPT_SOUNDS: [{ id: 'chime', label: 'Chime (default)', prefix: 'notification-chime' }],
}))

beforeEach(() => {
  api.getProfile.mockResolvedValue({
    id: 1,
    name: 'Bob',
    theme: 'auto',
    themeColor: 'blue',
    promptSound: 'chime',
  })
  saveColorScheme.mockResolvedValue({})
  saveThemeColor.mockResolvedValue({})
  savePromptSound.mockResolvedValue('chime')
  logout.mockResolvedValue(null)
})

describe('ProfilePage', () => {
  it('renders the account and appearance controls', async () => {
    renderWithProviders(<ProfilePage />)

    expect(await screen.findByRole('heading', { name: 'Personal settings' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Bob' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Auto' })).toBeChecked()
    expect(screen.getByRole('button', { name: 'Chime (default)' })).toBeInTheDocument()
  })

  it('saves the appearance choice', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ProfilePage />)

    await screen.findByRole('heading', { name: 'Bob' })
    await user.click(screen.getByRole('radio', { name: 'Dark' }))

    expect(saveColorScheme).toHaveBeenCalledWith('dark')
  })

  it('shows an error when the profile cannot be loaded', async () => {
    api.getProfile.mockRejectedValue(new Error('Profile unavailable'))
    renderWithProviders(<ProfilePage />)

    expect(await screen.findByText('Profile unavailable')).toBeInTheDocument()
  })
})
