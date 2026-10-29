import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import type { UserRole } from '@/types/api'

import { useAuthStore } from '@/stores/auth'

import { resolveNavigation } from './guard'
import { applyDocumentTitle } from './pageTitle'

const PublicLayout = () => import('@/layouts/PublicLayout.vue')
const DashboardLayout = () => import('@/layouts/DashboardLayout.vue')

const LandingPage = () => import('@/pages/public/LandingPage.vue')
const PublicClassesPage = () => import('@/pages/public/PublicClassesPage.vue')
const PublicClassDetailPage = () => import('@/pages/public/PublicClassDetailPage.vue')
const HelpPage = () => import('@/pages/public/HelpPage.vue')
const SafetyPage = () => import('@/pages/public/SafetyPage.vue')
const AuthPage = () => import('@/pages/public/AuthPage.vue')
const ForgotPasswordPage = () => import('@/pages/public/ForgotPasswordPage.vue')
const ResetPasswordPage = () => import('@/pages/public/ResetPasswordPage.vue')
const ConfirmEmailPage = () => import('@/pages/public/ConfirmEmailPage.vue')
const TermsPage = () => import('@/pages/public/TermsPage.vue')
const PrivacyPage = () => import('@/pages/public/PrivacyPage.vue')
const ResponsibleAiPage = () => import('@/pages/public/ResponsibleAiPage.vue')

const StudentHomePage = () => import('@/pages/student/StudentHomePage.vue')
const StudentSetupPage = () => import('@/pages/student/StudentSetupPage.vue')
const StudentLearnPage = () => import('@/pages/student/StudentLearnPage.vue')
const StudentLessonPlayerPage = () => import('@/pages/student/StudentLessonPlayerPage.vue')
const StudentPracticePage = () => import('@/pages/student/StudentPracticePage.vue')
const StudentMistakesReviewPage = () => import('@/pages/student/StudentMistakesReviewPage.vue')
const StudentAiHelperPage = () => import('@/pages/student/StudentAiHelperPage.vue')
const StudentClassesPage = () => import('@/pages/student/StudentClassesPage.vue')
const StudentFindClassesPage = () => import('@/pages/student/StudentFindClassesPage.vue')
const StudentLiveSessionsPage = () => import('@/pages/student/StudentLiveSessionsPage.vue')
const StudentClassPage = () => import('@/pages/student/StudentClassPage.vue')
const StudentClassLessonPage = () => import('@/pages/student/StudentClassLessonPage.vue')
const StudentClassActivityPage = () => import('@/pages/student/StudentClassActivityPage.vue')
const StudentQuizPage = () => import('@/pages/student/StudentQuizPage.vue')
const StudentProgressPage = () => import('@/pages/student/StudentProgressPage.vue')
const StudentPlanPage = () => import('@/pages/student/StudentPlanPage.vue')
const StudentLevelCheckPage = () => import('@/pages/student/StudentLevelCheckPage.vue')

const SupportPage = () => import('@/pages/shared/SupportPage.vue')
const SupportTicketPage = () => import('@/pages/shared/SupportTicketPage.vue')
const NotificationsPage = () => import('@/pages/shared/NotificationsPage.vue')
const AccountSettingsPage = () => import('@/pages/shared/AccountSettingsPage.vue')

const TeacherDashboardPage = () => import('@/pages/teacher/TeacherDashboardPage.vue')
const TeacherClassesPage = () => import('@/pages/teacher/TeacherClassesPage.vue')
const TeacherClassPage = () => import('@/pages/teacher/TeacherClassPage.vue')
const TeacherLessonEditorPage = () => import('@/pages/teacher/TeacherLessonEditorPage.vue')
const TeacherLiveSessionsPage = () => import('@/pages/teacher/TeacherLiveSessionsPage.vue')
const TeacherStudentsPage = () => import('@/pages/teacher/TeacherStudentsPage.vue')
const TeacherFeedbackHistoryPage = () => import('@/pages/teacher/TeacherFeedbackHistoryPage.vue')
const TeacherProfilePage = () => import('@/pages/teacher/TeacherProfilePage.vue')
const TeacherLockedPage = () => import('@/pages/teacher/TeacherLockedPage.vue')

