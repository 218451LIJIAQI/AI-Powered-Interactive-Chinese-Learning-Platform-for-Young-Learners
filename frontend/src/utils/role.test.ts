import { describe, expect, it } from 'vitest'

import { aiHelperEntryRoute, defaultRouteForRole } from './role'

describe('defaultRouteForRole', () => {
  it.each([
    ['STUDENT', '/student/home'],
    ['TEACHER', '/teacher/dashboard'],
    ['ADMIN', '/admin/dashboard'],
    [null, '/'],
  ] as const)('routes %s to %s', (role, route) => {
    expect(defaultRouteForRole(role)).toBe(route)
  })
})

describe('aiHelperEntryRoute', () => {
  it('opens the AI helper for a student and asks anyone else to sign in first', () => {
    expect(aiHelperEntryRoute(true)).toBe('/student/ai')
    expect(aiHelperEntryRoute(false)).toEqual({ name: 'auth', query: { redirect: '/student/ai' } })
  })
})
