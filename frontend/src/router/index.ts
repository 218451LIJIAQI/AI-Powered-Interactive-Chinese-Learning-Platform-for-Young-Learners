import { createRouter, createWebHistory } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'

import type { UserRole } from '@/types/api'

import { useAuthStore } from '@/stores/auth'

import { resolveNavigation } from './guard'
import { applyDocumentTitle } from './pageTitle'

const PublicLayout = () => import('@/layouts/PublicLayout.vue')
const DashboardLayout = () => import('@/layouts/DashboardLayout.vue')
const LandingPage = () => import('@/pages/public/LandingPage.vue')
const AuthPage = () => import('@/pages/public/AuthPage.vue')
const ForgotPasswordPage = () => import('@/pages/public/ForgotPasswordPage.vue')
const ResetPasswordPage = () => import('@/pages/public/ResetPasswordPage.vue')
const CatalogPage = () => import('@/pages/public/CatalogPage.vue')
const CourseDetailPage = () => import('@/pages/public/CourseDetailPage.vue')
const TermsPage = () => import('@/pages/public/TermsPage.vue')
const PrivacyPage = () => import('@/pages/public/PrivacyPage.vue')
const ResponsibleAiPage = () => import('@/pages/public/ResponsibleAiPage.vue')
const StudentHomePage = () => import('@/pages/student/StudentHomePage.vue')
const StudentLearnPage = () => import('@/pages/student/StudentLearnPage.vue')
const StudentPracticePage = () => import('@/pages/student/StudentPracticePage.vue')
const StudentQuizPage = () => import('@/pages/student/StudentQuizPage.vue')
const StudentClassesPage = () => import('@/pages/student/StudentClassesPage.vue')
const StudentAchievementsPage = () => import('@/pages/student/StudentAchievementsPage.vue')
const StudentRewardsPage = () => import('@/pages/student/StudentRewardsPage.vue')
const StudentAiHelperPage = () => import('@/pages/student/StudentAiHelperPage.vue')
const StudentProfilePage = () => import('@/pages/student/StudentProfilePage.vue')
const StudentProgressPage = () => import('@/pages/student/StudentProgressPage.vue')
const StudentPracticeSubmissionsPage = () => import('@/pages/student/StudentPracticeSubmissionsPage.vue')
const NotificationsPage = () => import('@/pages/shared/NotificationsPage.vue')
const AccountSettingsPage = () => import('@/pages/shared/AccountSettingsPage.vue')
const TeacherDashboardPage = () => import('@/pages/teacher/TeacherDashboardPage.vue')
const TeacherContentPage = () => import('@/pages/teacher/TeacherContentPage.vue')
const TeacherActivitiesPage = () => import('@/pages/teacher/TeacherActivitiesPage.vue')
const TeacherClassesPage = () => import('@/pages/teacher/TeacherClassesPage.vue')
const TeacherStudentsPage = () => import('@/pages/teacher/TeacherStudentsPage.vue')
const TeacherFeedbackPage = () => import('@/pages/teacher/TeacherFeedbackPage.vue')
const TeacherProfilePage = () => import('@/pages/teacher/TeacherProfilePage.vue')
const AdminDashboardPage = () => import('@/pages/admin/AdminDashboardPage.vue')
const AdminActivityLibraryPage = () => import('@/pages/admin/AdminActivityLibraryPage.vue')
const AdminUsersPage = () => import('@/pages/admin/AdminUsersPage.vue')
const AdminTeacherVerificationPage = () => import('@/pages/admin/AdminTeacherVerificationPage.vue')
const AdminContentPage = () => import('@/pages/admin/AdminContentPage.vue')
const AdminContentReviewPage = () => import('@/pages/admin/AdminContentReviewPage.vue')
const AdminModerationPage = () => import('@/pages/admin/AdminModerationPage.vue')
const AdminAnalyticsPage = () => import('@/pages/admin/AdminAnalyticsPage.vue')
const AdminSettingsPage = () => import('@/pages/admin/AdminSettingsPage.vue')
const UnauthorizedPage = () => import('@/pages/system/UnauthorizedPage.vue')
const NotFoundPage = () => import('@/pages/system/NotFoundPage.vue')

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    roles?: UserRole[]
    previewRole?: UserRole
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: PublicLayout,
      children: [
        { path: '', name: 'landing', component: LandingPage, meta: { title: 'MandarinSpark' } },
        { path: 'auth', name: 'auth', component: AuthPage, meta: { title: 'Sign in' } },
        {
          path: 'forgot-password',
          name: 'forgot-password',
          component: ForgotPasswordPage,
          meta: { title: 'Forgot password' },
        },
        {
          path: 'reset-password',
          name: 'reset-password',
          component: ResetPasswordPage,
          meta: { title: 'Reset password' },
        },
        { path: 'terms', name: 'terms', component: TermsPage, meta: { title: 'Terms of service' } },
        { path: 'privacy', name: 'privacy', component: PrivacyPage, meta: { title: 'Privacy notice' } },
        {
          path: 'responsible-ai',
          name: 'responsible-ai',
          component: ResponsibleAiPage,
          meta: { title: 'Responsible AI Guidance' },
        },
      ],
    },
    {
      path: '/courses',
      component: DashboardLayout,
      meta: { previewRole: 'STUDENT' },
      children: [
        {
          path: '',
          name: 'catalog',
          component: CatalogPage,
          meta: { title: 'Explore courses' },
        },
        {
          path: ':courseId',
          name: 'course-detail',
          component: CourseDetailPage,
          meta: { title: 'Course detail' },
        },
      ],
    },
    {
      path: '/student',
      component: DashboardLayout,
      meta: { roles: ['STUDENT'], previewRole: 'STUDENT' },
      children: [
        { path: '', redirect: { name: 'student-home' } },
        {
          path: 'home',
          name: 'student-home',
          component: StudentHomePage,
          meta: { title: 'Student home' },
        },
        {
          path: 'learn',
          name: 'student-learn',
          component: StudentLearnPage,
          meta: { title: 'Learn' },
        },
        {
          path: 'practice',
          name: 'student-practice',
          component: StudentPracticePage,
          meta: { title: 'Practice' },
        },
        {
          path: 'quiz/:quizId?',
          name: 'student-quiz',
          component: StudentQuizPage,
          meta: { title: 'Quiz attempt' },
        },
        {
          path: 'classes',
          name: 'student-classes',
          component: StudentClassesPage,
          meta: { title: 'Classes' },
        },
        {
          path: 'achievements',
          name: 'student-achievements',
          component: StudentAchievementsPage,
          meta: { title: 'Achievements' },
        },
        {
          path: 'rewards',
          name: 'student-rewards',
          component: StudentRewardsPage,
          meta: { title: 'Rewards' },
        },
        {
          path: 'ai-helper',
          name: 'student-ai-helper',
          component: StudentAiHelperPage,
          meta: { title: 'AI helper' },
        },
        {
          path: 'profile',
          name: 'student-profile',
          component: StudentProfilePage,
          meta: { title: 'Profile' },
        },
        {
          path: 'progress',
          name: 'student-progress',
          component: StudentProgressPage,
          meta: { title: 'My progress' },
        },
        {
          path: 'practice-submissions',
          name: 'student-practice-submissions',
          component: StudentPracticeSubmissionsPage,
          meta: { title: 'Speaking and writing practice' },
        },
        {
          path: 'notifications',
          name: 'student-notifications',
          component: NotificationsPage,
          meta: { title: 'Notifications' },
        },
        {
          path: 'account',
          name: 'student-account',
          component: AccountSettingsPage,
          meta: { title: 'Account and security' },
        },
      ],
    },
    {
      path: '/teacher',
      component: DashboardLayout,
      meta: { roles: ['TEACHER'], previewRole: 'TEACHER' },
      children: [
        { path: '', redirect: { name: 'teacher-dashboard' } },
        {
          path: 'dashboard',
          name: 'teacher-dashboard',
          component: TeacherDashboardPage,
          meta: { title: 'Teacher dashboard' },
        },
        {
          path: 'content',
          name: 'teacher-content',
          component: TeacherContentPage,
          meta: { title: 'Content management' },
        },
        {
          path: 'activities',
          name: 'teacher-activities',
          component: TeacherActivitiesPage,
          meta: { title: 'Practice activities' },
        },
        {
          path: 'classes',
          name: 'teacher-classes',
          component: TeacherClassesPage,
          meta: { title: 'Teacher classes' },
        },
        {
          path: 'students',
          name: 'teacher-students',
          component: TeacherStudentsPage,
          meta: { title: 'Teacher students' },
        },
        {
          path: 'feedback',
          name: 'teacher-feedback',
          component: TeacherFeedbackPage,
          meta: { title: 'Feedback center' },
        },
        {
          path: 'profile',
          name: 'teacher-profile',
          component: TeacherProfilePage,
          meta: { title: 'Teacher profile' },
        },
        {
          path: 'notifications',
          name: 'teacher-notifications',
          component: NotificationsPage,
          meta: { title: 'Notifications' },
        },
        {
          path: 'account',
          name: 'teacher-account',
          component: AccountSettingsPage,
          meta: { title: 'Account and security' },
        },
      ],
    },
    {
      path: '/admin',
      component: DashboardLayout,
      meta: { roles: ['ADMIN'], previewRole: 'ADMIN' },
      children: [
        { path: '', redirect: { name: 'admin-dashboard' } },
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: AdminDashboardPage,
          meta: { title: 'Admin dashboard' },
        },
        {
          path: 'users',
          name: 'admin-users',
          component: AdminUsersPage,
          meta: { title: 'Users' },
        },
        {
          path: 'teacher-verification',
          name: 'admin-teacher-verification',
          component: AdminTeacherVerificationPage,
          meta: { title: 'Teacher verification' },
        },
        {
          path: 'content',
          name: 'admin-content',
          component: AdminContentPage,
          meta: { title: 'Content management' },
        },
        {
          path: 'content-review',
          name: 'admin-content-review',
          component: AdminContentReviewPage,
          meta: { title: 'Content review' },
        },
        {
          path: 'activities',
          name: 'admin-activities',
          component: AdminActivityLibraryPage,
          meta: { title: 'Activity library' },
        },
        {
          path: 'moderation',
          name: 'admin-moderation',
          component: AdminModerationPage,
          meta: { title: 'Reports and moderation' },
        },
        {
          path: 'analytics',
          name: 'admin-analytics',
          component: AdminAnalyticsPage,
          meta: { title: 'Analytics' },
        },
        {
          path: 'settings',
          name: 'admin-settings',
          component: AdminSettingsPage,
          meta: { title: 'Settings and audit' },
        },
        {
          path: 'notifications',
          name: 'admin-notifications',
          component: NotificationsPage,
          meta: { title: 'Notifications' },
        },
        {
          path: 'account',
          name: 'admin-account',
          component: AccountSettingsPage,
          meta: { title: 'Account and security' },
        },
      ],
    },
    { path: '/unauthorized', name: 'unauthorized', component: UnauthorizedPage, meta: { title: 'Unauthorized' } },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundPage, meta: { title: 'Page not found' } },
  ],
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    const hashSelector = safeHashSelector(to.hash)
    if (hashSelector) return { el: hashSelector, behavior: 'smooth' }
    return { top: 0 }
  },
})

const SAFE_HASH_SELECTOR_PATTERN = /^#[A-Za-z][A-Za-z0-9_-]*$/

function safeHashSelector(hash: string): string | null {
  return SAFE_HASH_SELECTOR_PATTERN.test(hash) ? hash : null
}

router.beforeEach((to): Promise<RouteLocationRaw | boolean> => resolveNavigation(to, useAuthStore()))

// FR-I18N-05: the tab title follows the final route and the interface language (App.vue re-applies it on change).
router.afterEach((to) => applyDocumentTitle(to))

export { router }
