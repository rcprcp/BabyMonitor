import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// RTL normally registers this itself when `globals` is on; being explicit keeps
// the suite correct if a future test file opts out of globals.
afterEach(() => {
  cleanup()
})

// Mantine's useMediaQuery and several internals read matchMedia.
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
})

// jsdom has neither observer. recharts' ResponsiveContainer needs a size, and
// Mantine's overlays observe their triggers.
vi.stubGlobal(
  'ResizeObserver',
  class {
    observe() {}
    unobserve() {}
    disconnect() {}
  },
)
vi.stubGlobal(
  'IntersectionObserver',
  class {
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords() {
      return []
    }
  },
)

// jsdom reports every element as 0x0, which makes ResponsiveContainer skip its
// children. A fixed size lets the Environment chart render for the smoke test.
Element.prototype.getBoundingClientRect = () => ({
  width: 800,
  height: 600,
  top: 0,
  left: 0,
  right: 800,
  bottom: 600,
  x: 0,
  y: 0,
  toJSON: () => ({}),
})
Element.prototype.scrollIntoView = () => {}
window.scrollTo = () => {}

// No audio output in jsdom. The calls still resolve so previews and alerts
// cannot reject into the UI.
window.HTMLMediaElement.prototype.play = () => Promise.resolve()
window.HTMLMediaElement.prototype.pause = () => {}

// A no-op animation frame keeps LevelMeter's render loop from spinning forever
// during a test; the meter itself is not under test here.
vi.stubGlobal('requestAnimationFrame', () => 0)
vi.stubGlobal('cancelAnimationFrame', () => {})
