import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AudioAlertsPage from './AudioAlertsPage'
import { renderWithProviders } from '../test/render'

const { api } = vi.hoisted(() => ({
  api: {
    getAudioAlertClasses: vi.fn(),
    setAudioAlertClass: vi.fn(),
    setAudioAlertClasses: vi.fn(),
    resetAudioAlertClasses: vi.fn(),
  },
}))

vi.mock('../api', () => ({ api }))

const config = {
  groups: [{ id: 1, name: 'Speech & Human Sounds', firstIndex: 0, lastIndex: 23 }],
  classes: [
    { index: 19, name: 'Crying, sobbing', groupId: 1, enabled: true },
    { index: 20, name: 'Baby cry, infant cry', groupId: 1, enabled: false },
  ],
}

beforeEach(() => {
  api.getAudioAlertClasses.mockResolvedValue(config)
  api.setAudioAlertClass.mockResolvedValue(config)
  api.setAudioAlertClasses.mockResolvedValue(config)
  api.resetAudioAlertClasses.mockResolvedValue(config)
})

describe('AudioAlertsPage', () => {
  it('renders the configured classes', async () => {
    renderWithProviders(<AudioAlertsPage />)

    expect(
      await screen.findByRole('heading', { name: 'Audio Notification Config' }),
    ).toBeInTheDocument()
    expect((await screen.findAllByText('1 of 2 on')).length).toBeGreaterThan(0)
    expect(screen.getByRole('checkbox', { name: '19 · Crying, sobbing' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: '20 · Baby cry, infant cry' })).not.toBeChecked()
  })

  it('saves a single class when its checkbox is toggled', async () => {
    const user = userEvent.setup()
    renderWithProviders(<AudioAlertsPage />)

    const crying = await screen.findByRole('checkbox', { name: '19 · Crying, sobbing' })
    await user.click(crying)

    expect(api.setAudioAlertClass).toHaveBeenCalledWith(19, false)
  })

  it('shows an error when the configuration cannot be loaded', async () => {
    api.getAudioAlertClasses.mockRejectedValue(new Error('Config unavailable'))
    renderWithProviders(<AudioAlertsPage />)

    expect(await screen.findByText('Config unavailable')).toBeInTheDocument()
  })
})
