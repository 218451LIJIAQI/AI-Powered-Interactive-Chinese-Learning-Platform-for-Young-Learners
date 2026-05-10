import { createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw } from 'vue-router'

import AdminLayout from '../layouts/AdminLayout.vue'
import PublicLayout from '../layouts/PublicLayout.vue'
import StudentLayout from '../layouts/StudentLayout.vue'
import TeacherLayout from '../layouts/TeacherLayout.vue'
import { i18n } from '../i18n'
import AdminContentApprovalPage from '../pages/admin/AdminContentApprovalPage.vue'
import AdminContentDetailReviewPage from '../pages/admin/AdminContentDetailReviewPage.vue'
import AdminContentModerationPage from '../pages/admin/AdminContentModerationPage.vue'
import AdminContentModerationListPage from '../pages/admin/AdminContentModerationListPage.vue'
import AdminAiUsageLogsPage from '../pages/admin/AdminAiUsageLogsPage.vue'
import AdminAuditLogPage from '../pages/admin/AdminAuditLogPage.vue'
import AdminLiveClassOverviewPage from '../pages/admin/AdminLiveClassOverviewPage.vue'
import AdminReportDetailPage from '../pages/admin/AdminReportDetailPage.vue'
import AdminReportManagementPage from '../pages/admin/AdminReportManagementPage.vue'
import AdminReviewModerationPage from '../pages/admin/AdminReviewModerationPage.vue'
import AdminScheduleOverviewPage from '../pages/admin/AdminScheduleOverviewPage.vue'
import AdminSupportManagementPage from '../pages/admin/AdminSupportManagementPage.vue'
import AdminSupportTicketDetailPage from '../pages/admin/AdminSupportTicketDetailPage.vue'
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
import NotificationCenterPage from '../pages/shared/NotificationCenterPage.vue'
import CreateSupportTicketPage from '../pages/shared/CreateSupportTicketPage.vue'
import SupportTicketDetailPage from '../pages/shared/SupportTicketDetailPage.vue'
import SupportTicketListPage from '../pages/shared/SupportTicketListPage.vue'
import AiDisclaimerPage from '../pages/public/AiDisclaimerPage.vue'
import ChildSafetyPage from '../pages/public/ChildSafetyPage.vue'
import CourseDetailPage from '../pages/public/CourseDetailPage.vue'
import CourseDiscoveryPage from '../pages/public/CourseDiscoveryPage.vue'
import FAQPage from '../pages/public/FAQPage.vue'
import LandingPage from '../pages/public/LandingPage.vue'
import LoginPage from '../pages/public/LoginPage.vue'
import NotFoundPage from '../pages/public/NotFoundPage.vue'
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
import RecordedLessonPlayerPage from '../pages/student/RecordedLessonPlayerPage.vue'
import RefundHistoryPage from '../pages/student/RefundHistoryPage.vue'
import RewardsPage from '../pages/student/RewardsPage.vue'
import MyReportsPage from '../pages/student/MyReportsPage.vue'
import MyReviewsPage from '../pages/student/MyReviewsPage.vue'
import ReportFormPage from '../pages/student/ReportFormPage.vue'
import StudentDashboardPage from '../pages/student/StudentDashboardPage.vue'
import StudentFeedbackPage from '../pages/student/StudentFeedbackPage.vue'
import StudentLessonsPage from '../pages/student/StudentLessonsPage.vue'
import StudentLiveClassDetailPage from '../pages/student/StudentLiveClassDetailPage.vue'
import StudentLiveClassListPage from '../pages/student/StudentLiveClassListPage.vue'
import StudentSchedulePage from '../pages/student/StudentSchedulePage.vue'
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
import TeacherFeedbackPage from '../pages/teacher/TeacherFeedbackPage.vue'
import TeacherLiveClassManagementPage from '../pages/teacher/TeacherLiveClassManagementPage.vue'
import TeacherProfilePage from '../pages/teacher/TeacherProfilePage.vue'
import TeacherRecordedLessonManagementPage from '../pages/teacher/TeacherRecordedLessonManagementPage.vue'
import TeacherReportsRevisionRequestsPage from '../pages/teacher/TeacherReportsRevisionRequestsPage.vue'
import TeacherReviewsPage from '../pages/teacher/TeacherReviewsPage.vue'
import TeacherSchedulePage from '../pages/teacher/TeacherSchedulePage.vue'
import TeacherVerificationUploadPage from '../pages/teacher/TeacherVerificationUploadPage.vue'
import TeacherModerationRecordsPage from '../pages/teacher/TeacherModerationRecordsPage.vue'
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
    path: 'live-classes',
    name: 'student-live-classes',
    component: StudentLiveClassListPage,
    meta: { titleKey: 'nav.liveClasses' },
  },
  {
    path: 'live-classes/:id',
    name: 'student-live-class-detail',
    component: StudentLiveClassDetailPage,
    meta: { titleKey: 'phase10.liveClassDetailTitle', navKey: 'student-live-classes' },
  },
  {
    path: 'schedule',
    name: 'student-schedule',
    component: StudentSchedulePage,
    meta: { titleKey: 'nav.schedule' },
  },
  {
    path: 'reports',
    name: 'student-reports',
    component: MyReportsPage,
    meta: { titleKey: 'phase11.myReportsTitle' },
  },
  {
    path: 'reports/create',
    name: 'student-report-create',
    component: ReportFormPage,
    meta: { titleKey: 'phase11.submitReportTitle', navKey: 'student-reports' },
  },
  {
    path: 'reviews',
    name: 'student-reviews',
    component: MyReviewsPage,
    meta: { titleKey: 'phase11.myReviewsTitle' },
  },
  {
    path: 'feedback',
    name: 'student-feedback',
    component: StudentFeedbackPage,
    meta: { titleKey: 'phase11.studentFeedbackTitle' },
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
    path: 'recorded-lessons/:id',
    name: 'student-recorded-lesson',
    component: RecordedLessonPlayerPage,
    meta: { titleKey: 'phase10.recordedLessonPlayerTitle', navKey: 'student-lessons' },
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
    path: 'live-classes',
    name: 'teacher-live-classes',
    component: TeacherLiveClassManagementPage,
    meta: { titleKey: 'nav.liveClasses' },
  },
  {
    path: 'live-classes/create',
    name: 'teacher-live-class-create',
    component: TeacherLiveClassManagementPage,
    meta: { titleKey: 'phase10.createLiveClass', navKey: 'teacher-live-classes' },
  },
  {
    path: 'live-classes/:id/edit',
    name: 'teacher-live-class-edit',
    component: TeacherLiveClassManagementPage,
    meta: { titleKey: 'phase10.editLiveClass', navKey: 'teacher-live-classes' },
  },
  {
    path: 'schedule',
    name: 'teacher-schedule',
    component: TeacherSchedulePage,
    meta: { titleKey: 'nav.schedule' },
  },
  {
    path: 'recorded-lessons',
    name: 'teacher-recorded-lessons',
    component: TeacherRecordedLessonManagementPage,
    meta: { titleKey: 'nav.recordedLessons' },
  },
  {
    path: 'reviews',
    name: 'teacher-reviews',
    component: TeacherReviewsPage,
    meta: { titleKey: 'phase11.teacherReviewsTitle' },
  },
  {
    path: 'feedback',
    name: 'teacher-feedback',
    component: TeacherFeedbackPage,
    meta: { titleKey: 'phase11.teacherFeedbackTitle' },
  },
  {
    path: 'reports',
    name: 'teacher-reports',
    component: TeacherReportsRevisionRequestsPage,
    meta: { titleKey: 'phase11.teacherReportsTitle' },
  },
  {
    path: 'moderation-records',
    name: 'teacher-moderation-records',
    component: TeacherModerationRecordsPage,
    meta: { titleKey: 'phase11.teacherModerationTitle' },
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
    path: 'live-classes',
    name: 'admin-live-classes',
    component: AdminLiveClassOverviewPage,
    meta: { titleKey: 'nav.liveClasses' },
  },
  {
    path: 'schedule',
    name: 'admin-schedule',
    component: AdminScheduleOverviewPage,
    meta: { titleKey: 'nav.schedule' },
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
  {
    path: 'reports',
    name: 'admin-reports',
    component: AdminReportManagementPage,
    meta: { titleKey: 'phase11.adminReportsTitle' },
  },
  {
    path: 'reports/:id',
    name: 'admin-report-detail',
    component: AdminReportDetailPage,
    meta: { titleKey: 'phase11.reportDetailTitle', navKey: 'admin-reports' },
  },
  {
    path: 'reviews',
    name: 'admin-reviews',
    component: AdminReviewModerationPage,
    meta: { titleKey: 'phase11.adminReviewsTitle' },
  },
  {
    path: 'notifications',
    name: 'admin-notifications',
    component: NotificationCenterPage,
    meta: { titleKey: 'phase11.notificationsTitle' },
  },
  {
    path: 'support-tickets',
    name: 'admin-support-tickets',
    component: AdminSupportManagementPage,
    meta: { titleKey: 'phase11.adminSupportTitle' },
  },
  {
    path: 'support-tickets/:id',
    name: 'admin-support-ticket-detail',
    component: AdminSupportTicketDetailPage,
    meta: { titleKey: 'phase11.supportTicketDetailTitle', navKey: 'admin-support-tickets' },
  },
  {
    path: 'audit-logs',
    alias: 'activity-logs',
    name: 'admin-audit-logs',
    component: AdminAuditLogPage,
    meta: { titleKey: 'phase11.auditLogsTitle' },
  },
  {
    path: 'moderation',
    name: 'admin-moderation',
    component: AdminContentModerationPage,
    meta: { titleKey: 'phase11.adminModerationTitle' },
  },
  {
    path: 'settings',
    name: 'admin-settings',
    component: AdminContentModerationListPage,
    meta: { titleKey: 'nav.systemSettings' },
  },
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
      {
        path: ':pathMatch(.*)*',
        name: 'not-found',
        component: NotFoundPage,
        meta: { titleKey: 'notFound.title' },
      },
    ],
  },
  {
    path: '/notifications',
    name: 'notifications',
    component: NotificationCenterPage,
    meta: { titleKey: 'phase11.notificationsTitle', requiresAuth: true, roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  },
  {
    path: '/support-tickets',
    name: 'support-tickets',
    component: SupportTicketListPage,
    meta: { titleKey: 'phase11.supportTicketsTitle', requiresAuth: true, roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  },
  {
    path: '/support-tickets/create',
    name: 'support-ticket-create',
    component: CreateSupportTicketPage,
    meta: { titleKey: 'phase11.createSupportTicket', requiresAuth: true, roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  },
  {
    path: '/support-tickets/:id',
    name: 'support-ticket-detail',
    component: SupportTicketDetailPage,
    meta: { titleKey: 'phase11.supportTicketDetailTitle', requiresAuth: true, roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
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
  const translate = i18n.global.t as (key: string) => string
  const pageTitle = translate(titleKey)
  document.title = `${pageTitle} | ${translate('common.appName')}`
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
