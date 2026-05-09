import { createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw } from 'vue-router'

import AdminLayout from '../layouts/AdminLayout.vue'
import PublicLayout from '../layouts/PublicLayout.vue'
import StudentLayout from '../layouts/StudentLayout.vue'
import TeacherLayout from '../layouts/TeacherLayout.vue'
import { i18n } from '../i18n'
import AdminContentApprovalPage from '../pages/admin/AdminContentApprovalPage.vue'
import AdminContentDetailReviewPage from '../pages/admin/AdminContentDetailReviewPage.vue'
import AdminContentModerationListPage from '../pages/admin/AdminContentModerationListPage.vue'
import AdminDashboardPage from '../pages/admin/AdminDashboardPage.vue'
import AdminStudentListPage from '../pages/admin/AdminStudentListPage.vue'
import AdminTeacherApprovalListPage from '../pages/admin/AdminTeacherApprovalListPage.vue'
import AdminTeacherVerificationDetailPage from '../pages/admin/AdminTeacherVerificationDetailPage.vue'
import AdminUserManagementPage from '../pages/admin/AdminUserManagementPage.vue'
import PlaceholderWorkspacePage from '../components/common/PlaceholderWorkspacePage.vue'
import AiDisclaimerPage from '../pages/public/AiDisclaimerPage.vue'
import ChildSafetyPage from '../pages/public/ChildSafetyPage.vue'
import CourseDetailPage from '../pages/public/CourseDetailPage.vue'
import CourseDiscoveryPage from '../pages/public/CourseDiscoveryPage.vue'
import FAQPage from '../pages/public/FAQPage.vue'
import LandingPage from '../pages/public/LandingPage.vue'
import LoginPage from '../pages/public/LoginPage.vue'
import PrivacyPage from '../pages/public/PrivacyPage.vue'
import RegisterPage from '../pages/public/RegisterPage.vue'
import TeacherPublicProfilePage from '../pages/public/TeacherPublicProfilePage.vue'
import TermsPage from '../pages/public/TermsPage.vue'
import CharacterTracingPage from '../pages/student/CharacterTracingPage.vue'
import FlashcardPracticePage from '../pages/student/FlashcardPracticePage.vue'
import LessonPlayerPage from '../pages/student/LessonPlayerPage.vue'
import ListenAndChoosePage from '../pages/student/ListenAndChoosePage.vue'
import MatchingGamePage from '../pages/student/MatchingGamePage.vue'
import ProgressPage from '../pages/student/ProgressPage.vue'
import QuizPage from '../pages/student/QuizPage.vue'
import QuizResultPage from '../pages/student/QuizResultPage.vue'
import RewardsPage from '../pages/student/RewardsPage.vue'
import StudentDashboardPage from '../pages/student/StudentDashboardPage.vue'
import StudentLessonsPage from '../pages/student/StudentLessonsPage.vue'
import WrongAnswerReviewPage from '../pages/student/WrongAnswerReviewPage.vue'
import FlashcardEditorPage from '../pages/teacher/FlashcardEditorPage.vue'
import LessonEditorPage from '../pages/teacher/LessonEditorPage.vue'
import LessonManagementPage from '../pages/teacher/LessonManagementPage.vue'
import ListeningExerciseEditorPage from '../pages/teacher/ListeningExerciseEditorPage.vue'
import MatchingGameEditorPage from '../pages/teacher/MatchingGameEditorPage.vue'
import ModuleManagementPage from '../pages/teacher/ModuleManagementPage.vue'
import PackageEditorPage from '../pages/teacher/PackageEditorPage.vue'
import PackageManagementPage from '../pages/teacher/PackageManagementPage.vue'
import QuizEditorPage from '../pages/teacher/QuizEditorPage.vue'
import QuizResultsViewerPage from '../pages/teacher/QuizResultsViewerPage.vue'
import TeacherDashboardPage from '../pages/teacher/TeacherDashboardPage.vue'
import TeacherProfilePage from '../pages/teacher/TeacherProfilePage.vue'
import TeacherVerificationUploadPage from '../pages/teacher/TeacherVerificationUploadPage.vue'
import TracingExerciseEditorPage from '../pages/teacher/TracingExerciseEditorPage.vue'
import { pinia } from '../stores'
import { useAuthStore, type UserRole } from '../stores/authStore'

