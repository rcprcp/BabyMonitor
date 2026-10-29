import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { BottomNavigation, SideNavigation } from './Navigation'
import { renderWithProviders } from '../test/render'

describe('SideNavigation', () => {
  it('lists every destination in the requested order, without camera names', () => {
    renderWithProviders(<SideNavigation />)

    expect(screen.getByRole('link', { name: /Live Stream/ })).toHaveAttribute(
      'aria-current',
      'page',
    )
    expect(screen.getByRole('link', { name: 'Camera Configuration' })).toBeInTheDocument()

    const hrefs = screen.getAllByRole('link').map((link) => link.getAttribute('href'))
    expect(hrefs).toEqual([
      '/',
      '/notifications',
      '/environment',
      '/cameras',
      '/audio-alerts',
      '/environment-config',
      '/settings',
      '/system-settings',
    ])
    // Camera rows are reached from the list, not the menu.
    expect(screen.queryByRole('link', { name: /Nursery|Camera 2/ })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Environment Configuration/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'System Settings' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /More/ })).not.toBeInTheDocument()
  })

  it('keeps Camera Configuration active inside a camera page', () => {
    renderWithProviders(<SideNavigation />, { route: '/cameras/1' })

    expect(screen.getByRole('link', { name: 'Camera Configuration' })).toHaveAttribute(
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
    expect(screen.getByRole('link', { name: /Live Stream/ })).not.toHaveAttribute('aria-current')
  })
})

describe('BottomNavigation', () => {
  it('keeps four daily tabs and marks the current one', () => {
    renderWithProviders(<BottomNavigation />, { route: '/settings' })

    expect(screen.getByRole('link', { name: /Settings/ })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: /Live Stream/ })).not.toHaveAttribute('aria-current')
    // The phone bar has no More tab; the rest is reachable through Settings.
    expect(screen.queryByRole('link', { name: /More/ })).not.toBeInTheDocument()
    expect(screen.getAllByRole('link')).toHaveLength(4)
  })

  it('keeps Cameras active on a camera page', () => {
    renderWithProviders(<BottomNavigation />, { route: '/cameras/2' })

    expect(screen.getByRole('link', { name: /Cameras/ })).toHaveAttribute('aria-current', 'page')
  })
})
