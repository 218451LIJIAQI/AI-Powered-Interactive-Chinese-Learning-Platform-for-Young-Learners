import { afterEach, describe, expect, it, vi } from 'vitest'

import { useNumericRouteParam, useStringRouteParam } from './useRouteParam'

// Shared mutable route state — each test mutates `mockRoute.params` before
// calling the composable. We re-create the mock inside each test to make sure
// `computed(() => route.params[name])` sees the fresh params value.
const mockRoute = { params: {} as Record<string, string | string[]> }

vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
}))

afterEach(() => {
  mockRoute.params = {}
})

describe('useStringRouteParam', () => {
  it('returns the string param value when present', () => {
    mockRoute.params = { id: 'abc-123' }
    expect(useStringRouteParam('id').value).toBe('abc-123')
  })

  it('flattens the string[] form (catch-all routes) to its first entry', () => {
    mockRoute.params = { pathMatch: ['admin', 'users'] }
    expect(useStringRouteParam('pathMatch').value).toBe('admin')
  })

  it('returns empty string when param is missing or empty', () => {
    expect(useStringRouteParam('missing').value).toBe('')

    mockRoute.params = { id: '' }
    expect(useStringRouteParam('id').value).toBe('')

    mockRoute.params = { id: [] }
    expect(useStringRouteParam('id').value).toBe('')
  })
})

describe('useNumericRouteParam', () => {
  it('parses numeric string params to a finite number', () => {
    mockRoute.params = { lessonId: '42' }
    const value = useNumericRouteParam('lessonId').value
    expect(value).toBe(42)
    expect(Number.isFinite(value)).toBe(true)
  })

  it('returns NaN for missing params so callers can guard with Number.isFinite', () => {
    expect(Number.isNaN(useNumericRouteParam('missing').value)).toBe(true)
  })

  it('returns NaN for empty/whitespace params', () => {
    mockRoute.params = { id: '   ' }
    expect(Number.isNaN(useNumericRouteParam('id').value)).toBe(true)
  })

  it('returns NaN for non-numeric params (prevents /lessons/NaN API calls)', () => {
    mockRoute.params = { id: 'abc' }
    expect(Number.isNaN(useNumericRouteParam('id').value)).toBe(true)
  })

  it('trims leading/trailing whitespace before parsing', () => {
    mockRoute.params = { id: '  7 ' }
    expect(useNumericRouteParam('id').value).toBe(7)
  })
})