declare module 'vue-router' {
  interface RouteMeta {
    titleKey?: string
    requiresAuth?: boolean
    roles?: UserRole[]
    guestOnly?: boolean
    navKey?: string
  }
}

function createPlaceholderRoute(
  path: string,
  name: string,
  titleKey: string,
  accent: 'student' | 'teacher' | 'admin',
  eyebrow: string,
  title: string,
  description: string,
  bullets: string[],
): RouteRecordRaw {
  return {
    path,
    name,
    component: PlaceholderWorkspacePage,
    props: {
      eyebrow,
      title,
      description,
      bullets,
      accent,
    },
    meta: { titleKey },
  }
}

const studentChildren: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'student-dashboard',
    component: StudentDashboardPage,
    meta: { titleKey: 'nav.studentDashboard' },
  },
  {
    path: 'lessons',
    name: 'student-lessons',
    component: StudentLessonsPage,
    meta: { titleKey: 'nav.studentLessons' },
  },
  {
    path: 'lessons/:lessonId',
    name: 'student-lesson-player',
    component: LessonPlayerPage,
    meta: { titleKey: 'learning.lessonPlayerTitle', navKey: 'student-lessons' },
  },
  {
    path: 'lessons/:lessonId/flashcards',
    name: 'student-flashcards',
    component: FlashcardPracticePage,
    meta: { titleKey: 'learning.flashcardPracticeTitle', navKey: 'student-lessons' },
  },
  {
    path: 'quizzes/:quizId',
    name: 'student-quiz',
    component: QuizPage,
    meta: { titleKey: 'learning.quizPageTitle', navKey: 'student-lessons' },
  },
  {
    path: 'quiz-attempts/:attemptId',
    name: 'student-quiz-result',
    component: QuizResultPage,
    meta: { titleKey: 'learning.quizResultTitle', navKey: 'student-lessons' },
  },
  {
    path: 'matching-games/:gameId',
    name: 'student-matching-game',
    component: MatchingGamePage,
    meta: { titleKey: 'learning.matchingPlayTitle', navKey: 'student-lessons' },
  },
  {
    path: 'listening-exercises/:exerciseId',
    name: 'student-listening',
    component: ListenAndChoosePage,
    meta: { titleKey: 'learning.listeningPlayTitle', navKey: 'student-lessons' },
  },
  {
    path: 'tracing-exercises/:exerciseId',
    name: 'student-tracing',
    component: CharacterTracingPage,
    meta: { titleKey: 'learning.tracingPlayTitle', navKey: 'student-lessons' },
  },
  {
    path: 'progress',
    name: 'student-progress',
    component: ProgressPage,
    meta: { titleKey: 'nav.progress' },
  },
  {
    path: 'rewards',
    name: 'student-rewards',
    component: RewardsPage,
    meta: { titleKey: 'nav.rewards' },
  },
  {
    path: 'wrong-answers',
    name: 'student-wrong-answers',
    component: WrongAnswerReviewPage,
    meta: { titleKey: 'nav.wrongAnswers' },
  },
]

