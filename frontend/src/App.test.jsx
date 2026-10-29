import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import App from './App'

const { api } = vi.hoisted(() => ({
  api: {
    getSetting: vi.fn(),
    listCameras: vi.fn(),
    listSettings: vi.fn(),
    getProfile: vi.fn(),
    updateActiveCamera: vi.fn(),
    getCameraAudio: vi.fn(),
    listNotifications: vi.fn(),
    listVideoDevices: vi.fn(),
  },
}))

// Keep the real auth/session helpers (they read localStorage) and replace only
// the network calls the shell makes.
vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, api }
})

beforeEach(() => {
  localStorage.setItem('babymonitor.token', 'test-token')
  localStorage.setItem('babymonitor.name', 'Bob')

  // The shell asks for the system name and the first-run marker separately.
  api.getSetting.mockImplementation((tag) =>
    Promise.resolve({
      tag,
      value: tag === 'onboardingComplete' ? 'true' : 'Nursery Monitor',
    }),
  )
  api.listCameras.mockResolvedValue([
    { cameraNumber: 1, key: 'camera_name', value: 'Nursery', enabled: true },
    { cameraNumber: 1, key: 'stream_port', value: '8081', enabled: true },
    { cameraNumber: 2, key: 'stream_port', value: '8082', enabled: true },
  ])
  api.listSettings.mockResolvedValue([{ tag: 'stream_url', value: 'http://localhost' }])
  api.getProfile.mockResolvedValue({ activeCamera: 1 })
  api.updateActiveCamera.mockResolvedValue({})
  api.getCameraAudio.mockResolvedValue({ running: false, enabled: false })
  api.listNotifications.mockResolvedValue([])
  api.listVideoDevices.mockResolvedValue([{ device: '/dev/video0', name: 'Integrated Camera' }])
})

afterEach(() => {
  localStorage.clear()
  window.history.replaceState({}, '', '/')
})

// The alert stream opens an EventSource; jsdom has none and the shell only
// needs the object to exist and close cleanly.
vi.stubGlobal(
  'EventSource',
  class {
    close() {}
    addEventListener() {}
  },
)

describe('App shell', () => {
  it('puts every destination in the sidebar and drops the More hub', async () => {
    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Live stream' })).toBeInTheDocument()
    expect((await screen.findAllByRole('link', { name: /Monitor/ })).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: /Alerts/ }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: /Environment/ }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: /Cameras/ }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: /Audio Configuration/ }).length).toBeGreaterThan(0)
    expect(
      screen.getAllByRole('link', { name: /Environment Configuration/ }).length,
    ).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: /Settings/ }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: /System Settings/ }).length).toBeGreaterThan(0)
    expect(screen.queryByRole('link', { name: /More/ })).not.toBeInTheDocument()
  })

  it('sends a first run to the setup wizard', async () => {
    api.getSetting.mockImplementation((tag) =>
      Promise.resolve({ tag, value: tag === 'onboardingComplete' ? 'false' : 'Nursery Monitor' }),
    )

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Set up your monitor' })).toBeInTheDocument()
  })

  it('redirects the retired More hub to Settings', async () => {
    window.history.pushState({}, '', '/more')

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeInTheDocument()
  })

  it('redirects the retired profile URL to Settings', async () => {
    window.history.pushState({}, '', '/profile')

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeInTheDocument()
  })

  it('serves the camera list on a deep link', async () => {
    window.history.pushState({}, '', '/cameras')

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Cameras' })).toBeInTheDocument()
    // Both the sidebar and the camera card link to the camera page.
    const links = await screen.findAllByRole('link', { name: /Nursery/ })
    expect(links.some((link) => link.getAttribute('href') === '/cameras/1')).toBe(true)
  })
})
