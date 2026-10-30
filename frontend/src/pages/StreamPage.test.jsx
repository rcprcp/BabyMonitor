import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import StreamPage from './StreamPage'
import { renderWithProviders } from '../test/render'

const { api } = vi.hoisted(() => ({
  api: {
    listCameras: vi.fn(),
    listSettings: vi.fn(),
    getProfile: vi.fn(),
    updateActiveCamera: vi.fn(),
    getCameraAudio: vi.fn(),
    openCameraAudioStream: vi.fn(),
  },
}))

vi.mock('../api', () => ({ api }))

// The camera API returns one row per parameter; the page rolls them up.
const cameraRows = [
  { cameraNumber: 1, key: 'camera_name', value: 'Nursery', enabled: true },
  { cameraNumber: 1, key: 'stream_port', value: '8081', enabled: true },
  { cameraNumber: 2, key: 'camera_name', value: 'Hallway', enabled: false },
  { cameraNumber: 2, key: 'stream_port', value: '8082', enabled: false },
]

beforeEach(() => {
  localStorage.clear()
  api.listCameras.mockResolvedValue(cameraRows)
  api.listSettings.mockResolvedValue([{ tag: 'stream_url', value: 'http://localhost' }])
  api.getProfile.mockResolvedValue({ activeCamera: 1 })
  api.updateActiveCamera.mockResolvedValue({})
  api.getCameraAudio.mockResolvedValue({ running: false, enabled: false })
})

describe('StreamPage', () => {
  it('renders the live stream for the restored camera', async () => {
    renderWithProviders(<StreamPage />)

    expect(
      await screen.findByRole('heading', { name: 'Live stream — Nursery' }),
    ).toBeInTheDocument()
    const stream = await screen.findByAltText('Live baby monitor stream')
    expect(stream).toHaveAttribute('src', 'http://localhost:8081')
    expect(api.updateActiveCamera).toHaveBeenCalledWith('1')
  })

  it('offers each camera as a named button and grays out the stopped one', async () => {
    renderWithProviders(<StreamPage />)

    expect(await screen.findByRole('button', { name: 'Nursery (Camera1)' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    const stopped = screen.getByRole('button', { name: 'Hallway (Camera2) · stopped' })
    expect(stopped).toHaveAttribute('aria-pressed', 'false')
    expect(stopped).toBeDisabled()
    // The two camera buttons are separated by a hyphen.
    expect(screen.getByText('-')).toHaveAttribute('aria-hidden', 'true')
  })

  it('switches the stream and remembers the camera from the buttons', async () => {
    const user = userEvent.setup()
    api.listCameras.mockResolvedValue(cameraRows.map((row) => ({ ...row, enabled: true })))
    renderWithProviders(<StreamPage />)

    await user.click(await screen.findByRole('button', { name: 'Hallway (Camera2)' }))

    expect(api.updateActiveCamera).toHaveBeenCalledWith('2')
    const stream = await screen.findByAltText('Live baby monitor stream')
    expect(stream).toHaveAttribute('src', 'http://localhost:8082')
  })

  it('explains a stopped camera and links to its settings', async () => {
    api.getProfile.mockResolvedValue({ activeCamera: 2 })
    renderWithProviders(<StreamPage />)

    expect(await screen.findByText('Hallway is stopped')).toBeInTheDocument()
    const link = screen.getByRole('link', { name: 'Open camera settings' })
    expect(link).toHaveAttribute('href', '/cameras/2')
    expect(screen.queryByAltText('Live baby monitor stream')).not.toBeInTheDocument()
  })

  it('shows sound off without a listen button, and the volume can be set before listening', async () => {
    localStorage.setItem('babymonitor.listen.volume', '0.4')
    api.getCameraAudio.mockResolvedValue({ running: true, lastRms: 0 })
    renderWithProviders(<StreamPage />)

    expect(await screen.findByText('Sound off')).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: 'Sound' })).not.toBeChecked()
    expect(screen.queryByRole('button', { name: /listening/i })).not.toBeInTheDocument()

    const slider = screen.getByRole('slider', { name: 'Volume' })
    expect(slider).not.toHaveAttribute('aria-disabled', 'true')
    fireEvent.keyDown(slider, { key: 'ArrowRight' })

    expect(await screen.findByText('41%')).toBeInTheDocument()
    expect(localStorage.getItem('babymonitor.listen.volume')).toBe('0.41')
  })

  it('disables sound but leaves the volume available for a stopped camera', async () => {
    renderWithProviders(<StreamPage />)

    expect(await screen.findByText('Camera audio off')).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: 'Sound' })).toBeDisabled()
    expect(screen.getByRole('slider', { name: 'Volume' })).not.toHaveAttribute(
      'aria-disabled',
      'true',
    )
  })

  it('shows an error when the camera list cannot be loaded', async () => {
    api.listCameras.mockRejectedValue(new Error('Cannot load cameras'))
    renderWithProviders(<StreamPage />)

    expect(await screen.findByText('Cannot load cameras')).toBeInTheDocument()
  })
})
