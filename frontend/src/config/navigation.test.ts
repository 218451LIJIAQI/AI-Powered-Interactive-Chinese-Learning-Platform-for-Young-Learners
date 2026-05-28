import { describe, expect, it } from 'vitest'

import { defaultRouteForRole } from '@/utils/role'

import { publicNav, roleNavigation } from './navigation'

describe('navigation configuration', () => {
  it('keeps public navigation anchored to safe first-party routes', () => {
    expect(publicNav.map((item) => item.to)).toEqual(['/', '/courses', '/#about', '/#safety'])
    expect(publicNav.every((item) => item.labelKey.startsWith('nav.') && item.icon)).toBe(true)
  })

  it('provides each role with a unique sidebar and matching default landing route', () => {
    const expectedDefaults = {
      STUDENT: '/student/home',
      TEACHER: '/teacher/dashboard',
      ADMIN: '/admin/dashboard',
    } as const

    Object.entries(roleNavigation).forEach(([role, items]) => {
      const paths = items.map((item) => item.to)

      expect(paths).toContain(expectedDefaults[role as keyof typeof expectedDefaults])
      expect(defaultRouteForRole(role as keyof typeof expectedDefaults)).toBe(
        expectedDefaults[role as keyof typeof expectedDefaults],
      )
      expect(new Set(paths).size).toBe(paths.length)
      expect(items.every((item) => item.labelKey.startsWith('nav.') && item.icon.trim())).toBe(true)
    })
  })
})
