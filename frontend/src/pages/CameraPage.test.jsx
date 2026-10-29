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

const stoppedParameters = parameters.map((parameter) => ({ ...parameter, enabled: false }))

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
  it('renders the parameters, status and the two controls', async () => {
    renderWithProviders(<CameraPage cameraNumber={1} />)

    expect(await screen.findByRole('heading', { name: 'Camera 1' })).toBeInTheDocument()
    expect(await screen.findByText('Nursery')).toBeInTheDocument()
    expect(screen.getByDisplayValue('/dev/video0')).toBeInTheDocument()
    expect(screen.getByText('Recording')).toBeInTheDocument()
    expect(screen.getByText('Audio on')).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: 'Monitor this camera' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Listen for cries' })).toBeChecked()
    // The old separate buttons are gone.
    expect(screen.queryByRole('button', { name: 'Start/Apply' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Stop' })).not.toBeInTheDocument()
  })

  it('starts a stopped camera from the power switch', async () => {
    const user = userEvent.setup()
    api.getCamera.mockResolvedValue(stoppedParameters)
    api.getCameraMotion.mockResolvedValue({ running: false, pid: null, configExists: true })
    api.getCameraAudio.mockResolvedValue({ running: false, enabled: false, pid: null })
    renderWithProviders(<CameraPage cameraNumber={1} />)

    const power = await screen.findByRole('switch', { name: 'Monitor this camera' })
    expect(power).not.toBeChecked()
    // A stopped camera shows no audio chip at all.
    expect(screen.queryByText('Audio on')).not.toBeInTheDocument()
    expect(screen.queryByText('Audio off')).not.toBeInTheDocument()

    await user.click(power)

    expect(api.applyCameraMotion).toHaveBeenCalledWith(1)
  })

  it('remembers the audio preference when Listen for cries is turned off', async () => {
    const user = userEvent.setup()
    renderWithProviders(<CameraPage cameraNumber={1} />)

    const listen = await screen.findByRole('checkbox', { name: 'Listen for cries' })
    await user.click(listen)

    expect(api.stopCameraAudio).toHaveBeenCalledWith(1)
  })

  it('disables the audio preference while the camera is stopped', async () => {
    api.getCamera.mockResolvedValue(stoppedParameters)
    api.getCameraMotion.mockResolvedValue({ running: false, pid: null, configExists: true })
    renderWithProviders(<CameraPage cameraNumber={1} />)

    expect(await screen.findByRole('checkbox', { name: 'Listen for cries' })).toBeDisabled()
  })

  it('can force audio on its own from Diagnostics', async () => {
    const user = userEvent.setup()
    api.getCamera.mockResolvedValue(stoppedParameters)
    api.getCameraMotion.mockResolvedValue({ running: false, pid: null, configExists: true })
    api.getCameraAudio.mockResolvedValue({ running: false, enabled: false, pid: null })
    renderWithProviders(<CameraPage cameraNumber={1} />)

    await user.click(await screen.findByText('Diagnostics'))
    await user.click(await screen.findByRole('button', { name: 'Start audio' }))

    expect(api.startCameraAudio).toHaveBeenCalledWith(1)
  })

  it('shows an error when the camera cannot be started', async () => {
    const user = userEvent.setup()
    api.getCamera.mockResolvedValue(stoppedParameters)
    api.getCameraMotion.mockResolvedValue({ running: false, pid: null, configExists: true })
    api.applyCameraMotion.mockRejectedValue(new Error('Motion refused to start'))
    renderWithProviders(<CameraPage cameraNumber={1} />)

    const power = await screen.findByRole('switch', { name: 'Monitor this camera' })
    await waitFor(() => expect(power).toBeEnabled())
    await user.click(power)

    expect(await screen.findByText('Motion refused to start')).toBeInTheDocument()
  })
})
