import type { UserRole } from '@/types/api'

interface NavItem {
  readonly labelKey: string
  readonly to: string
  readonly icon: string
}

export interface RoleNavItem extends NavItem {
  /** Key in the nav-count response (`GET /users/me/nav-counts`) whose value is shown as a badge. */
  readonly countKey?: string
  /** Teacher tools that stay locked (and open the Locked page) until an admin approves the teacher. */
  readonly needsApproval?: boolean
}

/** Links in the public header (and the matching footer column). */
export const publicNav = [
  { labelKey: 'nav.classes', to: '/classes', icon: 'classes' },
  { labelKey: 'nav.howItWorks', to: '/#how-it-works', icon: 'learn' },
  { labelKey: 'nav.help', to: '/help', icon: 'help' },
] as const satisfies readonly NavItem[]

/**
 * One flat sidebar per role, in display order (the prototype's information architecture). Notifications and Account
 * live in the topbar (bell and user chip); Help & support sits under the list (see `roleSupportNav`).
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
  TEACHER: [
    { labelKey: 'nav.dashboard', to: '/teacher/dashboard', icon: 'dashboard' },
    { labelKey: 'nav.classes', to: '/teacher/classes', icon: 'classes', needsApproval: true },
    { labelKey: 'nav.liveSessions', to: '/teacher/live-sessions', icon: 'live', needsApproval: true },
    { labelKey: 'nav.students', to: '/teacher/students', icon: 'students', needsApproval: true },
    { labelKey: 'nav.teachingProfile', to: '/teacher/profile', icon: 'profile' },
  ],
  ADMIN: [
    { labelKey: 'nav.dashboard', to: '/admin/dashboard', icon: 'dashboard' },
    { labelKey: 'nav.users', to: '/admin/users', icon: 'users', countKey: 'teacherApplications' },
    { labelKey: 'nav.content', to: '/admin/content', icon: 'content', countKey: 'reviewQueue' },
    { labelKey: 'nav.ai', to: '/admin/ai', icon: 'ai', countKey: 'aiReports' },
    { labelKey: 'nav.support', to: '/admin/support', icon: 'help', countKey: 'supportOpen' },
    { labelKey: 'nav.analytics', to: '/admin/analytics', icon: 'analytics' },
    { labelKey: 'nav.auditLog', to: '/admin/audit-log', icon: 'audit' },
  ],
} as const satisfies Record<UserRole, readonly RoleNavItem[]>

/** The Help & support link under the sidebar list (admins answer support in their own Support section instead). */
export const roleSupportNav: Partial<Record<UserRole, RoleNavItem>> = {
  STUDENT: { labelKey: 'nav.helpSupport', to: '/student/support', icon: 'help', countKey: 'supportReplies' },
  TEACHER: { labelKey: 'nav.helpSupport', to: '/teacher/support', icon: 'help', countKey: 'supportReplies' },
}

/**
 * Nav items are plain paths (navigation.test.ts rules out a query); public items may add a hash, as in /#how-it-works.
 * A hash item is active only on that section, Home only without a hash, and any other item also on its subpages
 * and whatever query the page adds.
 */
export function isNavItemActive(to: string, route: { readonly path: string; readonly hash: string }): boolean {
  const hashIndex = to.indexOf('#')
  const path = (hashIndex >= 0 ? to.slice(0, hashIndex) : to) || '/'
  if (hashIndex >= 0) return route.path === path && route.hash === to.slice(hashIndex)
  if (path === '/') return route.path === '/' && !route.hash
  return route.path === path || route.path.startsWith(`${path}/`)
}

/** The sidebar item a route belongs to (its section): the header title and the active highlight follow it. */
export function activeNavItem(
  role: UserRole,
  route: { readonly path: string; readonly hash: string },
): RoleNavItem | undefined {
  const support = roleSupportNav[role]
  const items: readonly RoleNavItem[] = support ? [...roleNavigation[role], support] : roleNavigation[role]
  return items.find((item) => isNavItemActive(item.to, route))
}
