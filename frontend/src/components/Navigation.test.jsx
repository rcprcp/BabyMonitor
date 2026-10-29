import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { BottomNavigation, SideNavigation } from './Navigation'
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

describe('SideNavigation', () => {
  it('lists the four destinations and the cameras by name', async () => {
    renderWithProviders(<SideNavigation />)

    expect(screen.getByRole('link', { name: /Monitor/ })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: /Alerts/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Cameras/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /More/ })).toBeInTheDocument()

    // A named camera uses its name; an unnamed one falls back to its number.
    expect(await screen.findByRole('link', { name: 'Nursery' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Camera 2' })).toBeInTheDocument()
  })

  it('keeps the camera section active inside a camera page', async () => {
    renderWithProviders(<SideNavigation />, { route: '/cameras/1' })

    expect(screen.getByRole('link', { name: /Cameras/ })).toHaveAttribute('aria-current', 'page')
    expect(await screen.findByRole('link', { name: 'Nursery' })).toHaveAttribute(
      'aria-current',
      'page',
    )
  })

  it('does not mark Alerts active on another page', () => {
    renderWithProviders(<SideNavigation />, { route: '/settings' })

    expect(screen.getByRole('link', { name: /Alerts/ })).not.toHaveAttribute('aria-current')
    // Monitor is an `end` link: it is not active just because every path starts
    // with a slash.
    expect(screen.getByRole('link', { name: /Monitor/ })).not.toHaveAttribute('aria-current')
  })
})

describe('BottomNavigation', () => {
  it('marks the current tab and leaves the others alone', () => {
    renderWithProviders(<BottomNavigation />, { route: '/more' })

    expect(screen.getByRole('link', { name: /More/ })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: /Monitor/ })).not.toHaveAttribute('aria-current')
  })

  it('keeps Cameras active on a camera page', () => {
    renderWithProviders(<BottomNavigation />, { route: '/cameras/2' })

    expect(screen.getByRole('link', { name: /Cameras/ })).toHaveAttribute('aria-current', 'page')
  })
})
