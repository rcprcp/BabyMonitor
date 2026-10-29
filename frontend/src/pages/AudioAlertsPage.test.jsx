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
  it('opens on the matching preset with every section closed', async () => {
    renderWithProviders(<AudioAlertsPage />)

    expect(await screen.findByRole('heading', { name: 'Audio Configuration' })).toBeInTheDocument()
    const human = await screen.findByRole('button', { name: /Human sounds/ })
    expect(human).toHaveAttribute('aria-pressed', 'true')
    expect(await screen.findByText('62 of 62 on')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Set to Default' })).toBeEnabled()
    expect(screen.queryByText('Custom')).not.toBeInTheDocument()

    const sections = document.querySelectorAll('details')
    expect(sections).toHaveLength(2)
    sections.forEach((section) => expect(section.open).toBe(false))
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

  it('opens a section to edit a class and marks the set Custom', async () => {
    const user = userEvent.setup()
    renderWithProviders(<AudioAlertsPage />)

    await user.click(await screen.findByText('Speech & Human Sounds'))
    expect(document.querySelector('details')).toHaveAttribute('open')

    const crying = screen.getByRole('checkbox', { name: '19 · Crying, sobbing' })
    expect(crying).toBeChecked()
    await user.click(crying)

    expect(api.setAudioAlertClass).toHaveBeenCalledWith(19, false)
    expect(await screen.findByText('Custom')).toBeInTheDocument()
  })

  it('restores the preset it started from with Set to Default', async () => {
    const user = userEvent.setup()
    renderWithProviders(<AudioAlertsPage />)

    await user.click(await screen.findByRole('button', { name: /Cry only/ }))
    await user.click(await screen.findByText('Speech & Human Sounds'))
    await user.click(screen.getByRole('checkbox', { name: '19 · Crying, sobbing' }))
    expect(await screen.findByText('3 of 62 on')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Set to Default' }))

    expect(api.setAudioAlertClasses).toHaveBeenLastCalledWith([11, 19, 20, 21])
    expect(await screen.findByText('4 of 62 on')).toBeInTheDocument()
  })

  it('falls back to the shipped default for a hand-picked set', async () => {
    const user = userEvent.setup()
    api.getAudioAlertClasses.mockResolvedValue(configFor([19]))
    renderWithProviders(<AudioAlertsPage />)

    expect(await screen.findByText('Custom')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Set to Default' }))

    expect(api.setAudioAlertClasses).toHaveBeenCalledWith(humanSounds)
    expect(await screen.findByText('62 of 62 on')).toBeInTheDocument()
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
