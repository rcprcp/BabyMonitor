import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SettingsPage from './SettingsPage'
import { renderWithProviders } from '../test/render'

const { api } = vi.hoisted(() => ({
  api: {
    listSettings: vi.fn(),
    updateSetting: vi.fn(),
  },
}))

const { restart } = vi.hoisted(() => ({ restart: vi.fn() }))

const auth = vi.hoisted(() => ({
  logout: vi.fn(),
  saveColorScheme: vi.fn(),
  saveThemeColor: vi.fn(),
  savePromptSound: vi.fn(),
}))

vi.mock('../api', () => ({ api }))
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

const settings = [
  { tag: 'onboardingComplete', value: 'true' },
  { tag: 'stream_url', value: 'http://localhost' },
  { tag: 'systemName', value: 'My Monitor' },
]

beforeEach(() => {
  api.listSettings.mockResolvedValue(settings)
  api.updateSetting.mockImplementation((tag, value) => Promise.resolve({ tag, value }))
  restart.mockResolvedValue(undefined)
  auth.logout.mockResolvedValue(null)
  auth.saveColorScheme.mockResolvedValue({})
  auth.saveThemeColor.mockResolvedValue({})
  auth.savePromptSound.mockResolvedValue('soft')
})

describe('SettingsPage', () => {
  it('shows one page of labelled settings and hides internal rows', async () => {
    renderWithProviders(<SettingsPage />)

    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /Monitor name/ })).toHaveValue('My Monitor')
    expect(screen.getByRole('textbox', { name: /Stream address/ })).toHaveValue('http://localhost')
    // The raw tag stays visible as secondary text.
    expect(screen.getByText('systemName')).toBeInTheDocument()
    expect(screen.getByText('stream_url')).toBeInTheDocument()
    expect(screen.queryByLabelText(/onboardingComplete/)).not.toBeInTheDocument()

    expect(screen.getAllByRole('link', { name: 'Open' })).toHaveLength(2)
    expect(screen.getByText('Audio Configuration')).toBeInTheDocument()
    expect(screen.getByText('Environment')).toBeInTheDocument()
    expect(screen.getByText('Appearance')).toBeInTheDocument()
    expect(screen.getByText('Bob')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Run setup again' })).toBeInTheDocument()
  })

  it('saves both system fields together and normalises the host', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SettingsPage />)

    const name = await screen.findByRole('textbox', { name: /Monitor name/ })
    await user.clear(name)
    await user.type(name, 'Nursery Monitor')
    const host = screen.getByRole('textbox', { name: /Stream address/ })
    await user.clear(host)
    await user.type(host, 'http://192.168.1.20/')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(api.updateSetting).toHaveBeenCalledWith('systemName', 'Nursery Monitor')
    expect(api.updateSetting).toHaveBeenCalledWith('stream_url', 'http://192.168.1.20')
    expect(await screen.findByText('Saved')).toBeInTheDocument()
  })

  it('refuses a stream address with a port and explains why', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SettingsPage />)

    const host = await screen.findByRole('textbox', { name: /Stream address/ })
    await user.clear(host)
    await user.type(host, 'http://localhost:8080')
    await user.tab()

    expect(screen.getByText(/Leave the port out/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled()
    expect(api.updateSetting).not.toHaveBeenCalled()
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

  it('signs out from the account card', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SettingsPage />)

    await user.click(await screen.findByRole('button', { name: 'Sign out' }))

    expect(auth.logout).toHaveBeenCalledTimes(1)
  })

  it('re-runs the setup wizard', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SettingsPage />)

    await user.click(await screen.findByRole('button', { name: 'Run setup again' }))

    expect(restart).toHaveBeenCalledTimes(1)
  })

  it('keeps an unknown tag editable in the fallback section', async () => {
    const user = userEvent.setup()
    api.listSettings.mockResolvedValue([...settings, { tag: 'customTag', value: 'one' }])
    renderWithProviders(<SettingsPage />)

    expect(await screen.findByText('Other settings')).toBeInTheDocument()
    const field = screen.getByRole('textbox', { name: 'customTag' })
    await user.clear(field)
    await user.type(field, 'two')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(api.updateSetting).toHaveBeenCalledWith('customTag', 'two')
  })
})