const AdminDashboardPage = () => import('@/pages/admin/AdminDashboardPage.vue')
const AdminUsersPage = () => import('@/pages/admin/AdminUsersPage.vue')
const AdminTeacherApplicationsPage = () => import('@/pages/admin/AdminTeacherApplicationsPage.vue')
const AdminContentReviewPage = () => import('@/pages/admin/AdminContentReviewPage.vue')
const AdminTeacherClassesPage = () => import('@/pages/admin/AdminTeacherClassesPage.vue')
const AdminLearningPathPage = () => import('@/pages/admin/AdminLearningPathPage.vue')
const AdminPracticeLibraryPage = () => import('@/pages/admin/AdminPracticeLibraryPage.vue')
const AdminAiReportsPage = () => import('@/pages/admin/AdminAiReportsPage.vue')
const AdminAiUsagePage = () => import('@/pages/admin/AdminAiUsagePage.vue')
const AdminAiSettingsPage = () => import('@/pages/admin/AdminAiSettingsPage.vue')
const AdminSupportPage = () => import('@/pages/admin/AdminSupportPage.vue')
const AdminAnalyticsPage = () => import('@/pages/admin/AdminAnalyticsPage.vue')
const AdminAuditLogPage = () => import('@/pages/admin/AdminAuditLogPage.vue')

const SystemStatusPage = () => import('@/pages/system/SystemStatusPage.vue')

const SAFE_HASH_SELECTOR_PATTERN = /^#[A-Za-z][A-Za-z0-9_-]*$/

declare module 'vue-router' {
  interface RouteMeta {
    roles?: UserRole[]
    /**
     * Focus mode: a learning task (lesson player, practice review, setup, class activity, lesson quiz) fills the
     * screen without the sidebar and header; the page draws its own top bar (FocusTopBar).
     */
    focus?: boolean
    /** Teacher tools that open only after an admin approves the teacher (others see the Locked page). */
    requiresApprovedTeacher?: boolean
  }
}

