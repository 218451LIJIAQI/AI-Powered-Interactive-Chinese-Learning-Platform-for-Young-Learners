import type { RouteRecordRaw } from 'vue-router'

import { PRACTICE_ACTIVITIES } from '@/config/practiceActivities'
import type { UserRole } from '@/types/api'

const DashboardLayout = () => import('@/layouts/DashboardLayout.vue')

const NotificationsPage = () => import('@/pages/shared/NotificationsPage.vue')
const AccountSettingsPage = () => import('@/pages/shared/AccountSettingsPage.vue')

const SystemStatusPage = () => import('@/pages/system/SystemStatusPage.vue')

const practiceSlugPattern = Object.values(PRACTICE_ACTIVITIES)
  .map((activity) => activity.slug)
  .join('|')

declare module 'vue-router' {
  interface RouteMeta {
    roles?: UserRole[]
    /**
     * Focus mode: a learning task (lesson player, practice review, setup, level check, class activity, lesson quiz)
     * fills the screen without the sidebar and header; the page draws its own top bar (FocusTopBar).
     */
    focus?: boolean
    /** A public page that ends with the site footer (PublicFooter): the landing page. */
    footer?: boolean
    /** The sign-in and account-recovery pages render inside the full-window AuthShell without PublicTopNav. */
    authShell?: boolean
  }
}

