import { afterEach, describe, expect, it, vi } from 'vitest'

import { useRoleBasePath } from './useRoleBasePath'

// Mutable route state mutated per-test before calling the composable.
const mockRoute = { path: '/' }

vi.mock('vue-router', () => ({
  useRoute: () => mockRoute,
}))

afterEach(() => {
  mockRoute.path = '/'
})

describe('useRoleBasePath', () => {
  it('returns /student for any path under /student/*', () => {
    mockRoute.path = '/student/support-tickets/3'
    expect(useRoleBasePath().value).toBe('/student')
  })

  it('returns /teacher for any path under /teacher/*', () => {
    mockRoute.path = '/teacher/notifications'
    expect(useRoleBasePath().value).toBe('/teacher')
  })

  it('returns /admin for any path under /admin/*', () => {
    mockRoute.path = '/admin/support-tickets/12'
    expect(useRoleBasePath().value).toBe('/admin')
  })

  it('returns an empty string for non-role-scoped top-level paths', () => {
    mockRoute.path = '/support-tickets'
    expect(useRoleBasePath().value).toBe('')

    mockRoute.path = '/'
    expect(useRoleBasePath().value).toBe('')
  })

  it('does not match bare /student /teacher /admin (must have a trailing slash)', () => {
    // This guards the startsWith('/student/') contract so a page at exactly
    // '/student' (unlikely, but defensive) does not falsely claim a base path.
    mockRoute.path = '/student'
    expect(useRoleBasePath().value).toBe('')
  })
})
