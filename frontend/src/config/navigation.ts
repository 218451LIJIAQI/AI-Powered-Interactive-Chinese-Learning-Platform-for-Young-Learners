import type { NavCounts, UserRole } from '@/types/api'

export interface RoleNavItem {
  readonly labelKey: string
  readonly to: string
  readonly icon: string
  /** Key in the nav-count response (`GET /users/me/nav-counts`) whose value is shown as a badge. */
  readonly countKey?: keyof NavCounts
}

/** Links in the public header. */
export const publicNav = [
  { labelKey: 'nav.classes', to: '/classes' },
  { labelKey: 'nav.howItWorks', to: '/#how-it-works' },
  { labelKey: 'nav.help', to: '/help' },
] as const

/**
 * One flat sidebar per role, in display order (the prototype's information architecture): section roots only.
 * Notifications and Account live in the topbar (bell and user chip). Learners have no Help & support entry: they
 * report a problem where it happens, and support answers by e-mail.
 */
export const roleNavigation = {
  STUDENT: [
    { labelKey: 'nav.home', to: '/student/home', icon: 'home' },
    { labelKey: 'nav.learn', to: '/student/learn', icon: 'learn' },
    { labelKey: 'nav.practice', to: '/student/practice', icon: 'practice', countKey: 'mistakes' },
    { labelKey: 'nav.aiHelper', to: '/student/ai', icon: 'ai' },
    { labelKey: 'nav.classes', to: '/student/classes', icon: 'classes' },
    { labelKey: 'nav.progress', to: '/student/progress', icon: 'progress' },
  ],
  ADMIN: [
    { labelKey: 'nav.dashboard', to: '/admin/dashboard', icon: 'dashboard' },
    { labelKey: 'nav.users', to: '/admin/users', icon: 'users' },
    { labelKey: 'nav.content', to: '/admin/content', icon: 'content' },
    { labelKey: 'nav.ai', to: '/admin/ai', icon: 'ai', countKey: 'aiReports' },
    { labelKey: 'nav.support', to: '/admin/support', icon: 'help', countKey: 'supportOpen' },
    { labelKey: 'nav.analytics', to: '/admin/analytics', icon: 'analytics' },
    { labelKey: 'nav.auditLog', to: '/admin/audit-log', icon: 'audit' },
  ],
} as const satisfies Record<UserRole, readonly RoleNavItem[]>

/**
 * Nav items are plain paths; public items may add a hash, as in /#how-it-works.
 * A hash item is active only on that section, any other item also on its subpages and whatever query the page adds.
 */
export function isNavItemActive(to: string, route: { readonly path: string; readonly hash: string }): boolean {
  const hashIndex = to.indexOf('#')
  if (hashIndex >= 0) return route.path === to.slice(0, hashIndex) && route.hash === to.slice(hashIndex)
  if (to === '/student/classes' && route.path.startsWith('/student/quiz/')) return true
  return route.path === to || route.path.startsWith(`${to}/`)
}

/** The sidebar item a route belongs to (its section), which the header names. */
export function activeNavItem(
  role: UserRole,
  route: { readonly path: string; readonly hash: string },
): RoleNavItem | undefined {
  return roleNavigation[role].find((item) => isNavItemActive(item.to, route))
}
