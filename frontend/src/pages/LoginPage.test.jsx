import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import LoginPage from './LoginPage'
import { renderWithProviders } from '../test/render'

// AuthProvider is not part of this smoke test: the page only needs useAuth.
const { login } = vi.hoisted(() => ({ login: vi.fn() }))

vi.mock('../auth', () => ({
  useAuth: () => ({ login, isAuthenticated: false }),
}))

beforeEach(() => {
  login.mockReset()
})

describe('LoginPage', () => {
  it('renders the sign-in form', () => {
    renderWithProviders(<LoginPage />, { route: '/login' })

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByLabelText(/Name/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Password/)).toBeInTheDocument()
  })

  it('signs in with the entered credentials', async () => {
    login.mockResolvedValue({})
    const user = userEvent.setup()
    renderWithProviders(<LoginPage />, { route: '/login' })

    await user.type(screen.getByLabelText(/^Name/), 'Bob')
    await user.type(screen.getByLabelText(/^Password/), 'secret')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(login).toHaveBeenCalledWith('Bob', 'secret')
  })

  it('shows the server error when sign-in fails', async () => {
    login.mockRejectedValue(new Error('Wrong name or password'))
    const user = userEvent.setup()
    renderWithProviders(<LoginPage />, { route: '/login' })

    await user.type(screen.getByLabelText(/^Name/), 'Bob')
    await user.type(screen.getByLabelText(/^Password/), 'nope')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Wrong name or password')).toBeInTheDocument()
  })
})
