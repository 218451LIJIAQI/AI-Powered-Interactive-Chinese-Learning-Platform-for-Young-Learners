import type { UserRole } from '@/types/api'

export interface NavItem {
  readonly labelKey: string
  readonly to: string
  readonly icon: string
}

export interface RoleNavItem extends NavItem {
  /** Key of the sidebar group (see `roleNavigationGroups`) this item is rendered in. */
  readonly group: string
}

export interface NavGroup {
  readonly key: string
  readonly titleKey: string
  readonly ariaLabelKey: string
}

export const publicNav = [
  { labelKey: 'nav.home', to: '/', icon: 'home' },
  { labelKey: 'nav.courses', to: '/courses', icon: 'courses' },
  { labelKey: 'nav.about', to: '/#about', icon: 'about' },
  { labelKey: 'nav.responsibleAi', to: '/responsible-ai', icon: 'shield' },
] as const satisfies readonly NavItem[]

/** Sidebar groups per role, in display order. Items are matched to a group by their `group` key. */
export const roleNavigationGroups = {
  STUDENT: [
    { key: 'journey', titleKey: 'dashboard.journey', ariaLabelKey: 'dashboard.studentNavigation' },
    { key: 'motivation', titleKey: 'dashboard.motivation', ariaLabelKey: 'dashboard.studentMotivation' },
    { key: 'support', titleKey: 'dashboard.support', ariaLabelKey: 'dashboard.studentSupport' },
  ],
  TEACHER: [
    { key: 'overview', titleKey: 'dashboard.overview', ariaLabelKey: 'dashboard.teacherOverview' },
    { key: 'teaching', titleKey: 'dashboard.teaching', ariaLabelKey: 'dashboard.teacherTools' },
    { key: 'learners', titleKey: 'dashboard.learners', ariaLabelKey: 'dashboard.teacherLearners' },
    { key: 'profile', titleKey: 'dashboard.profile', ariaLabelKey: 'dashboard.teacherProfileSettings' },
  ],
  ADMIN: [
    { key: 'overview', titleKey: 'dashboard.overview', ariaLabelKey: 'dashboard.adminOverview' },
    { key: 'operations', titleKey: 'dashboard.operations', ariaLabelKey: 'dashboard.adminOperations' },
    { key: 'trust', titleKey: 'dashboard.trustContent', ariaLabelKey: 'dashboard.adminTrust' },
    { key: 'platform', titleKey: 'dashboard.platform', ariaLabelKey: 'dashboard.adminPlatform' },
  ],
} as const satisfies Record<UserRole, readonly NavGroup[]>

export const roleNavigation = {
  STUDENT: [
    { labelKey: 'nav.home', to: '/student/home', icon: 'home', group: 'journey' },
    { labelKey: 'nav.learn', to: '/student/learn', icon: 'learn', group: 'journey' },
    { labelKey: 'nav.practice', to: '/student/practice', icon: 'practice', group: 'journey' },
    { labelKey: 'nav.classes', to: '/student/classes', icon: 'classes', group: 'journey' },
    { labelKey: 'nav.practiceSubmissions', to: '/student/practice-submissions', icon: 'mic', group: 'journey' },
    { labelKey: 'nav.progress', to: '/student/progress', icon: 'progress', group: 'motivation' },
    { labelKey: 'nav.achievements', to: '/student/achievements', icon: 'trophy', group: 'motivation' },
    { labelKey: 'nav.rewards', to: '/student/rewards', icon: 'rewards', group: 'motivation' },
    { labelKey: 'nav.aiHelper', to: '/student/ai-helper', icon: 'ai', group: 'support' },
    { labelKey: 'nav.profile', to: '/student/profile', icon: 'profile', group: 'support' },
    { labelKey: 'nav.notifications', to: '/student/notifications', icon: 'bell', group: 'support' },
    { labelKey: 'nav.account', to: '/student/account', icon: 'account', group: 'support' },
  ],
  TEACHER: [
    { labelKey: 'nav.dashboard', to: '/teacher/dashboard', icon: 'dashboard', group: 'overview' },
    { labelKey: 'nav.classes', to: '/teacher/classes', icon: 'classes', group: 'teaching' },
    { labelKey: 'nav.content', to: '/teacher/content', icon: 'content', group: 'teaching' },
    { labelKey: 'nav.practiceActivities', to: '/teacher/activities', icon: 'fun', group: 'teaching' },
    { labelKey: 'nav.students', to: '/teacher/students', icon: 'students', group: 'learners' },
    { labelKey: 'nav.feedback', to: '/teacher/feedback', icon: 'moderation', group: 'learners' },
    { labelKey: 'nav.profile', to: '/teacher/profile', icon: 'profile', group: 'profile' },
    { labelKey: 'nav.notifications', to: '/teacher/notifications', icon: 'bell', group: 'profile' },
    { labelKey: 'nav.account', to: '/teacher/account', icon: 'account', group: 'profile' },
  ],
  ADMIN: [
    { labelKey: 'nav.dashboard', to: '/admin/dashboard', icon: 'dashboard', group: 'overview' },
    { labelKey: 'nav.users', to: '/admin/users', icon: 'users', group: 'operations' },
    {
      labelKey: 'nav.teacherVerification',
      to: '/admin/teacher-verification',
      icon: 'verification',
      group: 'operations',
    },
    { labelKey: 'nav.moderation', to: '/admin/moderation', icon: 'moderation', group: 'operations' },
    { labelKey: 'nav.contentReview', to: '/admin/content-review', icon: 'clip', group: 'trust' },
    { labelKey: 'nav.content', to: '/admin/content', icon: 'content', group: 'trust' },
    { labelKey: 'nav.activityLibrary', to: '/admin/activities', icon: 'practice', group: 'trust' },
    { labelKey: 'nav.analytics', to: '/admin/analytics', icon: 'analytics', group: 'platform' },
    { labelKey: 'nav.settings', to: '/admin/settings', icon: 'settings', group: 'platform' },
    { labelKey: 'nav.notifications', to: '/admin/notifications', icon: 'bell', group: 'platform' },
    { labelKey: 'nav.account', to: '/admin/account', icon: 'account', group: 'platform' },
  ],
} as const satisfies Record<UserRole, readonly RoleNavItem[]>
