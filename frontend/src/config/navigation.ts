import type { UserRole } from '@/types/api'

/**
 * Per-persona navigation maps for the dashboard sidebar and public
 * top nav.
 *
 * <h2>Why a single config file</h2>
 * <p>The sidebar, mobile drawer, and breadcrumb generator all need the
 * same source of truth. Co-locating the three persona lists prevents
 * the classic bug where one nav surface drifts (a new admin page
 * added to the sidebar but not the breadcrumb).</p>
 *
 * <h2>Labels via i18n</h2>
 * <p>Items carry {@code labelKey} (an i18n message id) rather than a
 * literal string so MS / EN / ZH translations stay isolated to the
 * i18n bundle. The same applies to icons — {@code icon} is an
 * IconGlyph name, not raw SVG, so swapping the design system later
 * does not require touching this file.</p>
 */
export interface NavItem {
  labelKey: string
  to: string
  icon: string
}

export const publicNav: NavItem[] = [
  { labelKey: 'nav.home', to: '/', icon: 'home' },
  { labelKey: 'nav.courses', to: '/courses', icon: 'courses' },
  { labelKey: 'nav.about', to: '/#about', icon: 'about' },
  { labelKey: 'nav.safety', to: '/#safety', icon: 'shield' },
]

export const roleNavigation: Record<UserRole, NavItem[]> = {
  STUDENT: [
    { labelKey: 'nav.home', to: '/student/home', icon: 'home' },
    { labelKey: 'nav.learn', to: '/student/learn', icon: 'learn' },
    { labelKey: 'nav.practice', to: '/student/practice', icon: 'practice' },
    { labelKey: 'nav.classes', to: '/student/classes', icon: 'classes' },
    { labelKey: 'nav.achievements', to: '/student/achievements', icon: 'trophy' },
    { labelKey: 'nav.rewards', to: '/student/rewards', icon: 'rewards' },
    { labelKey: 'nav.aiHelper', to: '/student/ai-helper', icon: 'ai' },
    { labelKey: 'nav.profile', to: '/student/profile', icon: 'profile' },
  ],
  TEACHER: [
    { labelKey: 'nav.dashboard', to: '/teacher/dashboard', icon: 'dashboard' },
    { labelKey: 'nav.content', to: '/teacher/content', icon: 'content' },
    { labelKey: 'nav.classes', to: '/teacher/classes', icon: 'classes' },
    { labelKey: 'nav.students', to: '/teacher/students', icon: 'students' },
    { labelKey: 'nav.feedback', to: '/teacher/feedback', icon: 'moderation' },
    { labelKey: 'nav.earnings', to: '/teacher/earnings', icon: 'earnings' },
    { labelKey: 'nav.profile', to: '/teacher/profile', icon: 'profile' },
  ],
  ADMIN: [
    { labelKey: 'nav.dashboard', to: '/admin/dashboard', icon: 'dashboard' },
    { labelKey: 'nav.users', to: '/admin/users', icon: 'users' },
    { labelKey: 'nav.teacherVerification', to: '/admin/teacher-verification', icon: 'verification' },
    { labelKey: 'nav.content', to: '/admin/content', icon: 'content' },
    { labelKey: 'nav.activityLibrary', to: '/admin/activities', icon: 'practice' },
    { labelKey: 'nav.moderation', to: '/admin/moderation', icon: 'moderation' },
    { labelKey: 'nav.support', to: '/admin/settings?section=support', icon: 'bell' },
    { labelKey: 'nav.finance', to: '/admin/finance', icon: 'finance' },
    { labelKey: 'nav.analytics', to: '/admin/analytics', icon: 'analytics' },
    { labelKey: 'nav.settings', to: '/admin/settings', icon: 'settings' },
  ],
}
