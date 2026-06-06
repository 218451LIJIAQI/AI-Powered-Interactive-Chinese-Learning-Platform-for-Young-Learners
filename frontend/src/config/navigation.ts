import type { UserRole } from '@/types/api'

export interface NavItem {
  readonly labelKey: string
  readonly to: string
  readonly icon: string
}

export const publicNav = [
  { labelKey: 'nav.home', to: '/', icon: 'home' },
  { labelKey: 'nav.courses', to: '/courses', icon: 'courses' },
  { labelKey: 'nav.about', to: '/#about', icon: 'about' },
  { labelKey: 'nav.safety', to: '/#safety', icon: 'shield' },
] as const satisfies readonly NavItem[]

export const roleNavigation = {
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
} as const satisfies Record<UserRole, readonly NavItem[]>
