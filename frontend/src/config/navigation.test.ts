import { describe, expect, it } from 'vitest'
import type { RouteLocationRaw } from 'vue-router'

import { router } from '@/router'
import { USER_ROLES, type UserRole } from '@/types/api'
import { defaultRouteForRole } from '@/utils/role'

import { activeNavItem, isNavItemActive, publicNav, roleNavigation } from './navigation'

const EXPECTED_SIDEBARS = {
  STUDENT: [
    '/student/home',
    '/student/learn',
    '/student/practice',
    '/student/ai',
    '/student/classes',
    '/student/progress',
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
    expect(publicNav.every((item) => item.labelKey.startsWith('nav.'))).toBe(true)
  })

  it('gives each role the sidebar of the information architecture, starting with its home', () => {
    for (const role of USER_ROLES) {
      const paths = roleNavigation[role].map((item) => item.to)
      expect(paths).toEqual(EXPECTED_SIDEBARS[role])
      expect(paths[0]).toBe(defaultRouteForRole(role))
      expect(roleNavigation[role].every((item) => item.labelKey.startsWith('nav.') && item.icon.trim())).toBe(true)
    }
  })

  it('links every sidebar, header and status-card item to a real page the role may open', () => {
    for (const role of USER_ROLES) {
      const home = role === 'STUDENT' ? 'student' : 'admin'
      const links = [
        ...roleNavigation[role].map((item) => item.to),
        // The header's bell and account chip, and the learner's status card.
        `/${home}/notifications`,
        `/${home}/account`,
        ...(role === 'STUDENT' ? ['/student/progress'] : []),
      ]
      for (const link of links) {
        const resolved = router.resolve(link)
        expect(resolved.name, link).not.toBe('not-found')
        expect(resolved.meta.roles, link).toEqual([role])
      }
    }
  })

  it('opens the AI Helper on Talk, because its sidebar link has no tab of its own', () => {
    const sectionRoot = router.getRoutes().find((record) => record.path === '/student/ai')
    expect(router.resolve(sectionRoot?.redirect as RouteLocationRaw).path).toBe('/student/ai/talk')
  })

  it('has no Help & support, teacher or live-session pages for learners any more', () => {
    for (const gone of ['/student/support', '/student/support/1038', '/student/classes/live', '/teacher/dashboard']) {
      expect(router.resolve(gone).name, gone).toBe('not-found')
    }
    expect(router.resolve('/student/practice/quizzes').name).toBe('not-found')
  })

  it('highlights a nav item on its page, its subpages and only its own hash section', () => {
    const at = (path: string, hash = '') => ({ path, hash })

    expect(isNavItemActive('/#how-it-works', at('/', '#how-it-works'))).toBe(true)
    expect(isNavItemActive('/#how-it-works', at('/'))).toBe(false)
    expect(isNavItemActive('/classes', at('/classes/12'))).toBe(true)
    expect(isNavItemActive('/student/learn', at('/student/learning'))).toBe(false)
    expect(isNavItemActive('/student/home', at('/student/learn'))).toBe(false)
  })

  it('finds the section a nested page belongs to, for the header title', () => {
    const at = (path: string) => ({ path, hash: '' })
    expect(activeNavItem('STUDENT', at('/student/classes/7/lessons/3'))?.to).toBe('/student/classes')
    expect(activeNavItem('STUDENT', at('/student/quiz/3/attempts/5'))?.to).toBe('/student/classes')
    expect(activeNavItem('STUDENT', at('/student/progress/plan'))?.to).toBe('/student/progress')
    expect(activeNavItem('ADMIN', at('/admin/content/learning-path'))?.to).toBe('/admin/content')
    expect(activeNavItem('ADMIN', at('/admin/content/classes/4/lessons/9/quiz'))?.to).toBe('/admin/content')
    expect(activeNavItem('ADMIN', at('/admin/notifications'))).toBeUndefined()
  })
})
