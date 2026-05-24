/**
 * Vue Router configuration — owns every navigable URL in the app.
 *
 * <h2>Lazy code-splitting</h2>
 * <p>Every page is imported via a dynamic `() => import(...)` so each
 * persona's bundle stays out of the initial download until the
 * route is actually visited. The student-only chunks never load
 * for a teacher session, and vice-versa.</p>
 *
 * <h2>Route meta contract</h2>
 * <p>{@link RouteMeta} declares the three flags pages use:</p>
 * <ul>
 *   <li>`title` — readable page title for the breadcrumb / topbar.</li>
 *   <li>`roles` — required user roles; the router guard redirects
 *       to `/auth` when the role doesn't match and `/unauthorized`
 *       when the role IS authenticated but for the wrong persona.</li>
 *   <li>`previewRole` — synthetic role used by the QA preview
 *       deployment to render a persona without a real session.</li>
 * </ul>
 *
 * <h2>Auth flow</h2>
 * <p>The before-each guard reads the Pinia store (hydrating it via
 * `bootstrap()` on first navigation), checks role + redirect query
 * preservation, and normalises legacy paths through `routeCompat`
 * so old email-notification deep-links don't 404.</p>
 */
import { createRouter, createWebHistory } from 'vue-router'

import type { UserRole } from '@/types/api'

import { useAuthStore } from '@/stores/auth'
import { defaultRouteForRole } from '@/utils/role'

