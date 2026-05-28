import { describe, expect, it } from 'vitest'

import { defaultRouteForRole } from './role'

describe('defaultRouteForRole', () => {
  it('routes each persona to its dashboard and anonymous users to public home', () => {
    expect(defaultRouteForRole('STUDENT')).toBe('/student/home')
    expect(defaultRouteForRole('TEACHER')).toBe('/teacher/dashboard')
    expect(defaultRouteForRole('ADMIN')).toBe('/admin/dashboard')
    expect(defaultRouteForRole(null)).toBe('/')
  })
})
