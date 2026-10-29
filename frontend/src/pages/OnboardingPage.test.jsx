import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import OnboardingPage from './OnboardingPage'
import { renderWithProviders } from '../test/render'

const { api } = vi.hoisted(() => ({
  api: {
    listCameras: vi.fn(),
    listSettings: vi.fn(),
    listVideoDevices: vi.fn(),
    updateSetting: vi.fn(),
    updateCameraParameter: vi.fn(),
    applyCameraMotion: vi.fn(),
  },
}))

const { markComplete } = vi.hoisted(() => ({ markComplete: vi.fn() }))

vi.mock('../api', () => ({ api }))
vi.mock('../onboarding', () => ({
  useOnboarding: () => ({ complete: false, markComplete, restart: vi.fn() }),
}))
vi.mock('../auth', () => ({
  useAuth: () => ({ promptSound: 'chime', user: 'Bob' }),
  PROMPT_SOUNDS: [{ id: 'chime', prefix: 'notification-chime' }],
}))

beforeEach(() => {
  api.listCameras.mockResolvedValue([
    { cameraNumber: 1, key: 'camera_name', value: 'Nursery', enabled: true },
    { cameraNumber: 1, key: 'video_device', value: '/dev/video0', enabled: true },
    { cameraNumber: 1, key: 'stream_port', value: '8081', enabled: true },
    { cameraNumber: 2, key: 'camera_name', value: 'Hallway', enabled: true },
    { cameraNumber: 2, key: 'video_device', value: '/dev/video4', enabled: true },
    { cameraNumber: 2, key: 'stream_port', value: '8082', enabled: true },
  ])
  api.listSettings.mockResolvedValue([
    { tag: 'systemName', value: 'My Monitor' },
    { tag: 'stream_url', value: 'http://localhost' },
  ])
  api.listVideoDevices.mockResolvedValue([
    { device: '/dev/video0', name: 'Integrated Camera' },
    { device: '/dev/video2', name: 'USB Camera' },
  ])
  api.updateSetting.mockResolvedValue({})
  api.updateCameraParameter.mockImplementation((cameraNumber, key, value) =>
    Promise.resolve({ cameraNumber, key, value }),
  )
  api.applyCameraMotion.mockResolvedValue({ running: true })
})

describe('OnboardingPage', () => {
  it('saves the monitor name and moves on to the cameras', async () => {
    const user = userEvent.setup()
    renderWithProviders(<OnboardingPage />, { route: '/welcome' })

    const name = await screen.findByLabelText('Monitor name')
    expect(screen.getByText('Demo account')).toBeInTheDocument()
    await user.clear(name)
    await user.type(name, 'Nursery Monitor')
    await user.click(screen.getByRole('button', { name: 'Continue' }))

    expect(api.updateSetting).toHaveBeenCalledWith('systemName', 'Nursery Monitor')
    expect(await screen.findByRole('textbox', { name: 'Camera 1 video device' })).toHaveValue(
      '/dev/video0',
    )
    expect(screen.getByText(/Found 2 video devices/)).toBeInTheDocument()
  })

  it('saves a changed camera device and restarts that camera', async () => {
    const user = userEvent.setup()
    renderWithProviders(<OnboardingPage />, { route: '/welcome' })

    await screen.findByLabelText('Monitor name')
    await user.click(screen.getByRole('button', { name: 'Continue' }))

    const device = await screen.findByRole('textbox', { name: 'Camera 1 video device' })
    await user.clear(device)
    await user.type(device, '/dev/video2')
    await user.click(screen.getByRole('button', { name: 'Continue' }))

    expect(api.updateCameraParameter).toHaveBeenCalledWith(1, 'video_device', '/dev/video2')
    expect(api.applyCameraMotion).toHaveBeenCalledWith(1)
    expect(await screen.findByText('Check the picture and the sound')).toBeInTheDocument()
  })

  it('finishes the wizard and marks onboarding complete', async () => {
    const user = userEvent.setup()
    renderWithProviders(<OnboardingPage />, { route: '/welcome' })

    await screen.findByLabelText('Monitor name')
    await user.click(screen.getByRole('button', { name: 'Continue' }))
    await user.click(await screen.findByRole('button', { name: 'Continue' }))
    await user.click(await screen.findByRole('button', { name: 'Finish setup' }))

    expect(markComplete).toHaveBeenCalledTimes(1)
  })

  it('can be skipped', async () => {
    const user = userEvent.setup()
    renderWithProviders(<OnboardingPage />, { route: '/welcome' })

    await screen.findByLabelText('Monitor name')
    await user.click(screen.getByRole('button', { name: 'Skip for now' }))

    expect(markComplete).toHaveBeenCalledTimes(1)
  })

  it('reports an error when the name cannot be saved', async () => {
    const user = userEvent.setup()
    api.updateSetting.mockRejectedValue(new Error('Setting rejected'))
    renderWithProviders(<OnboardingPage />, { route: '/welcome' })

    await screen.findByLabelText('Monitor name')
    await user.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByText('Setting rejected')).toBeInTheDocument()
  })
})
