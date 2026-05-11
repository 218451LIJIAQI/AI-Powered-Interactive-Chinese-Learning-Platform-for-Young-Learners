import { createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw } from 'vue-router'

import AdminLayout from '../layouts/AdminLayout.vue'
import PublicLayout from '../layouts/PublicLayout.vue'
import StudentLayout from '../layouts/StudentLayout.vue'
import TeacherLayout from '../layouts/TeacherLayout.vue'
import { i18n } from '../i18n'
import { pinia } from '../stores'
import { useAuthStore, type UserRole } from '../stores/authStore'

// ----------------------------------------------------------------------------
// Lazy-loaded page components.
//
// Each `() => import(...)` call is treated as a code-splitting boundary by
// Vite, which means each route's component lands in its own JavaScript chunk
// fetched on-demand. The combined effect is dramatic: instead of shipping all
// ~100 page components in the initial bundle, the browser only downloads the
// chunk for the route the user is actually visiting (plus any chunks that
// share dependencies, hoisted automatically by rollup).
//
// The layouts above are intentionally kept as eager imports because they are
// always rendered for an authenticated session — making them lazy would just
// add an unnecessary network round-trip on the very first navigation.
// ----------------------------------------------------------------------------

// Admin pages
const AdminContentApprovalPage = () => import('../pages/admin/AdminContentApprovalPage.vue')
const AdminContentDetailReviewPage = () => import('../pages/admin/AdminContentDetailReviewPage.vue')
const AdminContentModerationPage = () => import('../pages/admin/AdminContentModerationPage.vue')
const AdminContentModerationListPage = () => import('../pages/admin/AdminContentModerationListPage.vue')
const AdminAiUsageLogsPage = () => import('../pages/admin/AdminAiUsageLogsPage.vue')
const AdminAuditLogPage = () => import('../pages/admin/AdminAuditLogPage.vue')
const AdminLiveClassOverviewPage = () => import('../pages/admin/AdminLiveClassOverviewPage.vue')
const AdminReportDetailPage = () => import('../pages/admin/AdminReportDetailPage.vue')
const AdminReportManagementPage = () => import('../pages/admin/AdminReportManagementPage.vue')
const AdminReviewModerationPage = () => import('../pages/admin/AdminReviewModerationPage.vue')
const AdminScheduleOverviewPage = () => import('../pages/admin/AdminScheduleOverviewPage.vue')
const AdminSupportManagementPage = () => import('../pages/admin/AdminSupportManagementPage.vue')
const AdminSupportTicketDetailPage = () => import('../pages/admin/AdminSupportTicketDetailPage.vue')
const CommissionSettingPage = () => import('../pages/admin/CommissionSettingPage.vue')
const AdminDashboardPage = () => import('../pages/admin/AdminDashboardPage.vue')
const PaymentManagementPage = () => import('../pages/admin/PaymentManagementPage.vue')
const PayoutManagementPage = () => import('../pages/admin/PayoutManagementPage.vue')
const RefundManagementPage = () => import('../pages/admin/RefundManagementPage.vue')
const AdminStudentListPage = () => import('../pages/admin/AdminStudentListPage.vue')
const SubscriptionPlanManagementPage = () => import('../pages/admin/SubscriptionPlanManagementPage.vue')
const AdminTeacherApprovalListPage = () => import('../pages/admin/AdminTeacherApprovalListPage.vue')
const AdminTeacherVerificationDetailPage = () => import('../pages/admin/AdminTeacherVerificationDetailPage.vue')
const AdminUserManagementPage = () => import('../pages/admin/AdminUserManagementPage.vue')

// Shared pages (notifications, support tickets — used by every role)
const NotificationCenterPage = () => import('../pages/shared/NotificationCenterPage.vue')
const CreateSupportTicketPage = () => import('../pages/shared/CreateSupportTicketPage.vue')
const SupportTicketDetailPage = () => import('../pages/shared/SupportTicketDetailPage.vue')
const SupportTicketListPage = () => import('../pages/shared/SupportTicketListPage.vue')

// Public marketing pages
const AiDisclaimerPage = () => import('../pages/public/AiDisclaimerPage.vue')
const ChildSafetyPage = () => import('../pages/public/ChildSafetyPage.vue')
const CourseDetailPage = () => import('../pages/public/CourseDetailPage.vue')
const CourseDiscoveryPage = () => import('../pages/public/CourseDiscoveryPage.vue')
const FAQPage = () => import('../pages/public/FAQPage.vue')
const LandingPage = () => import('../pages/public/LandingPage.vue')
const LoginPage = () => import('../pages/public/LoginPage.vue')
const NotFoundPage = () => import('../pages/public/NotFoundPage.vue')
const PrivacyPage = () => import('../pages/public/PrivacyPage.vue')
const RegisterPage = () => import('../pages/public/RegisterPage.vue')
const TeacherPublicProfilePage = () => import('../pages/public/TeacherPublicProfilePage.vue')
const TermsPage = () => import('../pages/public/TermsPage.vue')

