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
  it('lists every destination in the requested order, with named cameras', async () => {
    renderWithProviders(<SideNavigation />)

    expect(screen.getByRole('link', { name: /Monitor/ })).toHaveAttribute('aria-current', 'page')
    // A named camera uses its name; an unnamed one falls back to its number.
    expect(await screen.findByRole('link', { name: 'Nursery' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Camera 2' })).toBeInTheDocument()

    const hrefs = screen.getAllByRole('link').map((link) => link.getAttribute('href'))
    expect(hrefs).toEqual([
      '/',
      '/notifications',
      '/environment',
      '/cameras',
      '/cameras/1',
      '/cameras/2',
      '/audio-alerts',
      '/environment-config',
      '/settings',
    ])
    expect(screen.getByRole('link', { name: /Environment Configuration/ })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /More/ })).not.toBeInTheDocument()
  })

  it('keeps the camera section active inside a camera page', async () => {
    renderWithProviders(<SideNavigation />, { route: '/cameras/1' })

    expect(screen.getByRole('link', { name: /Cameras/ })).toHaveAttribute('aria-current', 'page')
    expect(await screen.findByRole('link', { name: 'Nursery' })).toHaveAttribute(
      'aria-current',
      'page',
    )
  })

  it('marks Environment and does not mark Alerts elsewhere', () => {
    renderWithProviders(<SideNavigation />, { route: '/environment' })

    expect(screen.getByRole('link', { name: 'Environment' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(screen.getByRole('link', { name: /Alerts/ })).not.toHaveAttribute('aria-current')
    expect(screen.getByRole('link', { name: /Monitor/ })).not.toHaveAttribute('aria-current')
  })
})

describe('BottomNavigation', () => {
  it('keeps four daily tabs and marks the current one', () => {
    renderWithProviders(<BottomNavigation />, { route: '/settings' })

    expect(screen.getByRole('link', { name: /Settings/ })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: /Monitor/ })).not.toHaveAttribute('aria-current')
    // The phone bar has no More tab; the rest is reachable through Settings.
    expect(screen.queryByRole('link', { name: /More/ })).not.toBeInTheDocument()
    expect(screen.getAllByRole('link')).toHaveLength(4)
  })

  it('keeps Cameras active on a camera page', () => {
    renderWithProviders(<BottomNavigation />, { route: '/cameras/2' })

    expect(screen.getByRole('link', { name: /Cameras/ })).toHaveAttribute('aria-current', 'page')
  })
})
