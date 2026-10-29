import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import EnvironmentConfigPage from './EnvironmentConfigPage'
import { renderWithProviders } from '../test/render'

const { api } = vi.hoisted(() => ({
  api: {
    getEnvironmentThresholds: vi.fn(),
    updateEnvironmentThresholds: vi.fn(),
    getCameraLight: vi.fn(),
    updateCameraLight: vi.fn(),
    listCameras: vi.fn(),
    getCameraMotion: vi.fn(),
    sampleCameraLight: vi.fn(),
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
  api.getEnvironmentThresholds.mockResolvedValue(thresholds)
  api.updateEnvironmentThresholds.mockResolvedValue(thresholds)
  api.getCameraLight.mockResolvedValue(light)
  api.updateCameraLight.mockResolvedValue(light)
  api.listCameras.mockResolvedValue([
    { cameraNumber: 1, key: 'camera_name', value: 'Nursery', enabled: true },
  ])
  api.getCameraMotion.mockResolvedValue({ running: true })
  api.sampleCameraLight.mockResolvedValue({
    luma: 12,
    band: 1,
    lux: 1,
    sampledAt: '2026-10-29T12:00:00Z',
  })
})

describe('EnvironmentConfigPage', () => {
  it('shows the alert bounds and camera-light calibration', async () => {
    renderWithProviders(<EnvironmentConfigPage />)

    expect(
      await screen.findByRole('heading', { name: 'Environment Configuration' }),
    ).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Alert bounds' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Camera light' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Capture' })).toHaveLength(6)
    expect(screen.getByRole('link', { name: 'View readings' })).toHaveAttribute(
      'href',
      '/environment',
    )
  })

  it('saves the alert bounds', async () => {
    const user = userEvent.setup()
    renderWithProviders(<EnvironmentConfigPage />)

    await screen.findByRole('heading', { name: 'Alert bounds' })
    await user.click(screen.getByRole('button', { name: 'Save bounds' }))

    expect(api.updateEnvironmentThresholds).toHaveBeenCalledWith(
      expect.objectContaining(thresholds),
    )
  })

  it('shows an error when the bounds cannot be loaded', async () => {
    api.getEnvironmentThresholds.mockRejectedValue(new Error('Bounds unavailable'))
    renderWithProviders(<EnvironmentConfigPage />)

    expect(await screen.findByText('Bounds unavailable')).toBeInTheDocument()
  })
})
