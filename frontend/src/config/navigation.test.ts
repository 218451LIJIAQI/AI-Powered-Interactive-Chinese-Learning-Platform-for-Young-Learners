import { describe, expect, it } from 'vitest'

import type { UserRole } from '@/types/api'
import { defaultRouteForRole } from '@/utils/role'

import { activeNavItem, isNavItemActive, publicNav, roleNavigation, roleSupportNav } from './navigation'
import type { RoleNavItem } from './navigation'

const EXPECTED_SIDEBARS = {
  STUDENT: [
    '/student/home',
    '/student/learn',
    '/student/practice',
    '/student/ai',
    '/student/classes',
    '/student/progress',
  ],
  TEACHER: [
    '/teacher/dashboard',
    '/teacher/classes',
    '/teacher/live-sessions',
    '/teacher/students',
    '/teacher/profile',
  ],
  ADMIN: [
    '/admin/dashboard',
    '/admin/users',
    '/admin/content',
    '/admin/ai',
    '/admin/support',
    '/admin/analytics',
    '/admin/audit-log',
  ],
} as const satisfies Record<UserRole, readonly string[]>

describe('navigation configuration', () => {
  it('keeps public navigation anchored to first-party routes', () => {
    expect(publicNav.map((item) => item.to)).toEqual(['/classes', '/#how-it-works', '/help'])
    expect(publicNav.every((item) => item.labelKey.startsWith('nav.') && item.icon)).toBe(true)
  })

  it('gives each role the sidebar of the information architecture, starting with its home', () => {
    ;(Object.keys(EXPECTED_SIDEBARS) as UserRole[]).forEach((role) => {
      const paths = roleNavigation[role].map((item) => item.to)
      expect(paths).toEqual(EXPECTED_SIDEBARS[role])
      expect(paths[0]).toBe(defaultRouteForRole(role))
      expect(roleNavigation[role].every((item) => item.labelKey.startsWith('nav.') && item.icon.trim())).toBe(true)
    })
  })

  it('locks only the teaching tools until a teacher is approved', () => {
    const locked = (roleNavigation.TEACHER as readonly RoleNavItem[]).filter((item) => item.needsApproval)
    expect(locked.map((item) => item.to)).toEqual(['/teacher/classes', '/teacher/live-sessions', '/teacher/students'])
  })

  it('gives students and teachers a Help & support link; admins answer support from their own section', () => {
    expect(roleSupportNav.STUDENT?.to).toBe('/student/support')
    expect(roleSupportNav.TEACHER?.to).toBe('/teacher/support')
    expect(roleSupportNav.ADMIN).toBeUndefined()
  })

  it('links sidebar items to plain route paths without a query', () => {
    const paths = Object.values(roleNavigation).flatMap((items) => items.map((item) => item.to))
    expect(paths.some((path) => path.includes('?'))).toBe(false)
  })

  it('highlights a nav item on its page, its subpages and only its own hash section', () => {
    const at = (path: string, hash = '') => ({ path, hash })

    expect(isNavItemActive('/', at('/'))).toBe(true)
    expect(isNavItemActive('/', at('/', '#how-it-works'))).toBe(false)
    expect(isNavItemActive('/#how-it-works', at('/', '#how-it-works'))).toBe(true)
    expect(isNavItemActive('/classes', at('/classes/12'))).toBe(true)
    expect(isNavItemActive('/student/learn', at('/student/learning'))).toBe(false)
    expect(isNavItemActive('/student/home', at('/student/learn'))).toBe(false)
  })

  it('finds the section a nested page belongs to, for the header title', () => {
    const at = (path: string) => ({ path, hash: '' })
    expect(activeNavItem('STUDENT', at('/student/classes/7/lessons/3'))?.to).toBe('/student/classes')
    expect(activeNavItem('STUDENT', at('/student/progress/plan'))?.to).toBe('/student/progress')
    expect(activeNavItem('STUDENT', at('/student/support/1038'))?.to).toBe('/student/support')
    expect(activeNavItem('ADMIN', at('/admin/content/learning-path'))?.to).toBe('/admin/content')
    expect(activeNavItem('ADMIN', at('/admin/notifications'))).toBeUndefined()
  })
})
