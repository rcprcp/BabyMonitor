import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import CameraPage from './CameraPage'
import { renderWithProviders } from '../test/render'

const { api } = vi.hoisted(() => ({
  api: {
    getCamera: vi.fn(),
    getCameraMotion: vi.fn(),
    getCameraAudio: vi.fn(),
    applyCameraMotion: vi.fn(),
    stopCameraMotion: vi.fn(),
    startCameraAudio: vi.fn(),
    stopCameraAudio: vi.fn(),
    updateCameraParameter: vi.fn(),
    createCameraParameter: vi.fn(),
    deleteCameraParameter: vi.fn(),
  },
}))

vi.mock('../api', () => ({ api }))

const parameters = [
  { cameraNumber: 1, key: 'camera_name', value: 'Nursery', enabled: true },
  { cameraNumber: 1, key: 'video_device', value: '/dev/video0', enabled: true },
]

beforeEach(() => {
  api.getCamera.mockResolvedValue(parameters)
  api.getCameraMotion.mockResolvedValue({
    running: true,
    pid: 123,
    configExists: true,
    deviceAvailable: true,
  })
  api.getCameraAudio.mockResolvedValue({ running: true, enabled: true, pid: 456 })
  api.applyCameraMotion.mockResolvedValue({
    running: true,
    pid: 123,
    configExists: true,
    deviceAvailable: true,
  })
  api.stopCameraMotion.mockResolvedValue({ running: false, pid: null, configExists: true })
  api.startCameraAudio.mockResolvedValue({ running: true, enabled: true, pid: 456 })
  api.stopCameraAudio.mockResolvedValue({ running: false, enabled: false })
  api.updateCameraParameter.mockResolvedValue({ ...parameters[0] })
  api.createCameraParameter.mockResolvedValue({ cameraNumber: 1, key: 'x', value: 'y' })
  api.deleteCameraParameter.mockResolvedValue(null)
})

describe('CameraPage', () => {
  it('renders the camera parameters and status', async () => {
    renderWithProviders(<CameraPage cameraNumber={1} />)

    expect(await screen.findByRole('heading', { name: 'Camera 1' })).toBeInTheDocument()
    expect(await screen.findByText('Nursery')).toBeInTheDocument()
    expect(screen.getByDisplayValue('/dev/video0')).toBeInTheDocument()
    expect(screen.getByText('Recording')).toBeInTheDocument()
    expect(screen.getByText('Audio on')).toBeInTheDocument()
    // The pid is still one hover away, for support, but no longer the label.
    expect(screen.queryByText(/pid/)).not.toBeInTheDocument()
  })

  it('starts a stopped camera', async () => {
    api.getCamera.mockResolvedValue(
      parameters.map((parameter) => ({ ...parameter, enabled: false })),
    )
    api.getCameraMotion.mockResolvedValue({ running: false, pid: null, configExists: true })
    api.getCameraAudio.mockResolvedValue({ running: false, enabled: false, pid: null })
    renderWithProviders(<CameraPage cameraNumber={1} />)

    expect(await screen.findByRole('button', { name: 'Start camera' })).toBeEnabled()
    expect(screen.getByText('Stopped')).toBeInTheDocument()
    expect(screen.getByText('Audio stopped')).toBeInTheDocument()
  })

  it('restarts the camera when Restart camera is clicked', async () => {
    const user = userEvent.setup()
    renderWithProviders(<CameraPage cameraNumber={1} />)

    const apply = await screen.findByRole('button', { name: 'Restart camera' })
    await waitFor(() => expect(apply).toBeEnabled())
    await user.click(apply)

    expect(api.applyCameraMotion).toHaveBeenCalledWith(1)
  })

  it('shows an error when the camera cannot be applied', async () => {
    api.applyCameraMotion.mockRejectedValue(new Error('Motion refused to start'))
    const user = userEvent.setup()
    renderWithProviders(<CameraPage cameraNumber={1} />)

    const apply = await screen.findByRole('button', { name: 'Restart camera' })
    await waitFor(() => expect(apply).toBeEnabled())
    await user.click(apply)

    expect(await screen.findByText('Motion refused to start')).toBeInTheDocument()
  })
})
