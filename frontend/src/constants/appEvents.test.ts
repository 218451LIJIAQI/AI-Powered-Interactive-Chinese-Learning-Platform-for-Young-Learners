import { afterEach, describe, expect, it, vi } from 'vitest'

import { APP_EVENTS, dispatchAppEvent } from './appEvents'

describe('APP_EVENTS', () => {
  it('exposes a stable event name for notifications:refresh', () => {
    expect(APP_EVENTS.NOTIFICATIONS_REFRESH).toBe('notifications:refresh')
  })

  it('exposes a stable event name for app:forbidden', () => {
    expect(APP_EVENTS.FORBIDDEN).toBe('app:forbidden')
  })
})

describe('dispatchAppEvent', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('dispatches a CustomEvent with the requested name on window', () => {
    const spy = vi.spyOn(window, 'dispatchEvent')
    dispatchAppEvent(APP_EVENTS.NOTIFICATIONS_REFRESH)
    expect(spy).toHaveBeenCalled()
    const [event] = spy.mock.calls[0] as [Event]
    expect(event.type).toBe('notifications:refresh')
  })

  it('attaches the optional detail when provided', () => {
    const spy = vi.spyOn(window, 'dispatchEvent')
    dispatchAppEvent(APP_EVENTS.FORBIDDEN, 'Forbidden')
    expect(spy).toHaveBeenCalled()
    const [event] = spy.mock.calls[0] as [CustomEvent<string>]
    expect(event.type).toBe('app:forbidden')
    expect(event.detail).toBe('Forbidden')
  })

  it('omits the detail when no value is passed', () => {
    const spy = vi.spyOn(window, 'dispatchEvent')
    dispatchAppEvent(APP_EVENTS.NOTIFICATIONS_REFRESH)
    const [event] = spy.mock.calls[0] as [CustomEvent<unknown>]
    // Browsers default `CustomEvent.detail` to `null` when no init was supplied.
    expect(event.detail).toBeNull()
  })
})
