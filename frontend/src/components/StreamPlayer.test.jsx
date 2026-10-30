import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, fireEvent, screen } from '@testing-library/react'
import StreamPlayer from './StreamPlayer'
import { renderWithProviders } from '../test/render'

describe('StreamPlayer', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows a live status, the last frame and a manual Retry control', () => {
    renderWithProviders(<StreamPlayer url="http://camera:8081" />)

    const stream = screen.getByAltText('Live baby monitor stream')
    fireEvent.load(stream)

    expect(screen.getByText('Live')).toBeInTheDocument()
    expect(screen.getByText(/Last frame .* ago|just now/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('reloads the image immediately when Retry is pressed', () => {
    renderWithProviders(<StreamPlayer url="http://camera:8081" />)

    fireEvent.load(screen.getByAltText('Live baby monitor stream'))
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))

    const retried = screen.getByAltText('Live baby monitor stream')
    expect(retried).toHaveAttribute('src', expect.stringContaining('_cache='))
    expect(screen.getAllByText('Connecting…')).toHaveLength(2)

    fireEvent.load(retried)
    expect(screen.getByText('Live')).toBeInTheDocument()
  })

  it('marks a silent stream stalled and reconnects it after 15 seconds', () => {
    vi.useFakeTimers()
    renderWithProviders(<StreamPlayer url="http://camera:8081" />)

    fireEvent.load(screen.getByAltText('Live baby monitor stream'))
    expect(screen.getByText('Live')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(15_000)
    })

    expect(screen.getAllByText('Stream stalled')).toHaveLength(2)
    expect(screen.getByText(/No new frame for 15s/)).toBeInTheDocument()
    const reconnected = screen.getByAltText('Live baby monitor stream')
    expect(reconnected).toHaveAttribute('src', expect.stringContaining('_cache='))

    fireEvent.load(reconnected)
    expect(screen.getByText('Live')).toBeInTheDocument()
  })
})