const teacherChildren: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'teacher-dashboard',
    component: TeacherDashboardPage,
    meta: { titleKey: 'nav.teacherDashboard' },
  },
  {
    path: 'profile',
    name: 'teacher-profile',
    component: TeacherProfilePage,
    meta: { titleKey: 'nav.teacherProfile' },
  },
  {
    path: 'verification-status',
    name: 'teacher-verification-status',
    component: TeacherVerificationUploadPage,
    meta: { titleKey: 'nav.teacherVerificationStatus' },
  },
  {
    path: 'verification-documents',
    name: 'teacher-verification-documents',
    component: TeacherVerificationUploadPage,
    meta: { titleKey: 'nav.uploadVerificationDocuments' },
  },
  {
    path: 'modules',
    name: 'teacher-modules',
    component: ModuleManagementPage,
    meta: { titleKey: 'nav.moduleManagement' },
  },
  {
    path: 'lessons',
    name: 'teacher-lessons',
    component: LessonManagementPage,
    meta: { titleKey: 'nav.lessonManagement' },
  },
  {
    path: 'lessons/new',
    name: 'teacher-lesson-new',
    component: LessonEditorPage,
    meta: { titleKey: 'content.lessonEditorTitle', navKey: 'teacher-lessons' },
  },
  {
    path: 'lessons/:id/edit',
    name: 'teacher-lesson-edit',
    component: LessonEditorPage,
    meta: { titleKey: 'content.lessonEditorTitle', navKey: 'teacher-lessons' },
  },
  {
    path: 'packages',
    name: 'teacher-packages',
    component: PackageManagementPage,
    meta: { titleKey: 'nav.packageManagement' },
  },
  {
    path: 'packages/new',
    name: 'teacher-package-new',
    component: PackageEditorPage,
    meta: { titleKey: 'content.packageEditorTitle', navKey: 'teacher-packages' },
  },
  {
    path: 'packages/:id/edit',
    name: 'teacher-package-edit',
    component: PackageEditorPage,
    meta: { titleKey: 'content.packageEditorTitle', navKey: 'teacher-packages' },
  },
  {
    path: 'quizzes',
    name: 'teacher-quizzes',
    component: QuizEditorPage,
    meta: { titleKey: 'nav.quizManagement' },
  },
  {
    path: 'flashcards',
    name: 'teacher-flashcards',
    component: FlashcardEditorPage,
    meta: { titleKey: 'nav.flashcardManagement' },
  },
  {
    path: 'matching-games',
    name: 'teacher-matching-games',
    component: MatchingGameEditorPage,
    meta: { titleKey: 'nav.matchingGameManagement' },
  },
  {
    path: 'listening-exercises',
    name: 'teacher-listening-exercises',
    component: ListeningExerciseEditorPage,
    meta: { titleKey: 'nav.listeningExerciseManagement' },
  },
  {
    path: 'tracing-exercises',
    name: 'teacher-tracing-exercises',
    component: TracingExerciseEditorPage,
    meta: { titleKey: 'nav.tracingExerciseManagement' },
  },
  {
    path: 'quiz-results',
    name: 'teacher-quiz-results',
    component: QuizResultsViewerPage,
    meta: { titleKey: 'nav.quizResults' },
  },
]

