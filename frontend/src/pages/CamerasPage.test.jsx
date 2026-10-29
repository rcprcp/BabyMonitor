import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import CamerasPage from './CamerasPage'
import { renderWithProviders } from '../test/render'

const { api } = vi.hoisted(() => ({ api: { listCameras: vi.fn() } }))

vi.mock('../api', () => ({ api }))

beforeEach(() => {
  api.listCameras.mockResolvedValue([
    { cameraNumber: 1, key: 'camera_name', value: 'Nursery', enabled: true },
    { cameraNumber: 1, key: 'stream_port', value: '8081', enabled: true },
    { cameraNumber: 2, key: 'stream_port', value: '8082', enabled: true },
  ])
})

describe('CamerasPage', () => {
  it('lists each camera by name, falling back to its number', async () => {
    renderWithProviders(<CamerasPage />)

    expect(await screen.findByRole('heading', { name: 'Camera Configuration' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: /Nursery/ })).toHaveAttribute(
      'href',
      '/cameras/1',
    )
    expect(screen.getByRole('link', { name: /Camera 2/ })).toHaveAttribute('href', '/cameras/2')
    // A camera without a name says so rather than showing a blank card.
    expect(screen.getByText('No name set on this camera')).toBeInTheDocument()
  })

  it('shows an error when the cameras cannot be loaded', async () => {
    api.listCameras.mockRejectedValue(new Error('Cameras unavailable'))
    renderWithProviders(<CamerasPage />)

    expect(await screen.findByText('Cameras unavailable')).toBeInTheDocument()
  })
})
