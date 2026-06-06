import { describe, expect, it } from 'vitest'

import type { UserRole } from '@/types/api'
import { defaultRouteForRole } from '@/utils/role'

import { publicNav, roleNavigation } from './navigation'
import type { NavItem } from './navigation'

const EXPECTED_DEFAULT_ROUTES = {
  STUDENT: '/student/home',
  TEACHER: '/teacher/dashboard',
  ADMIN: '/admin/dashboard',
} as const satisfies Record<UserRole, string>

function roleEntries(record: Record<UserRole, readonly NavItem[]>): Array<[UserRole, readonly NavItem[]]> {
  return Object.entries(record) as Array<[UserRole, readonly NavItem[]]>
}

describe('navigation configuration', () => {
  it('keeps public navigation anchored to safe first-party routes', () => {
    expect(publicNav.map((item) => item.to)).toEqual(['/', '/courses', '/#about', '/#safety'])
    expect(publicNav.every((item) => item.labelKey.startsWith('nav.') && item.icon)).toBe(true)
  })

  it('provides each role with a unique sidebar and matching default landing route', () => {
    roleEntries(roleNavigation).forEach(([role, items]) => {
      const paths = items.map((item) => item.to)
      const defaultRoute = EXPECTED_DEFAULT_ROUTES[role]

      expect(paths).toContain(defaultRoute)
      expect(defaultRouteForRole(role)).toBe(defaultRoute)
      expect(new Set(paths).size).toBe(paths.length)
      expect(items.every((item) => item.labelKey.startsWith('nav.') && item.icon.trim())).toBe(true)
    })
  })
})
