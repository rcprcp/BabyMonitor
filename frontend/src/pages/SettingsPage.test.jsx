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

vi.mock('../api', () => ({ api }))

const settings = [
  { tag: 'systemName', value: 'My Monitor' },
  { tag: 'stream_url', value: 'http://localhost' },
]

beforeEach(() => {
  api.listSettings.mockResolvedValue(settings)
  api.updateSetting.mockImplementation((tag, value) => Promise.resolve({ tag, value }))
})

describe('SettingsPage', () => {
  it('renders every setting', async () => {
    renderWithProviders(<SettingsPage />)

    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeInTheDocument()
    expect(await screen.findByLabelText('systemName')).toHaveValue('My Monitor')
    expect(screen.getByLabelText('stream_url')).toHaveValue('http://localhost')
  })

  it('saves an edited setting', async () => {
    const user = userEvent.setup()
    renderWithProviders(<SettingsPage />)

    const name = await screen.findByLabelText('systemName')
    await user.clear(name)
    await user.type(name, 'Renamed Monitor')
    await user.click(screen.getAllByRole('button', { name: 'Save' })[0])

    expect(api.updateSetting).toHaveBeenCalledWith('systemName', 'Renamed Monitor')
  })

  it('shows an error when saving fails', async () => {
    api.updateSetting.mockRejectedValue(new Error('Setting rejected'))
    const user = userEvent.setup()
    renderWithProviders(<SettingsPage />)

    const name = await screen.findByLabelText('systemName')
    await user.type(name, '!')
    await user.click(screen.getAllByRole('button', { name: 'Save' })[0])

    expect(await screen.findByText('Setting rejected')).toBeInTheDocument()
  })
})
