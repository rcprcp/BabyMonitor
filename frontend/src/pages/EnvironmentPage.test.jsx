import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import EnvironmentPage from './EnvironmentPage'
import { renderWithProviders } from '../test/render'

const { api } = vi.hoisted(() => ({
  api: {
    listEnvironment: vi.fn(),
    listCameraLightBands: vi.fn(),
    getEnvironmentThresholds: vi.fn(),
  },
}))

vi.mock('../api', () => ({ api }))

const thresholds = {
  humidityLow: 30,
  humidityHigh: 75,
  temperatureLow: 66,
  temperatureHigh: 76,
  brightnessLow: 200,
  brightnessHigh: 2000,
}

beforeEach(() => {
  api.listEnvironment.mockResolvedValue([
    {
      eventTime: '2026-10-28T12:00:00Z',
      temperature: 20,
      humidity: 50,
      brightness: 100,
    },
  ])
  api.listCameraLightBands.mockResolvedValue([])
  api.getEnvironmentThresholds.mockResolvedValue(thresholds)
})

describe('EnvironmentPage', () => {
  it('shows the readings and links to the configuration page', async () => {
    renderWithProviders(<EnvironmentPage />)

    expect(await screen.findByRole('heading', { name: 'Environment' })).toBeInTheDocument()
    expect(await screen.findByText('1 readings')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Environment Configuration' })).toHaveAttribute(
      'href',
      '/environment-config',
    )
    // The installer cards moved to their own page.
    expect(screen.queryByRole('heading', { name: 'Alert bounds' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Camera light' })).not.toBeInTheDocument()
  })

  it('shows an error when the readings cannot be loaded', async () => {
    api.listEnvironment.mockRejectedValue(new Error('Readings unavailable'))
    renderWithProviders(<EnvironmentPage />)

    expect(await screen.findByText('Readings unavailable')).toBeInTheDocument()
  })
})