// Student pages
const CharacterTracingPage = () => import('../pages/student/CharacterTracingPage.vue')
const FlashcardPracticePage = () => import('../pages/student/FlashcardPracticePage.vue')
const AiPronunciationFeedbackPage = () => import('../pages/student/AiPronunciationFeedbackPage.vue')
const AiQuizGeneratorPage = () => import('../pages/student/AiQuizGeneratorPage.vue')
const AiSentencePracticePage = () => import('../pages/student/AiSentencePracticePage.vue')
const AiStoryGeneratorPage = () => import('../pages/student/AiStoryGeneratorPage.vue')
const AiTutorPage = () => import('../pages/student/AiTutorPage.vue')
const AiVocabularyHelperPage = () => import('../pages/student/AiVocabularyHelperPage.vue')
const AiWritingFeedbackPage = () => import('../pages/student/AiWritingFeedbackPage.vue')
const CartPage = () => import('../pages/student/CartPage.vue')
const LessonPlayerPage = () => import('../pages/student/LessonPlayerPage.vue')
const ListenAndChoosePage = () => import('../pages/student/ListenAndChoosePage.vue')
const MatchingGamePage = () => import('../pages/student/MatchingGamePage.vue')
const PaymentHistoryPage = () => import('../pages/student/PaymentHistoryPage.vue')
const PaymentResultPage = () => import('../pages/student/PaymentResultPage.vue')
const ProgressPage = () => import('../pages/student/ProgressPage.vue')
const QuizPage = () => import('../pages/student/QuizPage.vue')
const QuizResultPage = () => import('../pages/student/QuizResultPage.vue')
const RecordedLessonPlayerPage = () => import('../pages/student/RecordedLessonPlayerPage.vue')
const RefundHistoryPage = () => import('../pages/student/RefundHistoryPage.vue')
const RewardsPage = () => import('../pages/student/RewardsPage.vue')
const MyReportsPage = () => import('../pages/student/MyReportsPage.vue')
const MyReviewsPage = () => import('../pages/student/MyReviewsPage.vue')
const ReportFormPage = () => import('../pages/student/ReportFormPage.vue')
const StudentDashboardPage = () => import('../pages/student/StudentDashboardPage.vue')
const StudentFeedbackPage = () => import('../pages/student/StudentFeedbackPage.vue')
const StudentLessonsPage = () => import('../pages/student/StudentLessonsPage.vue')
const StudentLiveClassDetailPage = () => import('../pages/student/StudentLiveClassDetailPage.vue')
const StudentLiveClassListPage = () => import('../pages/student/StudentLiveClassListPage.vue')
const StudentSchedulePage = () => import('../pages/student/StudentSchedulePage.vue')
const SubscriptionPage = () => import('../pages/student/SubscriptionPage.vue')
const WrongAnswerReviewPage = () => import('../pages/student/WrongAnswerReviewPage.vue')

