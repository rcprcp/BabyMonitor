import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import EnvironmentPage from './EnvironmentPage'
import { renderWithProviders } from '../test/render'

const { api } = vi.hoisted(() => ({
  api: {
    listEnvironment: vi.fn(),
    listCameraLightBands: vi.fn(),
    getEnvironmentThresholds: vi.fn(),
    updateEnvironmentThresholds: vi.fn(),
    getCameraLight: vi.fn(),
    listCameras: vi.fn(),
    getCameraMotion: vi.fn(),
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

const light = {
  cameraNumber: 1,
  exposureMs: 20,
  enabled: false,
  darkLuma: null,
  veryDimLuma: null,
  dimLuma: null,
  moderateLuma: null,
  brightLuma: null,
  veryBrightLuma: null,
  lastBand: null,
  lastLuma: null,
  lastLux: null,
  lastSampleAt: null,
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
  api.updateEnvironmentThresholds.mockResolvedValue(thresholds)
  api.getCameraLight.mockResolvedValue(light)
  api.listCameras.mockResolvedValue([
    { cameraNumber: 1, key: 'camera_name', value: 'Nursery', enabled: true },
  ])
  api.getCameraMotion.mockResolvedValue({ running: true })
})

describe('EnvironmentPage', () => {
  it('renders the readings, alert bounds and camera-light card', async () => {
    renderWithProviders(<EnvironmentPage />)

    expect(await screen.findByRole('heading', { name: 'Environment' })).toBeInTheDocument()
    expect(await screen.findByText('1 readings')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Alert bounds' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Camera light' })).toBeInTheDocument()
  })

  it('saves the alert bounds', async () => {
    const user = userEvent.setup()
    renderWithProviders(<EnvironmentPage />)

    await screen.findByRole('heading', { name: 'Alert bounds' })
    await user.click(screen.getByRole('button', { name: 'Save bounds' }))

    expect(api.updateEnvironmentThresholds).toHaveBeenCalledWith(
      expect.objectContaining(thresholds),
    )
  })

  it('shows an error when the readings cannot be loaded', async () => {
    api.listEnvironment.mockRejectedValue(new Error('Readings unavailable'))
    renderWithProviders(<EnvironmentPage />)

    expect(await screen.findByText('Readings unavailable')).toBeInTheDocument()
  })
})
