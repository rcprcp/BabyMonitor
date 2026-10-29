import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SystemSettingsPage from './SystemSettingsPage'
import { renderWithProviders } from '../test/render'

const { api } = vi.hoisted(() => ({
  api: {
    listSettings: vi.fn(),
    updateSetting: vi.fn(),
  },
}))

vi.mock('../api', () => ({ api }))

const settings = [
  { tag: 'onboardingComplete', value: 'true' },
  { tag: 'stream_url', value: 'http://localhost' },
  { tag: 'systemName', value: 'My Monitor' },
]

beforeEach(() => {
  api.listSettings.mockResolvedValue(settings)
  api.updateSetting.mockImplementation((tag, value) => Promise.resolve({ tag, value }))
})

describe('SystemSettingsPage', () => {
  it('shows the labelled system fields and hides internal rows', async () => {
    renderWithProviders(<SystemSettingsPage />)

    expect(await screen.findByRole('heading', { name: 'System Settings' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /Monitor name/ })).toHaveValue('My Monitor')
    expect(screen.getByRole('textbox', { name: /Stream address/ })).toHaveValue('http://localhost')
    // The raw tag stays visible as secondary text.
    expect(screen.getByText('systemName')).toBeInTheDocument()
    expect(screen.getByText('stream_url')).toBeInTheDocument()
    expect(screen.queryByLabelText(/onboardingComplete/)).not.toBeInTheDocument()
  })

  it('saves both fields together and normalises the host', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SystemSettingsPage />)

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
    renderWithProviders(<SystemSettingsPage />)

    const host = await screen.findByRole('textbox', { name: /Stream address/ })
    await user.clear(host)
    await user.type(host, 'http://localhost:8080')
    await user.tab()

    expect(screen.getByText(/Leave the port out/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled()
    expect(api.updateSetting).not.toHaveBeenCalled()
  })

  it('keeps an unknown tag editable in the fallback section', async () => {
    const user = userEvent.setup()
    api.listSettings.mockResolvedValue([...settings, { tag: 'customTag', value: 'one' }])
    renderWithProviders(<SystemSettingsPage />)

    expect(await screen.findByText('Other settings')).toBeInTheDocument()
    const field = screen.getByRole('textbox', { name: 'customTag' })
    await user.clear(field)
    await user.type(field, 'two')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(api.updateSetting).toHaveBeenCalledWith('customTag', 'two')
  })
})
