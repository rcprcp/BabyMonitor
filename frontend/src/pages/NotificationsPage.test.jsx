import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import NotificationsPage from './NotificationsPage'
import { renderWithProviders } from '../test/render'

const { api } = vi.hoisted(() => ({
  api: {
    listNotifications: vi.fn(),
    dismissNotification: vi.fn(),
    dismissAllNotifications: vi.fn(),
  },
}))

vi.mock('../api', () => ({ api }))

const now = '2026-10-28T12:00:00Z'
const rows = [
  {
    id: 2,
    eventType: 'Audio',
    description: 'Baby cried',
    eventTime: now,
    videoLink: null,
  },
  {
    id: 1,
    eventType: 'Video',
    description: 'Motion detected',
    eventTime: now,
    videoLink: '/api/cameras/1/recordings/clip.mp4',
  },
]

beforeEach(() => {
  api.listNotifications.mockResolvedValue(rows)
  api.dismissNotification.mockResolvedValue(null)
  api.dismissAllNotifications.mockResolvedValue(null)
})

describe('NotificationsPage', () => {
  it('renders the feed returned by the API', async () => {
    renderWithProviders(<NotificationsPage />)

    expect(await screen.findByRole('heading', { name: 'Notifications' })).toBeInTheDocument()
    expect(await screen.findByText('Baby cried')).toBeInTheDocument()
    expect(screen.getByText('Motion detected')).toBeInTheDocument()
  })

  it('reloads the feed when Refresh is clicked', async () => {
    const user = userEvent.setup()
    renderWithProviders(<NotificationsPage />)

    await screen.findByText('Baby cried')
    await user.click(screen.getByRole('button', { name: 'Refresh' }))

    expect(api.listNotifications).toHaveBeenCalledTimes(2)
  })

  it('shows an error when the feed cannot be loaded', async () => {
    api.listNotifications.mockRejectedValue(new Error('Feed unavailable'))
    renderWithProviders(<NotificationsPage />)

    expect(await screen.findByText('Feed unavailable')).toBeInTheDocument()
  })
})
