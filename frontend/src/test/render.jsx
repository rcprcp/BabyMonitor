import { MantineProvider } from '@mantine/core'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { createAppTheme } from '../theme'

// Render a page with the same providers the app supplies: Mantine (with the
// account's palette) and a router, because several pages use useNavigate or
// NavLink. AuthProvider is deliberately omitted: tests mock ../auth directly,
// which keeps each page test independent of the login flow.
export function renderWithProviders(ui, { route = '/' } = {}) {
  return render(
    <MemoryRouter
      initialEntries={[route]}
      future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
    >
      <MantineProvider theme={createAppTheme('blue')}>{ui}</MantineProvider>
    </MemoryRouter>,
  )
}