const PublicLayout = () => import('@/layouts/PublicLayout.vue')
const DashboardLayout = () => import('@/layouts/DashboardLayout.vue')
const LandingPage = () => import('@/pages/public/LandingPage.vue')
const AuthPage = () => import('@/pages/public/AuthPage.vue')
const ForgotPasswordPage = () => import('@/pages/public/ForgotPasswordPage.vue')
const ResetPasswordPage = () => import('@/pages/public/ResetPasswordPage.vue')
const PaymentResultPage = () => import('@/pages/public/PaymentResultPage.vue')
const CatalogPage = () => import('@/pages/public/CatalogPage.vue')
const CourseDetailPage = () => import('@/pages/public/CourseDetailPage.vue')
const TermsPage = () => import('@/pages/public/TermsPage.vue')
const PrivacyPage = () => import('@/pages/public/PrivacyPage.vue')
const StudentHomePage = () => import('@/pages/student/StudentHomePage.vue')
const StudentLearnPage = () => import('@/pages/student/StudentLearnPage.vue')
const StudentPracticePage = () => import('@/pages/student/StudentPracticePage.vue')
const StudentQuizPage = () => import('@/pages/student/StudentQuizPage.vue')
const StudentClassesPage = () => import('@/pages/student/StudentClassesPage.vue')
const StudentAchievementsPage = () => import('@/pages/student/StudentAchievementsPage.vue')
const StudentRewardsPage = () => import('@/pages/student/StudentRewardsPage.vue')
const StudentAiHelperPage = () => import('@/pages/student/StudentAiHelperPage.vue')
const StudentProfilePage = () => import('@/pages/student/StudentProfilePage.vue')
const TeacherDashboardPage = () => import('@/pages/teacher/TeacherDashboardPage.vue')
const TeacherContentPage = () => import('@/pages/teacher/TeacherContentPage.vue')
const TeacherClassesPage = () => import('@/pages/teacher/TeacherClassesPage.vue')
const TeacherStudentsPage = () => import('@/pages/teacher/TeacherStudentsPage.vue')
const TeacherFeedbackPage = () => import('@/pages/teacher/TeacherFeedbackPage.vue')
const TeacherProfilePage = () => import('@/pages/teacher/TeacherProfilePage.vue')
const TeacherEarningsPage = () => import('@/pages/teacher/TeacherEarningsPage.vue')
const AdminDashboardPage = () => import('@/pages/admin/AdminDashboardPage.vue')
const AdminActivityLibraryPage = () => import('@/pages/admin/AdminActivityLibraryPage.vue')
const AdminUsersPage = () => import('@/pages/admin/AdminUsersPage.vue')
const AdminTeacherVerificationPage = () => import('@/pages/admin/AdminTeacherVerificationPage.vue')
const AdminContentPage = () => import('@/pages/admin/AdminContentPage.vue')
const AdminModerationPage = () => import('@/pages/admin/AdminModerationPage.vue')
const AdminFinancePage = () => import('@/pages/admin/AdminFinancePage.vue')
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
        { path: 'forgot-password', name: 'forgot-password', component: ForgotPasswordPage, meta: { title: 'Forgot password' } },
        { path: 'reset-password', name: 'reset-password', component: ResetPasswordPage, meta: { title: 'Reset password' } },
        { path: 'terms', name: 'terms', component: TermsPage, meta: { title: 'Terms of service' } },
        { path: 'privacy', name: 'privacy', component: PrivacyPage, meta: { title: 'Privacy policy' } },
      ],
    },
    {
      path: '/payment',
      component: PublicLayout,
      children: [
        { path: 'success', name: 'payment-success', component: PaymentResultPage, meta: { title: 'Payment success' } },
        { path: 'cancel', name: 'payment-cancel', component: PaymentResultPage, meta: { title: 'Payment cancelled' } },
      ],
    },
    {
      path: '/courses',
      component: DashboardLayout,
      meta: { previewRole: 'STUDENT' },
      children: [
        { path: '', name: 'catalog', component: CatalogPage, meta: { title: 'Explore courses', previewRole: 'STUDENT' } },
        { path: ':courseId', name: 'course-detail', component: CourseDetailPage, meta: { title: 'Course detail', previewRole: 'STUDENT' } },
      ],
    },
    {
      path: '/student',
      component: DashboardLayout,
      meta: { roles: ['STUDENT'], previewRole: 'STUDENT' },
      children: [
        { path: '', redirect: { name: 'student-home' } },
        { path: 'home', name: 'student-home', component: StudentHomePage, meta: { title: 'Student home', roles: ['STUDENT'], previewRole: 'STUDENT' } },
        { path: 'learn', name: 'student-learn', component: StudentLearnPage, meta: { title: 'Learn', roles: ['STUDENT'], previewRole: 'STUDENT' } },
        { path: 'practice', name: 'student-practice', component: StudentPracticePage, meta: { title: 'Practice', roles: ['STUDENT'], previewRole: 'STUDENT' } },
        { path: 'quiz/:quizId?', name: 'student-quiz', component: StudentQuizPage, meta: { title: 'Quiz attempt', roles: ['STUDENT'], previewRole: 'STUDENT' } },
        { path: 'classes', name: 'student-classes', component: StudentClassesPage, meta: { title: 'Classes', roles: ['STUDENT'], previewRole: 'STUDENT' } },
        { path: 'achievements', name: 'student-achievements', component: StudentAchievementsPage, meta: { title: 'Achievements', roles: ['STUDENT'], previewRole: 'STUDENT' } },
        { path: 'rewards', name: 'student-rewards', component: StudentRewardsPage, meta: { title: 'Rewards', roles: ['STUDENT'], previewRole: 'STUDENT' } },
        { path: 'ai-helper', name: 'student-ai-helper', component: StudentAiHelperPage, meta: { title: 'AI helper', roles: ['STUDENT'], previewRole: 'STUDENT' } },
        { path: 'profile', name: 'student-profile', component: StudentProfilePage, meta: { title: 'Profile', roles: ['STUDENT'], previewRole: 'STUDENT' } },
      ],
    },
    {
      path: '/teacher',
      component: DashboardLayout,
      meta: { roles: ['TEACHER'], previewRole: 'TEACHER' },
      children: [
        { path: '', redirect: { name: 'teacher-dashboard' } },
        { path: 'dashboard', name: 'teacher-dashboard', component: TeacherDashboardPage, meta: { title: 'Teacher dashboard', roles: ['TEACHER'], previewRole: 'TEACHER' } },
        { path: 'content', name: 'teacher-content', component: TeacherContentPage, meta: { title: 'Content management', roles: ['TEACHER'], previewRole: 'TEACHER' } },
        { path: 'classes', name: 'teacher-classes', component: TeacherClassesPage, meta: { title: 'Teacher classes', roles: ['TEACHER'], previewRole: 'TEACHER' } },
        { path: 'students', name: 'teacher-students', component: TeacherStudentsPage, meta: { title: 'Teacher students', roles: ['TEACHER'], previewRole: 'TEACHER' } },
        { path: 'feedback', name: 'teacher-feedback', component: TeacherFeedbackPage, meta: { title: 'Feedback center', roles: ['TEACHER'], previewRole: 'TEACHER' } },
        { path: 'profile', name: 'teacher-profile', component: TeacherProfilePage, meta: { title: 'Teacher profile', roles: ['TEACHER'], previewRole: 'TEACHER' } },
        { path: 'earnings', name: 'teacher-earnings', component: TeacherEarningsPage, meta: { title: 'Teacher earnings', roles: ['TEACHER'], previewRole: 'TEACHER' } },
      ],
    },
    {
      path: '/admin',
      component: DashboardLayout,
      meta: { roles: ['ADMIN'], previewRole: 'ADMIN' },
      children: [
        { path: '', redirect: { name: 'admin-dashboard' } },
        { path: 'dashboard', name: 'admin-dashboard', component: AdminDashboardPage, meta: { title: 'Admin dashboard', roles: ['ADMIN'], previewRole: 'ADMIN' } },
        { path: 'users', name: 'admin-users', component: AdminUsersPage, meta: { title: 'Users', roles: ['ADMIN'], previewRole: 'ADMIN' } },
        { path: 'teacher-verification', name: 'admin-teacher-verification', component: AdminTeacherVerificationPage, meta: { title: 'Teacher verification', roles: ['ADMIN'], previewRole: 'ADMIN' } },
        { path: 'content', name: 'admin-content', component: AdminContentPage, meta: { title: 'Content management', roles: ['ADMIN'], previewRole: 'ADMIN' } },
        { path: 'activities', name: 'admin-activities', component: AdminActivityLibraryPage, meta: { title: 'Activity library', roles: ['ADMIN'], previewRole: 'ADMIN' } },
        { path: 'moderation', name: 'admin-moderation', component: AdminModerationPage, meta: { title: 'Reports and moderation', roles: ['ADMIN'], previewRole: 'ADMIN' } },
        { path: 'finance', name: 'admin-finance', component: AdminFinancePage, meta: { title: 'Finance', roles: ['ADMIN'], previewRole: 'ADMIN' } },
        { path: 'analytics', name: 'admin-analytics', component: AdminAnalyticsPage, meta: { title: 'Analytics', roles: ['ADMIN'], previewRole: 'ADMIN' } },
        { path: 'settings', name: 'admin-settings', component: AdminSettingsPage, meta: { title: 'Settings and audit', roles: ['ADMIN'], previewRole: 'ADMIN' } },
      ],
    },
    { path: '/unauthorized', name: 'unauthorized', component: UnauthorizedPage, meta: { title: 'Unauthorized' } },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundPage, meta: { title: 'Page not found' } },
  ],
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, behavior: 'smooth' }
    return { top: 0 }
  },
})

const AUTH_ROUTE_NAMES: ReadonlySet<string> = new Set(['auth', 'forgot-password', 'reset-password'])

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.bootstrap()

  document.title = to.meta.title ? `${to.meta.title} | MandarinSpark` : 'MandarinSpark'

  const requiredRoles = to.meta.roles ?? []

  if (typeof to.name === 'string' && AUTH_ROUTE_NAMES.has(to.name) && auth.isAuthenticated) {
    return defaultRouteForRole(auth.activeRole)
  }

  if (requiredRoles.length === 0) {
    return true
  }

  if (auth.isPreviewSession && to.meta.previewRole && requiredRoles.includes(auth.previewRole as UserRole)) {
    return true
  }

  if (!auth.isAuthenticated) {
    return { name: 'auth', query: { redirect: to.fullPath } }
  }

  if (!requiredRoles.some((role) => auth.hasRole(role))) {
    return { name: 'unauthorized' }
  }

  return true
})

export { router }
