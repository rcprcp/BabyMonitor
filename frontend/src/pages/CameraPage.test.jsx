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
    listVideoDevices: vi.fn(),
  },
}))

vi.mock('../api', () => ({ api }))

const parameters = [
  { cameraNumber: 1, key: 'camera_name', value: 'Nursery', enabled: true },
  { cameraNumber: 1, key: 'video_device', value: '/dev/video0', enabled: true },
  { cameraNumber: 1, key: 'width', value: '1280', enabled: true },
  { cameraNumber: 1, key: 'height', value: '720', enabled: true },
  { cameraNumber: 1, key: 'framerate', value: '15', enabled: true },
  { cameraNumber: 1, key: 'stream_port', value: '8081', enabled: true },
  { cameraNumber: 1, key: 'threshold', value: '1500', enabled: true },
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
  api.updateCameraParameter.mockImplementation((cameraNumber, key, value) =>
    Promise.resolve({ cameraNumber, key, value }),
  )
  api.createCameraParameter.mockResolvedValue({ cameraNumber: 1, key: 'x', value: 'y' })
  api.deleteCameraParameter.mockResolvedValue(null)
  api.listVideoDevices.mockResolvedValue([
    { device: '/dev/video0', name: 'Integrated Camera' },
    { device: '/dev/video2', name: 'USB Camera' },
  ])
})

describe('CameraPage', () => {
  it('renders the friendly settings, status and the two controls', async () => {
    renderWithProviders(<CameraPage cameraNumber={1} />)

    expect(await screen.findByRole('heading', { name: 'Camera 1' })).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: 'Monitor this camera' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Listen for cries' })).toBeChecked()
    expect(screen.getByRole('textbox', { name: 'Camera name' })).toHaveValue('Nursery')
    expect(screen.getByRole('textbox', { name: 'Camera device' })).toHaveValue('/dev/video0')
    expect(screen.getByRole('textbox', { name: 'Resolution' })).toHaveValue('1280 × 720 (720p)')
    expect(screen.getByRole('textbox', { name: 'Frame rate' })).toHaveValue('15')
    expect(screen.getByRole('textbox', { name: 'Stream port' })).toHaveValue('8081')
    expect(screen.getByText('Recording · Audio on')).toBeInTheDocument()
  })

  it('saves the name without asking for a restart', async () => {
    const user = userEvent.setup()
    renderWithProviders(<CameraPage cameraNumber={1} />)

    const name = await screen.findByRole('textbox', { name: 'Camera name' })
    await user.clear(name)
    await user.type(name, 'Crib')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(api.updateCameraParameter).toHaveBeenCalledWith(1, 'camera_name', 'Crib')
    expect(screen.queryByRole('button', { name: 'Restart camera' })).not.toBeInTheDocument()
    expect(screen.getByText('Saved')).toBeInTheDocument()
  })

  it('asks for a restart after a device change and restarts on request', async () => {
    const user = userEvent.setup()
    renderWithProviders(<CameraPage cameraNumber={1} />)

    const device = await screen.findByRole('textbox', { name: 'Camera device' })
    await user.clear(device)
    await user.type(device, '/dev/video2')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(api.updateCameraParameter).toHaveBeenCalledWith(1, 'video_device', '/dev/video2')
    await user.click(await screen.findByRole('button', { name: 'Restart camera' }))

    expect(api.applyCameraMotion).toHaveBeenCalledWith(1)
  })

  it('writes width and height when the resolution preset changes', async () => {
    const user = userEvent.setup()
    renderWithProviders(<CameraPage cameraNumber={1} />)

    await user.click(await screen.findByRole('textbox', { name: 'Resolution' }))
    await user.click(await screen.findByText('1920 × 1080 (1080p)'))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(api.updateCameraParameter).toHaveBeenCalledWith(1, 'width', '1920')
    expect(api.updateCameraParameter).toHaveBeenCalledWith(1, 'height', '1080')
  })

  it('starts a stopped camera from the power switch', async () => {
    const user = userEvent.setup()
    api.getCamera.mockResolvedValue(stoppedParameters)
    api.getCameraMotion.mockResolvedValue({ running: false, pid: null, configExists: true })
    api.getCameraAudio.mockResolvedValue({ running: false, enabled: false, pid: null })
    renderWithProviders(<CameraPage cameraNumber={1} />)

    const power = await screen.findByRole('switch', { name: 'Monitor this camera' })
    expect(power).not.toBeChecked()
    expect(screen.getByText('Stopped')).toBeInTheDocument()

    await user.click(power)

    expect(api.applyCameraMotion).toHaveBeenCalledWith(1)
  })

  it('remembers the audio preference when Listen for cries is turned off', async () => {
    const user = userEvent.setup()
    renderWithProviders(<CameraPage cameraNumber={1} />)

    await user.click(await screen.findByRole('checkbox', { name: 'Listen for cries' }))

    expect(api.stopCameraAudio).toHaveBeenCalledWith(1)
    expect(
      await screen.findByText(/Listening starts again when the monitor restarts/),
    ).toBeInTheDocument()
  })

  it('disables the audio preference while the camera is stopped', async () => {
    api.getCamera.mockResolvedValue(stoppedParameters)
    api.getCameraMotion.mockResolvedValue({ running: false, pid: null, configExists: true })
    renderWithProviders(<CameraPage cameraNumber={1} />)

    expect(await screen.findByRole('checkbox', { name: 'Listen for cries' })).toBeDisabled()
  })

  it('keeps Motion options, pids and audio diagnostics under Advanced', async () => {
    const user = userEvent.setup()
    renderWithProviders(<CameraPage cameraNumber={1} />)

    const summary = await screen.findByText('Advanced')
    const disclosure = summary.closest('details')
    expect(disclosure).not.toHaveAttribute('open')

    await user.click(summary)

    expect(disclosure).toHaveAttribute('open')
    expect(screen.getByText('Motion options')).toBeInTheDocument()
    expect(screen.getByText('Motion: running (pid 123)')).toBeInTheDocument()
    expect(screen.getByText('Config: written')).toBeInTheDocument()
    expect(screen.getByDisplayValue('1500')).toBeInTheDocument()
  })

  it('can force audio on its own from Advanced', async () => {
    const user = userEvent.setup()
    api.getCamera.mockResolvedValue(stoppedParameters)
    api.getCameraMotion.mockResolvedValue({ running: false, pid: null, configExists: true })
    api.getCameraAudio.mockResolvedValue({ running: false, enabled: false, pid: null })
    renderWithProviders(<CameraPage cameraNumber={1} />)

    await user.click(await screen.findByText('Advanced'))
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
