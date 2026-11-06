import { describe, expect, it } from 'vitest'

import { defaultRouteForRole } from './role'

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