const adminChildren: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'admin-dashboard',
    component: AdminDashboardPage,
    meta: { titleKey: 'nav.adminDashboard' },
  },
  {
    path: 'users',
    name: 'admin-users',
    component: AdminUserManagementPage,
    meta: { titleKey: 'nav.userManagement' },
  },
  {
    path: 'students',
    name: 'admin-students',
    component: AdminStudentListPage,
    meta: { titleKey: 'nav.students' },
  },
  {
    path: 'teacher-approval',
    name: 'admin-teacher-approval',
    component: AdminTeacherApprovalListPage,
    meta: { titleKey: 'nav.teacherApproval' },
  },
  {
    path: 'teacher-approval/:id',
    name: 'admin-teacher-approval-detail',
    component: AdminTeacherVerificationDetailPage,
    meta: { titleKey: 'nav.teacherApproval', navKey: 'admin-teacher-approval' },
  },
  {
    path: 'content/approvals',
    name: 'admin-content-approvals',
    component: AdminContentApprovalPage,
    meta: { titleKey: 'nav.contentApproval' },
  },
  {
    path: 'content/approvals/:type/:id',
    name: 'admin-content-approval-detail',
    component: AdminContentDetailReviewPage,
    meta: { titleKey: 'nav.contentApproval', navKey: 'admin-content-approvals' },
  },
  {
    path: 'content/moderation',
    name: 'admin-content-moderation',
    component: AdminContentModerationListPage,
    meta: { titleKey: 'nav.contentModeration' },
  },
  createPlaceholderRoute(
    'ai-usage-logs',
    'admin-ai-usage-logs',
    'nav.aiUsageLogs',
    'admin',
    'AI Usage Logs',
    'Detailed AI usage management is deferred to a later phase',
    'This page will later expand the operational view of provider usage, features, and moderation-sensitive prompts.',
    ['Track AI feature volume', 'Inspect provider usage trends', 'Review operational safeguards'],
  ),
  createPlaceholderRoute(
    'payments',
    'admin-payments',
    'nav.paymentManagement',
    'admin',
    'Payment Management',
    'Payment management is prepared for the commerce phase',
    'This page will later host transaction review, order lookups, and Stripe sandbox monitoring.',
    ['Review payment records', 'Inspect failed transactions', 'Track revenue by period'],
  ),
  createPlaceholderRoute(
    'refunds',
    'admin-refunds',
    'nav.refundManagement',
    'admin',
    'Refund Management',
    'Refund workflows will appear here later',
    'Admins will later review refund requests, decisions, and refund-processing history from this section.',
    ['View pending refund requests', 'Approve or reject cases', 'Track refund outcomes'],
  ),
  createPlaceholderRoute(
    'payouts',
    'admin-payouts',
    'nav.payoutManagement',
    'admin',
    'Payout Management',
    'Payout review workflows will be added later',
    'This page is ready for teacher payout requests, approvals, and finance notes.',
    ['Review payout requests', 'Approve or reject requests', 'Track paid-out records'],
  ),
  createPlaceholderRoute(
    'subscription-plans',
    'admin-subscription-plans',
    'nav.subscriptionPlans',
    'admin',
    'Subscription Plans',
    'Subscription management will be implemented later',
    'This section will later support plan pricing, activation, benefits, and premium access configuration.',
    ['Manage Free, Basic, Premium, and Family plans', 'Update pricing and benefits', 'Enable or disable plan visibility'],
  ),
  createPlaceholderRoute(
    'reports',
    'admin-reports',
    'nav.reports',
    'admin',
    'Reports',
    'Reporting workflows are staged for a later phase',
    'This page will later support review of content issues, inappropriate material reports, and technical complaints.',
    ['Review pending reports', 'Take moderation actions', 'Track resolution history'],
  ),
  createPlaceholderRoute(
    'reviews',
    'admin-reviews',
    'nav.reviewsModeration',
    'admin',
    'Reviews Moderation',
    'Review moderation tools will be added later',
    'Admins will later inspect student reviews, hide unsuitable content, and preserve moderation logs here.',
    ['Inspect public reviews', 'Hide inappropriate comments', 'Keep moderation records'],
  ),
  createPlaceholderRoute(
    'notifications',
    'admin-notifications',
    'nav.notifications',
    'admin',
    'Notifications',
    'Admin notifications are prepared for later phases',
    'This area will later gather platform alerts for approvals, tickets, reports, and financial events.',
    ['View platform alerts', 'Prioritize pending actions', 'Track operational reminders'],
  ),
  createPlaceholderRoute(
    'support-tickets',
    'admin-support-tickets',
    'nav.supportTickets',
    'admin',
    'Support Tickets',
    'Ticket management will be implemented later',
    'This page will later allow admins to triage support tickets and respond to students and teachers.',
    ['Review open tickets', 'Reply and update status', 'Close resolved issues'],
  ),
  createPlaceholderRoute(
    'activity-logs',
    'admin-activity-logs',
    'nav.activityLogs',
    'admin',
    'Activity Logs',
    'Detailed activity log browsing will expand in a later phase',
    'This section is ready for login history, approval actions, and moderation audit trails.',
    ['Review audit events', 'Filter by action type', 'Inspect admin decisions'],
  ),
  createPlaceholderRoute(
    'settings',
    'admin-settings',
    'nav.systemSettings',
    'admin',
    'System Settings',
    'Platform settings management will be added later',
    'This workspace is prepared for commission rate, platform toggles, and operational configuration controls.',
    ['Adjust commission and platform defaults', 'Review environment-linked settings', 'Manage feature toggle scaffolds'],
  ),
]

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: PublicLayout,
    children: [
      {
        path: '',
        name: 'landing',
        component: LandingPage,
        meta: { titleKey: 'nav.home' },
      },
      {
        path: 'login',
        name: 'login',
        component: LoginPage,
        meta: { titleKey: 'nav.login', guestOnly: true },
      },
      {
        path: 'register',
        name: 'register',
        component: RegisterPage,
        meta: { titleKey: 'nav.register', guestOnly: true },
      },
      {
        path: 'courses',
        name: 'courses',
        component: CourseDiscoveryPage,
        meta: { titleKey: 'nav.courses' },
      },
      {
        path: 'courses/:id',
        name: 'course-detail',
        component: CourseDetailPage,
        meta: { titleKey: 'content.courseDetailTitle' },
      },
      {
        path: 'faq',
        name: 'faq',
        component: FAQPage,
        meta: { titleKey: 'nav.faq' },
      },
      {
        path: 'terms',
        name: 'terms',
        component: TermsPage,
        meta: { titleKey: 'nav.terms' },
      },
      {
        path: 'privacy',
        name: 'privacy',
        component: PrivacyPage,
        meta: { titleKey: 'nav.privacy' },
      },
      {
        path: 'child-safety',
        name: 'child-safety',
        component: ChildSafetyPage,
        meta: { titleKey: 'nav.childSafety' },
      },
      {
        path: 'ai-disclaimer',
        name: 'ai-disclaimer',
        component: AiDisclaimerPage,
        meta: { titleKey: 'nav.aiDisclaimer' },
      },
      {
        path: 'teachers/:id',
        name: 'teacher-public-profile',
        component: TeacherPublicProfilePage,
        meta: { titleKey: 'nav.teacherPublicProfile' },
      },
    ],
  },
  {
    path: '/student',
    component: StudentLayout,
    meta: { requiresAuth: true, roles: ['STUDENT'], titleKey: 'nav.studentDashboard' },
    children: studentChildren,
  },
  {
    path: '/teacher',
    component: TeacherLayout,
    meta: { requiresAuth: true, roles: ['TEACHER'], titleKey: 'nav.teacherDashboard' },
    children: teacherChildren,
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, roles: ['ADMIN'], titleKey: 'nav.adminDashboard' },
    children: adminChildren,
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore(pinia)

  if (!authStore.initialized && authStore.token) {
    try {
      await authStore.fetchCurrentUser()
    } catch {
      // Axios interceptor handles redirect cleanup.
    }
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    return redirectByRole(authStore.roles[0] ?? null)
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.roles?.length && !to.meta.roles.some((role) => authStore.hasRole(role))) {
    return redirectByRole(authStore.roles[0] ?? null)
  }

  return true
})

router.afterEach((to: RouteLocationNormalized) => {
  const titleKey = typeof to.meta.titleKey === 'string' ? to.meta.titleKey : 'common.appName'
  const pageTitle = i18n.global.t(titleKey)
  document.title = `${pageTitle} | ${i18n.global.t('common.appName')}`
})

function redirectByRole(role: UserRole | null) {
  if (role === 'STUDENT') {
    return { name: 'student-dashboard' }
  }

  if (role === 'TEACHER') {
    return { name: 'teacher-dashboard' }
  }

  if (role === 'ADMIN') {
    return { name: 'admin-dashboard' }
  }

  return { name: 'landing' }
}
