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

const NAMES = {
  0: 'Speech',
  11: 'Screaming',
  19: 'Crying, sobbing',
  20: 'Baby cry, infant cry',
  21: 'Whimper',
}

const GROUPS = [
  { id: 1, name: 'Speech & Human Sounds', firstIndex: 0, lastIndex: 23 },
  { id: 2, name: 'Singing & Music Genres', firstIndex: 24, lastIndex: 276 },
]

// The page only needs the classes a preset can name: 0-61 covers all three.
function configFor(enabled) {
  const on = new Set(enabled)
  return {
    groups: GROUPS,
    classes: Array.from({ length: 62 }, (_, index) => ({
      index,
      name: NAMES[index] ?? `Sound ${index}`,
      groupId: index <= 23 ? 1 : 2,
      enabled: on.has(index),
    })),
  }
}

const humanSounds = Array.from({ length: 62 }, (_, index) => index)

beforeEach(() => {
  api.getAudioAlertClasses.mockResolvedValue(configFor(humanSounds))
  api.setAudioAlertClass.mockImplementation((index, enabled) =>
    Promise.resolve(configFor(enabled ? [index] : [])),
  )
  api.setAudioAlertClasses.mockImplementation((indices) => Promise.resolve(configFor(indices)))
  api.resetAudioAlertClasses.mockResolvedValue(configFor(humanSounds))
})

describe('AudioAlertsPage', () => {
  it('opens on the preset that matches the saved set', async () => {
    renderWithProviders(<AudioAlertsPage />)

    expect(await screen.findByRole('heading', { name: 'Audio notifications' })).toBeInTheDocument()
    const human = await screen.findByRole('button', { name: /Human sounds/ })
    expect(human).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /Cry only/ })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
    expect(await screen.findByText('62 of 62 on')).toBeInTheDocument()
    // The 521 checkboxes are a tuning screen, not the default view.
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  it('saves the whole set when a preset is chosen', async () => {
    const user = userEvent.setup()
    renderWithProviders(<AudioAlertsPage />)

    const cry = await screen.findByRole('button', { name: /Cry only/ })
    await user.click(cry)

    expect(api.setAudioAlertClasses).toHaveBeenCalledWith([11, 19, 20, 21])
    expect(await screen.findByText('4 of 62 on')).toBeInTheDocument()
    expect(cry).toHaveAttribute('aria-pressed', 'true')
  })

  it('keeps the full list behind Custom', async () => {
    const user = userEvent.setup()
    renderWithProviders(<AudioAlertsPage />)

    const custom = await screen.findByRole('button', { name: /Custom/ })
    expect(custom).toHaveAttribute('aria-expanded', 'false')

    await user.click(custom)

    expect(custom).toHaveAttribute('aria-expanded', 'true')
    const crying = screen.getByRole('checkbox', { name: '19 · Crying, sobbing' })
    expect(crying).toBeChecked()

    await user.click(crying)

    expect(api.setAudioAlertClass).toHaveBeenCalledWith(19, false)
  })

  it('shows a hand-picked set as Custom and opens it', async () => {
    api.getAudioAlertClasses.mockResolvedValue(configFor([19]))
    renderWithProviders(<AudioAlertsPage />)

    expect((await screen.findAllByText('1 of 62 on')).length).toBeGreaterThan(0)
    const custom = screen.getByRole('button', { name: /Custom/ })
    expect(custom).toHaveAttribute('aria-expanded', 'true')
    expect(custom).toHaveTextContent('Current')
    expect(screen.getByRole('checkbox', { name: '19 · Crying, sobbing' })).toBeChecked()
  })

  it('warns when no sound can alert', async () => {
    api.getAudioAlertClasses.mockResolvedValue(configFor([]))
    renderWithProviders(<AudioAlertsPage />)

    expect(await screen.findByText('No sounds are enabled')).toBeInTheDocument()
  })

  it('shows an error when the configuration cannot be loaded', async () => {
    api.getAudioAlertClasses.mockRejectedValue(new Error('Config unavailable'))
    renderWithProviders(<AudioAlertsPage />)

    expect(await screen.findByText('Config unavailable')).toBeInTheDocument()
  })
})