// Teacher pages
const EarningsDashboardPage = () => import('../pages/teacher/EarningsDashboardPage.vue')
const FlashcardEditorPage = () => import('../pages/teacher/FlashcardEditorPage.vue')
const LessonEditorPage = () => import('../pages/teacher/LessonEditorPage.vue')
const LessonManagementPage = () => import('../pages/teacher/LessonManagementPage.vue')
const ListeningExerciseEditorPage = () => import('../pages/teacher/ListeningExerciseEditorPage.vue')
const MatchingGameEditorPage = () => import('../pages/teacher/MatchingGameEditorPage.vue')
const ModuleManagementPage = () => import('../pages/teacher/ModuleManagementPage.vue')
const PackageEditorPage = () => import('../pages/teacher/PackageEditorPage.vue')
const PackageManagementPage = () => import('../pages/teacher/PackageManagementPage.vue')
const TeacherAiQuizGeneratorPage = () => import('../pages/teacher/TeacherAiQuizGeneratorPage.vue')
const TeacherAiStoryGeneratorPage = () => import('../pages/teacher/TeacherAiStoryGeneratorPage.vue')
const TeacherAiTeacherAssistantPage = () => import('../pages/teacher/TeacherAiTeacherAssistantPage.vue')
const TeacherAiVocabularyHelperPage = () => import('../pages/teacher/TeacherAiVocabularyHelperPage.vue')
const PayoutRequestPage = () => import('../pages/teacher/PayoutRequestPage.vue')
const QuizEditorPage = () => import('../pages/teacher/QuizEditorPage.vue')
const QuizResultsViewerPage = () => import('../pages/teacher/QuizResultsViewerPage.vue')
const TeacherDashboardPage = () => import('../pages/teacher/TeacherDashboardPage.vue')
const TeacherFeedbackPage = () => import('../pages/teacher/TeacherFeedbackPage.vue')
const TeacherLiveClassManagementPage = () => import('../pages/teacher/TeacherLiveClassManagementPage.vue')
const TeacherProfilePage = () => import('../pages/teacher/TeacherProfilePage.vue')
const TeacherRecordedLessonManagementPage = () => import('../pages/teacher/TeacherRecordedLessonManagementPage.vue')
const TeacherReportsRevisionRequestsPage = () => import('../pages/teacher/TeacherReportsRevisionRequestsPage.vue')
const TeacherReviewsPage = () => import('../pages/teacher/TeacherReviewsPage.vue')
const TeacherSchedulePage = () => import('../pages/teacher/TeacherSchedulePage.vue')
const TeacherVerificationUploadPage = () => import('../pages/teacher/TeacherVerificationUploadPage.vue')
const TeacherModerationRecordsPage = () => import('../pages/teacher/TeacherModerationRecordsPage.vue')
const TracingExerciseEditorPage = () => import('../pages/teacher/TracingExerciseEditorPage.vue')

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
  {
    path: 'notifications',
    name: 'student-notifications',
    component: NotificationCenterPage,
    meta: { titleKey: 'phase11.notificationsTitle' },
  },
  {
    path: 'support-tickets',
    name: 'student-support-tickets',
    component: SupportTicketListPage,
    meta: { titleKey: 'phase11.supportTicketsTitle' },
  },
  {
    path: 'support-tickets/create',
    name: 'student-support-ticket-create',
    component: CreateSupportTicketPage,
    meta: { titleKey: 'phase11.createSupportTicket', navKey: 'student-support-tickets' },
  },
  {
    path: 'support-tickets/:id',
    name: 'student-support-ticket-detail',
    component: SupportTicketDetailPage,
    meta: { titleKey: 'phase11.supportTicketDetailTitle', navKey: 'student-support-tickets' },
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
  {
    path: 'notifications',
    name: 'teacher-notifications',
    component: NotificationCenterPage,
    meta: { titleKey: 'phase11.notificationsTitle' },
  },
  {
    path: 'support-tickets',
    name: 'teacher-support-tickets',
    component: SupportTicketListPage,
    meta: { titleKey: 'phase11.supportTicketsTitle' },
  },
  {
    path: 'support-tickets/create',
    name: 'teacher-support-ticket-create',
    component: CreateSupportTicketPage,
    meta: { titleKey: 'phase11.createSupportTicket', navKey: 'teacher-support-tickets' },
  },
  {
    path: 'support-tickets/:id',
    name: 'teacher-support-ticket-detail',
    component: SupportTicketDetailPage,
    meta: { titleKey: 'phase11.supportTicketDetailTitle', navKey: 'teacher-support-tickets' },
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
  // Top-level deep-link aliases — automatically redirect into the right
  // role-scoped layout so the user always keeps their sidebar. If the user is
  // not signed in yet, fall back to /login with the original path preserved
  // (instead of returning the same path, which would create a redirect loop).
  {
    path: '/notifications',
    name: 'notifications',
    redirect: () => roleScopedRedirect('/notifications'),
    meta: { requiresAuth: true, roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  },
  {
    path: '/support-tickets',
    name: 'support-tickets',
    redirect: () => roleScopedRedirect('/support-tickets'),
    meta: { requiresAuth: true, roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  },
  {
    path: '/support-tickets/create',
    name: 'support-ticket-create',
    redirect: () => roleScopedRedirect('/support-tickets/create'),
    meta: { requiresAuth: true, roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
  },
  {
    path: '/support-tickets/:id',
    name: 'support-ticket-detail',
    redirect: (to) => roleScopedRedirect(`/support-tickets/${to.params.id}`),
    meta: { requiresAuth: true, roles: ['STUDENT', 'TEACHER', 'ADMIN'] },
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
  const translate = i18n.global.t as (key: string) => string
  const appName = translate('common.appName')
  // Prefer a per-route title when provided; otherwise fall back to the app
  // name alone (avoiding the "App Name | App Name" duplication that happens
  // when a route omits `meta.titleKey`).
  const titleKey = typeof to.meta.titleKey === 'string' ? to.meta.titleKey : null
  if (titleKey) {
    const pageTitle = translate(titleKey)
    document.title = pageTitle === appName ? appName : `${pageTitle} | ${appName}`
  } else {
    document.title = appName
  }
})

/** Returns the layout-scoped base path for the currently signed-in user. */
function currentRoleBase(): '/student' | '/teacher' | '/admin' | null {
  const auth = useAuthStore(pinia)
  if (auth.hasRole('STUDENT')) return '/student'
  if (auth.hasRole('TEACHER')) return '/teacher'
  if (auth.hasRole('ADMIN')) return '/admin'
  return null
}

/**
 * Resolves a role-aware redirect target for a shared deep link. When the user
 * is not yet authenticated, hand back a `/login` location with the original
 * path preserved as `?redirect=`, so they land directly on the deep link
 * after signing in instead of bouncing through the default role dashboard.
 *
 * The bare-suffix form (e.g. `/notifications`) is required because route
 * redirects run **before** `router.beforeEach`, so returning the same path
 * here would create an infinite redirect loop.
 */
function roleScopedRedirect(pathSuffix: string) {
  const base = currentRoleBase()
  if (base) {
    return { path: `${base}${pathSuffix}` }
  }
  return { name: 'login' as const, query: { redirect: pathSuffix } }
}

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