/** The app's route table; the page tests mount their pages on it too (src/test/routes.ts). */
export const routes: readonly RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/PublicLayout.vue'),
    children: [
      { path: '', name: 'landing', component: () => import('@/pages/public/LandingPage.vue'), meta: { footer: true } },
      { path: 'classes', name: 'classes', component: () => import('@/pages/public/PublicClassesPage.vue') },
      {
        path: 'classes/:classId(\\d+)',
        name: 'class-detail',
        component: () => import('@/pages/public/PublicClassDetailPage.vue'),
      },
      { path: 'help', name: 'help', component: () => import('@/pages/public/HelpPage.vue') },
      { path: 'safety', name: 'safety', component: () => import('@/pages/public/SafetyPage.vue') },
      { path: 'auth', name: 'auth', component: () => import('@/pages/public/AuthPage.vue'), meta: { authShell: true } },
      {
        path: 'forgot-password',
        name: 'forgot-password',
        component: () => import('@/pages/public/ForgotPasswordPage.vue'),
        meta: { authShell: true },
      },
      {
        path: 'reset-password',
        name: 'reset-password',
        component: () => import('@/pages/public/ResetPasswordPage.vue'),
        meta: { authShell: true },
      },
      { path: 'confirm-email', name: 'confirm-email', component: () => import('@/pages/public/ConfirmEmailPage.vue') },
      { path: 'terms', name: 'terms', component: () => import('@/pages/public/TermsPage.vue') },
      { path: 'privacy', name: 'privacy', component: () => import('@/pages/public/PrivacyPage.vue') },
      {
        path: 'responsible-ai',
        name: 'responsible-ai',
        component: () => import('@/pages/public/ResponsibleAiPage.vue'),
      },
    ],
  },
  {
    path: '/student',
    component: DashboardLayout,
    meta: { roles: ['STUDENT'] },
    children: [
      { path: '', redirect: { name: 'student-home' } },
      { path: 'home', name: 'student-home', component: () => import('@/pages/student/StudentHomePage.vue') },
      {
        path: 'setup',
        name: 'student-setup',
        component: () => import('@/pages/student/StudentSetupPage.vue'),
        meta: { focus: true },
      },
      { path: 'learn', name: 'student-learn', component: () => import('@/pages/student/StudentLearnPage.vue') },
      {
        path: 'learn/lessons/:lessonId(\\d+)',
        name: 'student-learn-lesson',
        component: () => import('@/pages/student/StudentLessonPlayerPage.vue'),
        meta: { focus: true },
      },
      {
        path: 'practice/review',
        name: 'student-practice-review',
        component: () => import('@/pages/student/StudentMistakesReviewPage.vue'),
        meta: { focus: true },
      },
      {
        path: `practice/:type(${practiceSlugPattern}|mistakes)?`,
        name: 'student-practice',
        component: () => import('@/pages/student/StudentPracticePage.vue'),
      },
      { path: 'ai', redirect: { name: 'student-ai', params: { tab: 'talk' } } },
      {
        path: 'ai/:tab(talk|ask|quiz|story|voice)',
        name: 'student-ai',
        component: () => import('@/pages/student/StudentAiHelperPage.vue'),
      },
      { path: 'classes', name: 'student-classes', component: () => import('@/pages/student/StudentClassesPage.vue') },
      {
        path: 'classes/find',
        name: 'student-classes-find',
        component: () => import('@/pages/student/StudentFindClassesPage.vue'),
      },
      {
        path: 'classes/:classId(\\d+)',
        name: 'student-class',
        component: () => import('@/pages/student/StudentClassPage.vue'),
      },
      {
        path: 'classes/:classId(\\d+)/lessons/:lessonId(\\d+)',
        name: 'student-class-lesson',
        component: () => import('@/pages/student/StudentClassLessonPage.vue'),
      },
      {
        path: 'classes/:classId(\\d+)/lessons/:lessonId(\\d+)/activities/:activityType(flashcards|tracing|matching)/:activityId(\\d+)',
        name: 'student-class-activity',
        component: () => import('@/pages/student/StudentClassActivityPage.vue'),
        meta: { focus: true },
      },
      {
        path: 'quiz/:quizId(\\d+)',
        name: 'student-quiz',
        component: () => import('@/pages/student/StudentQuizPage.vue'),
        meta: { focus: true },
      },
      {
        path: 'quiz/:quizId(\\d+)/attempts/:attemptId(\\d+)',
        name: 'student-quiz-attempt',
        component: () => import('@/pages/student/StudentQuizAttemptPage.vue'),
      },
      {
        path: 'progress',
        name: 'student-progress',
        component: () => import('@/pages/student/StudentProgressPage.vue'),
      },
      { path: 'progress/plan', name: 'student-plan', component: () => import('@/pages/student/StudentPlanPage.vue') },
      {
        path: 'progress/plan/level-check',
        name: 'student-level-check',
        component: () => import('@/pages/student/StudentLevelCheckPage.vue'),
        meta: { focus: true },
      },
      { path: 'notifications', name: 'student-notifications', component: NotificationsPage },
      { path: 'account', name: 'student-account', component: AccountSettingsPage },
    ],
  },
  {
    path: '/admin',
    component: DashboardLayout,
    meta: { roles: ['ADMIN'] },
    children: [
      { path: '', redirect: { name: 'admin-dashboard' } },
      { path: 'dashboard', name: 'admin-dashboard', component: () => import('@/pages/admin/AdminDashboardPage.vue') },
      { path: 'users', name: 'admin-users', component: () => import('@/pages/admin/AdminUsersPage.vue') },
      // Content › Classes: the classes and lessons the MandarinSpark team makes (A3).
      { path: 'content', name: 'admin-classes', component: () => import('@/pages/admin/AdminClassesPage.vue') },
      {
        path: 'content/classes/:classId(\\d+)/:tab(details)?',
        name: 'admin-class',
        component: () => import('@/pages/admin/AdminClassPage.vue'),
      },
      {
        path: 'content/classes/:classId(\\d+)/lessons/:lessonId(\\d+)/:tab(activities|quiz|preview)?',
        name: 'admin-lesson',
        component: () => import('@/pages/admin/AdminLessonEditorPage.vue'),
      },
      {
        path: 'content/learning-path',
        name: 'admin-learning-path',
        component: () => import('@/pages/admin/AdminLearningPathPage.vue'),
      },
      {
        path: 'content/practice',
        name: 'admin-practice-library',
        component: () => import('@/pages/admin/AdminPracticeLibraryPage.vue'),
      },
      { path: 'ai', name: 'admin-ai-reports', component: () => import('@/pages/admin/AdminAiReportsPage.vue') },
      { path: 'ai/usage', name: 'admin-ai-usage', component: () => import('@/pages/admin/AdminAiUsagePage.vue') },
      {
        path: 'ai/settings',
        name: 'admin-ai-settings',
        component: () => import('@/pages/admin/AdminAiSettingsPage.vue'),
      },
      { path: 'support', name: 'admin-support', component: () => import('@/pages/admin/AdminSupportPage.vue') },
      { path: 'analytics', name: 'admin-analytics', component: () => import('@/pages/admin/AdminAnalyticsPage.vue') },
      { path: 'audit-log', name: 'admin-audit-log', component: () => import('@/pages/admin/AdminAuditLogPage.vue') },
      { path: 'notifications', name: 'admin-notifications', component: NotificationsPage },
      { path: 'account', name: 'admin-account', component: AccountSettingsPage },
    ],
  },
  { path: '/unauthorized', name: 'unauthorized', component: SystemStatusPage, props: { kind: 'unauthorized' } },
  // Open to everyone, signed in or not (no roles): the API may be unreachable when it is shown.
  { path: '/maintenance', name: 'maintenance', component: SystemStatusPage, props: { kind: 'maintenance' } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: SystemStatusPage, props: { kind: 'not-found' } },
]
