import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MorePage from './MorePage'
import { renderWithProviders } from '../test/render'

const { logout } = vi.hoisted(() => ({ logout: vi.fn() }))

vi.mock('../auth', () => ({
  useAuth: () => ({ user: 'Bob', logout }),
}))

beforeEach(() => {
  logout.mockResolvedValue(null)
})

describe('MorePage', () => {
  it('links to every secondary destination', () => {
    renderWithProviders(<MorePage />)

    expect(screen.getByRole('heading', { name: 'More' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Environment/ })).toHaveAttribute(
      'href',
      '/environment',
    )
    expect(screen.getByRole('link', { name: /Audio notifications/ })).toHaveAttribute(
      'href',
      '/audio-alerts',
    )
    expect(screen.getByRole('link', { name: /Settings/ })).toHaveAttribute('href', '/settings')
    expect(screen.getByRole('link', { name: /Personal settings/ })).toHaveAttribute(
      'href',
      '/profile',
    )
  })

  it('signs out from the account', async () => {
    const user = userEvent.setup()
    renderWithProviders(<MorePage />)

    await user.click(screen.getByRole('button', { name: 'Sign out (Bob)' }))

    expect(logout).toHaveBeenCalledTimes(1)
  })
})
