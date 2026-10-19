import { describe, expect, it } from 'vitest'

import type { UserRole } from '@/types/api'
import { defaultRouteForRole } from '@/utils/role'

import { publicNav, roleNavigation, roleNavigationGroups } from './navigation'
import type { NavGroup, NavItem, RoleNavItem } from './navigation'

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
    expect(publicNav.map((item) => item.to)).toEqual(['/', '/courses', '/#about', '/responsible-ai'])
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

  it('assigns every sidebar item to a declared group and leaves no group empty', () => {
    ;(Object.keys(roleNavigation) as UserRole[]).forEach((role) => {
      const items: readonly RoleNavItem[] = roleNavigation[role]
      const groups: readonly NavGroup[] = roleNavigationGroups[role]
      const groupKeys = groups.map((group) => group.key)

      expect(new Set(groupKeys).size).toBe(groupKeys.length)
      expect(items.every((item) => groupKeys.includes(item.group))).toBe(true)
      expect(groupKeys.every((key) => items.some((item) => item.group === key))).toBe(true)
    })
  })

  it('exposes the W3 teaching and review surfaces in the matching role groups', () => {
    expect(roleNavigation.TEACHER).toContainEqual(
      expect.objectContaining({ to: '/teacher/activities', labelKey: 'nav.practiceActivities', group: 'teaching' }),
    )
    expect(roleNavigation.ADMIN).toContainEqual(
      expect.objectContaining({ to: '/admin/content-review', labelKey: 'nav.contentReview', group: 'trust' }),
    )
  })

  it('gives every role a notifications page and an account page (D12, D20)', () => {
    for (const role of ['STUDENT', 'TEACHER', 'ADMIN'] as const) {
      const prefix = role.toLowerCase()
      const paths = roleNavigation[role].map((item) => item.to)
      expect(paths).toContain(`/${prefix}/notifications`)
      expect(paths).toContain(`/${prefix}/account`)
    }
    expect(roleNavigation.STUDENT.map((item) => item.to)).toEqual(
      expect.arrayContaining(['/student/progress', '/student/practice-submissions']),
    )
  })

  it('links sidebar items to plain route paths without unused query sections', () => {
    const paths = Object.values(roleNavigation).flatMap((items) => items.map((item) => item.to))

    expect(paths.some((path) => path.includes('?'))).toBe(false)
  })
})
