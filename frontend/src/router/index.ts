import { createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw } from 'vue-router'

import AdminLayout from '../layouts/AdminLayout.vue'
import PublicLayout from '../layouts/PublicLayout.vue'
import StudentLayout from '../layouts/StudentLayout.vue'
import TeacherLayout from '../layouts/TeacherLayout.vue'
import { i18n } from '../i18n'
import AdminContentApprovalPage from '../pages/admin/AdminContentApprovalPage.vue'
import AdminContentDetailReviewPage from '../pages/admin/AdminContentDetailReviewPage.vue'
import AdminContentModerationListPage from '../pages/admin/AdminContentModerationListPage.vue'
import AdminAiUsageLogsPage from '../pages/admin/AdminAiUsageLogsPage.vue'
import CommissionSettingPage from '../pages/admin/CommissionSettingPage.vue'
import AdminDashboardPage from '../pages/admin/AdminDashboardPage.vue'
import PaymentManagementPage from '../pages/admin/PaymentManagementPage.vue'
import PayoutManagementPage from '../pages/admin/PayoutManagementPage.vue'
import RefundManagementPage from '../pages/admin/RefundManagementPage.vue'
import AdminStudentListPage from '../pages/admin/AdminStudentListPage.vue'
import SubscriptionPlanManagementPage from '../pages/admin/SubscriptionPlanManagementPage.vue'
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
import AiPronunciationFeedbackPage from '../pages/student/AiPronunciationFeedbackPage.vue'
import AiQuizGeneratorPage from '../pages/student/AiQuizGeneratorPage.vue'
import AiSentencePracticePage from '../pages/student/AiSentencePracticePage.vue'
import AiStoryGeneratorPage from '../pages/student/AiStoryGeneratorPage.vue'
import AiTutorPage from '../pages/student/AiTutorPage.vue'
import AiVocabularyHelperPage from '../pages/student/AiVocabularyHelperPage.vue'
import AiWritingFeedbackPage from '../pages/student/AiWritingFeedbackPage.vue'
import CartPage from '../pages/student/CartPage.vue'
import LessonPlayerPage from '../pages/student/LessonPlayerPage.vue'
import ListenAndChoosePage from '../pages/student/ListenAndChoosePage.vue'
import MatchingGamePage from '../pages/student/MatchingGamePage.vue'
import PaymentHistoryPage from '../pages/student/PaymentHistoryPage.vue'
import PaymentResultPage from '../pages/student/PaymentResultPage.vue'
import ProgressPage from '../pages/student/ProgressPage.vue'
import QuizPage from '../pages/student/QuizPage.vue'
import QuizResultPage from '../pages/student/QuizResultPage.vue'
import RefundHistoryPage from '../pages/student/RefundHistoryPage.vue'
import RewardsPage from '../pages/student/RewardsPage.vue'
import StudentDashboardPage from '../pages/student/StudentDashboardPage.vue'
import StudentLessonsPage from '../pages/student/StudentLessonsPage.vue'
import SubscriptionPage from '../pages/student/SubscriptionPage.vue'
import WrongAnswerReviewPage from '../pages/student/WrongAnswerReviewPage.vue'
import EarningsDashboardPage from '../pages/teacher/EarningsDashboardPage.vue'
import FlashcardEditorPage from '../pages/teacher/FlashcardEditorPage.vue'
import LessonEditorPage from '../pages/teacher/LessonEditorPage.vue'
import LessonManagementPage from '../pages/teacher/LessonManagementPage.vue'
import ListeningExerciseEditorPage from '../pages/teacher/ListeningExerciseEditorPage.vue'
import MatchingGameEditorPage from '../pages/teacher/MatchingGameEditorPage.vue'
import ModuleManagementPage from '../pages/teacher/ModuleManagementPage.vue'
import PackageEditorPage from '../pages/teacher/PackageEditorPage.vue'
import PackageManagementPage from '../pages/teacher/PackageManagementPage.vue'
import TeacherAiQuizGeneratorPage from '../pages/teacher/TeacherAiQuizGeneratorPage.vue'
import TeacherAiStoryGeneratorPage from '../pages/teacher/TeacherAiStoryGeneratorPage.vue'
import TeacherAiTeacherAssistantPage from '../pages/teacher/TeacherAiTeacherAssistantPage.vue'
import TeacherAiVocabularyHelperPage from '../pages/teacher/TeacherAiVocabularyHelperPage.vue'
import PayoutRequestPage from '../pages/teacher/PayoutRequestPage.vue'
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
    path: 'cart',
    name: 'student-cart',
    component: CartPage,
    meta: { titleKey: 'nav.cart' },
  },
  {
    path: 'payments',
    name: 'student-payments',
    component: PaymentHistoryPage,
    meta: { titleKey: 'nav.paymentHistory' },
  },
  {
    path: 'payment-result',
    name: 'student-payment-result',
    component: PaymentResultPage,
    meta: { titleKey: 'commerce.paymentResultTitle', navKey: 'student-payments' },
  },
  {
    path: 'subscription',
    name: 'student-subscription',
    component: SubscriptionPage,
    meta: { titleKey: 'nav.subscription' },
  },
  {
    path: 'refunds',
    name: 'student-refunds',
    component: RefundHistoryPage,
    meta: { titleKey: 'commerce.refundTitle' },
  },
  {
    path: 'ai/tutor',
    name: 'student-ai-tutor',
    component: AiTutorPage,
    meta: { titleKey: 'nav.aiTutor' },
  },
  {
    path: 'ai/vocabulary',
    name: 'student-ai-vocabulary',
    component: AiVocabularyHelperPage,
    meta: { titleKey: 'nav.aiVocabulary' },
  },
  {
    path: 'ai/sentence-practice',
    name: 'student-ai-sentence-practice',
    component: AiSentencePracticePage,
    meta: { titleKey: 'nav.aiSentencePractice' },
  },
  {
    path: 'ai/quiz-generator',
    name: 'student-ai-quiz-generator',
    component: AiQuizGeneratorPage,
    meta: { titleKey: 'nav.aiQuizGenerator' },
  },
  {
    path: 'ai/story-generator',
    name: 'student-ai-story-generator',
    component: AiStoryGeneratorPage,
    meta: { titleKey: 'nav.aiStoryGenerator' },
  },
  {
    path: 'ai/pronunciation-feedback',
    name: 'student-ai-pronunciation-feedback',
    component: AiPronunciationFeedbackPage,
    meta: { titleKey: 'nav.pronunciationFeedback' },
  },
  {
    path: 'ai/writing-feedback',
    name: 'student-ai-writing-feedback',
    component: AiWritingFeedbackPage,
    meta: { titleKey: 'nav.writingFeedback' },
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
  {
    path: 'ai/assistant',
    name: 'teacher-ai-assistant',
    component: TeacherAiTeacherAssistantPage,
    meta: { titleKey: 'nav.aiTeacherAssistant' },
  },
  {
    path: 'ai/vocabulary',
    name: 'teacher-ai-vocabulary',
    component: TeacherAiVocabularyHelperPage,
    meta: { titleKey: 'nav.aiVocabulary' },
  },
  {
    path: 'ai/quiz-generator',
    name: 'teacher-ai-quiz-generator',
    component: TeacherAiQuizGeneratorPage,
    meta: { titleKey: 'nav.aiQuizGenerator' },
  },
  {
    path: 'ai/story-generator',
    name: 'teacher-ai-story-generator',
    component: TeacherAiStoryGeneratorPage,
    meta: { titleKey: 'nav.aiStoryGenerator' },
  },
  {
    path: 'earnings',
    name: 'teacher-earnings',
    component: EarningsDashboardPage,
    meta: { titleKey: 'nav.earnings' },
  },
  {
    path: 'payouts',
    name: 'teacher-payouts',
    component: PayoutRequestPage,
    meta: { titleKey: 'nav.payoutRequests' },
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
  {
    path: 'ai-usage-logs',
    name: 'admin-ai-usage-logs',
    component: AdminAiUsageLogsPage,
    meta: { titleKey: 'nav.aiUsageLogs' },
  },
  {
    path: 'payments',
    name: 'admin-payments',
    component: PaymentManagementPage,
    meta: { titleKey: 'nav.paymentManagement' },
  },
  {
    path: 'refunds',
    name: 'admin-refunds',
    component: RefundManagementPage,
    meta: { titleKey: 'nav.refundManagement' },
  },
  {
    path: 'payouts',
    name: 'admin-payouts',
    component: PayoutManagementPage,
    meta: { titleKey: 'nav.payoutManagement' },
  },
  {
    path: 'subscription-plans',
    name: 'admin-subscription-plans',
    component: SubscriptionPlanManagementPage,
    meta: { titleKey: 'nav.subscriptionPlans' },
  },
  {
    path: 'commission-settings',
    name: 'admin-commission-settings',
    component: CommissionSettingPage,
    meta: { titleKey: 'commerce.commissionTitle' },
  },
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
    path: '/payment/success',
    name: 'payment-success',
    component: PaymentResultPage,
    meta: { titleKey: 'commerce.paymentResultTitle', requiresAuth: true, roles: ['STUDENT'], navKey: 'student-payments' },
  },
  {
    path: '/payment/cancel',
    name: 'payment-cancel',
    component: PaymentResultPage,
    meta: { titleKey: 'commerce.paymentCancelTitle', requiresAuth: true, roles: ['STUDENT'], navKey: 'student-payments' },
  },
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