/** Old paths kept as redirects so links in stored notifications and bookmarks still land on the right page. */
const legacyRedirects: RouteRecordRaw[] = [
  { path: '/courses', redirect: { name: 'classes' } },
  { path: '/courses/:classId', redirect: (to) => ({ name: 'class-detail', params: { classId: to.params.classId } }) },
  { path: '/catalog', redirect: { name: 'classes' } },
  { path: '/student/achievements', redirect: { name: 'student-progress' } },
  { path: '/student/rewards', redirect: { name: 'student-progress' } },
  { path: '/student/profile', redirect: { name: 'student-progress' } },
  { path: '/student/feedback', redirect: { name: 'student-classes' } },
  { path: '/student/ai-helper', redirect: { name: 'student-ai' } },
  { path: '/student/ai-reports', redirect: { name: 'student-ai', params: { tab: 'ask' } } },
  { path: '/student/practice-submissions', redirect: { name: 'student-ai', params: { tab: 'voice' } } },
  { path: '/student/practice/wrong-answers', redirect: { name: 'student-practice', params: { type: 'mistakes' } } },
  { path: '/student/live-classes', redirect: { name: 'student-live-sessions' } },
  { path: '/teacher/content', redirect: { name: 'teacher-classes' } },
  { path: '/teacher/activities', redirect: { name: 'teacher-classes' } },
  { path: '/teacher/feedback', redirect: { name: 'teacher-feedback-history' } },
  { path: '/teacher/live-classes', redirect: { name: 'teacher-live-sessions' } },
  { path: '/teacher/recorded-lessons', redirect: { name: 'teacher-classes' } },
  { path: '/teacher/moderation-records', redirect: { name: 'teacher-feedback-history' } },
  { path: '/teacher/verification-status', redirect: { name: 'teacher-profile' } },
  { path: '/admin/teacher-verification', redirect: { name: 'admin-teacher-applications' } },
  { path: '/admin/teacher-approval', redirect: { name: 'admin-teacher-applications' } },
  { path: '/admin/content-review', redirect: { name: 'admin-content-review' } },
  { path: '/admin/activities', redirect: { name: 'admin-practice-library' } },
  { path: '/admin/moderation', redirect: { name: 'admin-ai-reports' } },
  { path: '/admin/ai-response-reports', redirect: { name: 'admin-ai-reports' } },
  { path: '/admin/settings', redirect: { name: 'admin-ai-settings' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: PublicLayout,
      children: [
        { path: '', name: 'landing', component: LandingPage },
        { path: 'classes', name: 'classes', component: PublicClassesPage },
        { path: 'classes/:classId(\\d+)', name: 'class-detail', component: PublicClassDetailPage },
        { path: 'help', name: 'help', component: HelpPage },
        { path: 'safety', name: 'safety', component: SafetyPage },
        { path: 'auth', name: 'auth', component: AuthPage },
        { path: 'forgot-password', name: 'forgot-password', component: ForgotPasswordPage },
        { path: 'reset-password', name: 'reset-password', component: ResetPasswordPage },
        { path: 'confirm-email', name: 'confirm-email', component: ConfirmEmailPage },
        { path: 'terms', name: 'terms', component: TermsPage },
        { path: 'privacy', name: 'privacy', component: PrivacyPage },
        { path: 'responsible-ai', name: 'responsible-ai', component: ResponsibleAiPage },
      ],
    },
    {
      path: '/student',
      component: DashboardLayout,
      meta: { roles: ['STUDENT'] },
      children: [
        { path: '', redirect: { name: 'student-home' } },
        { path: 'home', name: 'student-home', component: StudentHomePage },
        { path: 'setup', name: 'student-setup', component: StudentSetupPage, meta: { focus: true } },
        { path: 'learn', name: 'student-learn', component: StudentLearnPage },
        {
          path: 'learn/lessons/:lessonId(\\d+)',
          name: 'student-learn-lesson',
          component: StudentLessonPlayerPage,
          meta: { focus: true },
        },
        {
          path: 'practice/review',
          name: 'student-practice-review',
          component: StudentMistakesReviewPage,
          meta: { focus: true },
        },
        {
          path: 'practice/:type(flashcards|quizzes|listening|tracing|drag-drop|matching|story|mistakes)?',
          name: 'student-practice',
          component: StudentPracticePage,
        },
        { path: 'ai/:tab(talk|ask|quiz|story|voice)?', name: 'student-ai', component: StudentAiHelperPage },
        { path: 'classes', name: 'student-classes', component: StudentClassesPage },
        { path: 'classes/find', name: 'student-classes-find', component: StudentFindClassesPage },
        { path: 'classes/live', name: 'student-live-sessions', component: StudentLiveSessionsPage },
        {
          path: 'classes/:classId(\\d+)/:tab(results|feedback)?',
          name: 'student-class',
          component: StudentClassPage,
        },
        {
          path: 'classes/:classId(\\d+)/lessons/:lessonId(\\d+)',
          name: 'student-class-lesson',
          component: StudentClassLessonPage,
        },
        {
          path: 'classes/:classId(\\d+)/lessons/:lessonId(\\d+)/activities/:activityType/:activityId(\\d+)',
          name: 'student-class-activity',
          component: StudentClassActivityPage,
          meta: { focus: true },
        },
        { path: 'quiz/:quizId(\\d+)', name: 'student-quiz', component: StudentQuizPage, meta: { focus: true } },
        { path: 'progress', name: 'student-progress', component: StudentProgressPage },
        { path: 'progress/plan', name: 'student-plan', component: StudentPlanPage },
        {
          path: 'progress/plan/level-check',
          name: 'student-level-check',
          component: StudentLevelCheckPage,
          meta: { focus: true },
        },
        { path: 'support', name: 'student-support', component: SupportPage },
        { path: 'support/:ticketId(\\d+)', name: 'student-support-ticket', component: SupportTicketPage },
        { path: 'notifications', name: 'student-notifications', component: NotificationsPage },
        { path: 'account', name: 'student-account', component: AccountSettingsPage },
      ],
    },
    {
      path: '/teacher',
      component: DashboardLayout,
      meta: { roles: ['TEACHER'] },
      children: [
        { path: '', redirect: { name: 'teacher-dashboard' } },
        { path: 'dashboard', name: 'teacher-dashboard', component: TeacherDashboardPage },
        {
          path: 'classes',
          name: 'teacher-classes',
          component: TeacherClassesPage,
          meta: { requiresApprovedTeacher: true },
        },
        {
          path: 'classes/:classId(\\d+)/:tab(details|history)?',
          name: 'teacher-class',
          component: TeacherClassPage,
          meta: { requiresApprovedTeacher: true },
        },
        {
          path: 'classes/:classId(\\d+)/lessons/:lessonId(\\d+)/:tab(activities|quiz|preview)?',
          name: 'teacher-lesson',
          component: TeacherLessonEditorPage,
          meta: { requiresApprovedTeacher: true },
        },
        {
          path: 'live-sessions/:tab(past)?',
          name: 'teacher-live-sessions',
          component: TeacherLiveSessionsPage,
          meta: { requiresApprovedTeacher: true },
        },
        {
          path: 'students',
          name: 'teacher-students',
          component: TeacherStudentsPage,
          meta: { requiresApprovedTeacher: true },
        },
        {
          path: 'students/feedback',
          name: 'teacher-feedback-history',
          component: TeacherFeedbackHistoryPage,
          meta: { requiresApprovedTeacher: true },
        },
        { path: 'profile', name: 'teacher-profile', component: TeacherProfilePage },
        { path: 'locked', name: 'teacher-locked', component: TeacherLockedPage },
        { path: 'support', name: 'teacher-support', component: SupportPage },
        { path: 'support/:ticketId(\\d+)', name: 'teacher-support-ticket', component: SupportTicketPage },
        { path: 'notifications', name: 'teacher-notifications', component: NotificationsPage },
        { path: 'account', name: 'teacher-account', component: AccountSettingsPage },
      ],
    },
    {
      path: '/admin',
      component: DashboardLayout,
      meta: { roles: ['ADMIN'] },
      children: [
        { path: '', redirect: { name: 'admin-dashboard' } },
        { path: 'dashboard', name: 'admin-dashboard', component: AdminDashboardPage },
        { path: 'users', name: 'admin-users', component: AdminUsersPage },
        { path: 'users/applications', name: 'admin-teacher-applications', component: AdminTeacherApplicationsPage },
        { path: 'content', name: 'admin-content-review', component: AdminContentReviewPage },
        { path: 'content/classes', name: 'admin-teacher-classes', component: AdminTeacherClassesPage },
        { path: 'content/learning-path', name: 'admin-learning-path', component: AdminLearningPathPage },
        { path: 'content/practice', name: 'admin-practice-library', component: AdminPracticeLibraryPage },
        { path: 'ai', name: 'admin-ai-reports', component: AdminAiReportsPage },
        { path: 'ai/usage', name: 'admin-ai-usage', component: AdminAiUsagePage },
        { path: 'ai/settings', name: 'admin-ai-settings', component: AdminAiSettingsPage },
        { path: 'support', name: 'admin-support', component: AdminSupportPage },
        { path: 'analytics', name: 'admin-analytics', component: AdminAnalyticsPage },
        { path: 'audit-log', name: 'admin-audit-log', component: AdminAuditLogPage },
        { path: 'notifications', name: 'admin-notifications', component: NotificationsPage },
        { path: 'account', name: 'admin-account', component: AccountSettingsPage },
      ],
    },
    ...legacyRedirects,
    { path: '/unauthorized', name: 'unauthorized', component: SystemStatusPage, props: { kind: 'unauthorized' } },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: SystemStatusPage, props: { kind: 'not-found' } },
  ],
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    // Only a plain id is used as a selector (a crafted hash cannot inject one).
    if (SAFE_HASH_SELECTOR_PATTERN.test(to.hash)) {
      const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
      return { el: to.hash, behavior: reduceMotion ? 'auto' : 'smooth' }
    }
    return { top: 0 }
  },
})

router.beforeEach((to) => resolveNavigation(to, useAuthStore()))

// FRLANG02: the tab title follows the final route and the interface language (App.vue re-applies it on change).
router.afterEach((to) => applyDocumentTitle(to))

export { router }
