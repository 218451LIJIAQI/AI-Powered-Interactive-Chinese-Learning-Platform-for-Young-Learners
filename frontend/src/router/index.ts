import { createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw } from 'vue-router'

import AdminLayout from '../layouts/AdminLayout.vue'
import PublicLayout from '../layouts/PublicLayout.vue'
import StudentLayout from '../layouts/StudentLayout.vue'
import TeacherLayout from '../layouts/TeacherLayout.vue'
import { i18n } from '../i18n'
import AdminDashboardPage from '../pages/admin/AdminDashboardPage.vue'
import AiDisclaimerPage from '../pages/public/AiDisclaimerPage.vue'
import ChildSafetyPage from '../pages/public/ChildSafetyPage.vue'
import CourseDiscoveryPage from '../pages/public/CourseDiscoveryPage.vue'
import FAQPage from '../pages/public/FAQPage.vue'
import LandingPage from '../pages/public/LandingPage.vue'
import LoginPage from '../pages/public/LoginPage.vue'
import PrivacyPage from '../pages/public/PrivacyPage.vue'
import RegisterPage from '../pages/public/RegisterPage.vue'
import TermsPage from '../pages/public/TermsPage.vue'
import StudentDashboardPage from '../pages/student/StudentDashboardPage.vue'
import TeacherDashboardPage from '../pages/teacher/TeacherDashboardPage.vue'
import PlaceholderWorkspacePage from '../components/common/PlaceholderWorkspacePage.vue'
import { pinia } from '../stores'
import { useAuthStore, type UserRole } from '../stores/authStore'

declare module 'vue-router' {
  interface RouteMeta {
    titleKey?: string
    requiresAuth?: boolean
    roles?: UserRole[]
    guestOnly?: boolean
    placeholder?: {
      eyebrow: string
      title: string
      description: string
      bullets: string[]
      accent?: 'student' | 'teacher' | 'admin'
    }
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
  createPlaceholderRoute(
    'modules',
    'student-modules',
    'nav.learningModules',
    'student',
    'Learning Modules',
    'Learning modules are ready for the next content phase',
    'This area is prepared for structured level, module, and lesson browsing once content workflows are implemented.',
    ['Browse modules by level, category, and learning type', 'Continue unfinished lessons', 'Unlock free and paid content safely through backend access control'],
  ),
  createPlaceholderRoute(
    'ai-tutor',
    'student-ai-tutor',
    'nav.aiTutor',
    'student',
    'AI Tutor',
    'AI Tutor workspace coming in a later phase',
    'This student-friendly area will host free-form Chinese learning questions, lesson help, and guided practice.',
    ['Ask Chinese vocabulary and sentence questions', 'Get child-friendly explanations', 'Use lesson-level ask-AI help tools'],
  ),
  createPlaceholderRoute(
    'ai-vocabulary',
    'student-ai-vocabulary',
    'nav.aiVocabulary',
    'student',
    'AI Vocabulary Helper',
    'Vocabulary support tools are scaffolded here',
    'This page will later help students explore words in English, Bahasa Melayu, and Chinese with examples and mini practice.',
    ['Chinese meaning and pinyin support', 'English and Bahasa Melayu translations', 'Simple example sentence generation'],
  ),
  createPlaceholderRoute(
    'ai-sentence-practice',
    'student-ai-sentence-practice',
    'nav.aiSentencePractice',
    'student',
    'AI Sentence Practice',
    'Sentence practice tools are planned for a later phase',
    'This page is ready for grammar feedback, word-order suggestions, and short follow-up exercises.',
    ['Check simple Chinese sentence structure', 'Get pinyin and translations', 'Receive gentle improvement tips'],
  ),
  createPlaceholderRoute(
    'pronunciation-feedback',
    'student-pronunciation-feedback',
    'nav.pronunciationFeedback',
    'student',
    'Pronunciation Feedback',
    'Pronunciation practice will be added after the dashboard phase',
    'The layout is ready for microphone recording, playback, and child-friendly pronunciation feedback workflows.',
    ['Record voice attempts', 'Replay teacher audio or browser speech', 'Receive tone and pronunciation guidance'],
  ),
  createPlaceholderRoute(
    'writing-feedback',
    'student-writing-feedback',
    'nav.writingFeedback',
    'student',
    'Writing Feedback',
    'Writing and tracing support is prepared here',
    'This page will later host tracing submissions, writing snapshots, and encouraging feedback.',
    ['Canvas-based tracing practice', 'Writing image submission', 'Simple prototype-friendly feedback'],
  ),
  createPlaceholderRoute(
    'progress',
    'student-progress',
    'nav.progress',
    'student',
    'Progress',
    'Progress insights will expand in future phases',
    'This page is ready for charts, learning history, weak-area summaries, and AI-supported next-step advice.',
    ['Learning progress trends', 'Quiz score summaries', 'Weak-area revision guidance'],
  ),
  createPlaceholderRoute(
    'rewards',
    'student-rewards',
    'nav.rewards',
    'student',
    'Rewards',
    'Gamified rewards will grow from this foundation',
    'This page is prepared for points, badges, levels, streaks, and achievement showcases.',
    ['Badge collection', 'Level milestones', 'Daily streak achievements'],
  ),
  createPlaceholderRoute(
    'cart',
    'student-cart',
    'nav.cart',
    'student',
    'Cart',
    'Commerce pages are staged for a later phase',
    'This layout is ready for paid course items, premium AI packages, and Stripe sandbox checkout.',
    ['Review saved paid items', 'Buy now and cart checkout flow', 'Membership upgrade entry points'],
  ),
  createPlaceholderRoute(
    'payment-history',
    'student-payment-history',
    'nav.paymentHistory',
    'student',
    'Payment History',
    'Payment history will appear here in the commerce phase',
    'Students will later be able to review successful, failed, and refunded purchases safely from this page.',
    ['Payment status history', 'Refund request entry points', 'Subscription billing snapshots'],
  ),
  createPlaceholderRoute(
    'subscription',
    'student-subscription',
    'nav.subscription',
    'student',
    'Subscription',
    'Subscription membership management is prepared here',
    'This page will later show Free, Basic, Premium, and Family plan options with safe upgrade flows.',
    ['Membership overview', 'Plan comparison and benefits', 'Upgrade and renewal actions'],
  ),
  createPlaceholderRoute(
    'wishlist',
    'student-wishlist',
    'nav.wishlist',
    'student',
    'Wishlist',
    'Saved items management will expand here',
    'Students will later revisit saved stories, vocabulary, lessons, and premium content from one page.',
    ['Saved learning content', 'Saved AI practice items', 'Quick access back into learning'],
  ),
  createPlaceholderRoute(
    'notifications',
    'student-notifications',
    'nav.notifications',
    'student',
    'Notifications',
    'Student notifications are prepared for later phases',
    'This page will later organize quiz results, teacher feedback, progress updates, and reminders.',
    ['Reward updates', 'Teacher feedback alerts', 'Live class and learning reminders'],
  ),
  createPlaceholderRoute(
    'support',
    'student-support',
    'nav.support',
    'student',
    'Support',
    'Help center workflows will arrive in a later phase',
    'This page is ready for FAQs, support ticket entry, and safe help guidance for young learners.',
    ['Submit support requests', 'Read help guidance', 'Follow ticket replies from admins'],
  ),
]

const teacherChildren: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'teacher-dashboard',
    component: TeacherDashboardPage,
    meta: { titleKey: 'nav.teacherDashboard' },
  },
  createPlaceholderRoute(
    'content',
    'teacher-content',
    'nav.contentManagement',
    'teacher',
    'Content Management',
    'Content management is structured for a later phase',
    'This workspace is ready for modules, lessons, activities, and publishing states once content workflows begin.',
    ['Draft, submit, review, and publish content', 'See content status across modules and lessons', 'Prepare free and paid learning packages'],
  ),
  createPlaceholderRoute(
    'lessons',
    'teacher-lessons',
    'nav.lessonManagement',
    'teacher',
    'Lesson Management',
    'Lesson authoring tools will be enabled in a later phase',
    'This page will host lesson creation, editing, rich activity composition, and media uploads.',
    ['Create structured lessons', 'Attach audio, images, and video references', 'Organize lessons by modules and categories'],
  ),
  createPlaceholderRoute(
    'quizzes',
    'teacher-quizzes',
    'nav.quizManagement',
    'teacher',
    'Quiz Management',
    'Quiz creation tools are staged for a future phase',
    'The UI is ready for quiz authoring, review, and performance monitoring once the learning workflow opens.',
    ['Build multiple question types', 'Review attempt performance', 'Save AI-assisted quiz drafts later'],
  ),
  createPlaceholderRoute(
    'flashcards',
    'teacher-flashcards',
    'nav.flashcardManagement',
    'teacher',
    'Flashcard Management',
    'Flashcard authoring will be added later',
    'This page is prepared for vocabulary decks, image-backed cards, and lesson-linked practice materials.',
    ['Create vocabulary card sets', 'Attach images and hints', 'Group flashcards by lesson topics'],
  ),
  createPlaceholderRoute(
    'matching-games',
    'teacher-matching-games',
    'nav.matchingGameManagement',
    'teacher',
    'Matching Games',
    'Matching game tools are prepared for a later phase',
    'This section will later support kid-friendly matching activities for words, meanings, and images.',
    ['Word-to-meaning matching', 'Word-to-image matching', 'Lesson-linked practice sets'],
  ),
  createPlaceholderRoute(
    'ai-assistant',
    'teacher-ai-assistant',
    'nav.aiTeacherAssistant',
    'teacher',
    'AI Teacher Assistant',
    'AI teacher tools are intentionally deferred to a later phase',
    'This workspace is reserved for lesson outlines, content ideas, and teaching assistance after approval.',
    ['Generate lesson outline ideas', 'Draft vocabulary support sets', 'Receive teaching suggestions from AI'],
  ),
  createPlaceholderRoute(
    'ai-content-tools',
    'teacher-ai-content-tools',
    'nav.aiContentTools',
    'teacher',
    'AI Content Tools',
    'AI content generation tools will be added later',
    'This page is ready for AI quiz, story, and practice content generation with teacher review before saving.',
    ['Generate editable quiz drafts', 'Create story-based lesson ideas', 'Draft reusable practice content'],
  ),
  createPlaceholderRoute(
    'student-progress',
    'teacher-student-progress',
    'nav.studentProgress',
    'teacher',
    'Student Progress',
    'Progress monitoring tools will expand here',
    'Teachers will later review learner activity, weak areas, and coaching opportunities from this section.',
    ['View active learners', 'Review quiz and lesson progress', 'Track weak areas and engagement'],
  ),
  createPlaceholderRoute(
    'reviews',
    'teacher-reviews',
    'nav.reviews',
    'teacher',
    'Reviews',
    'Course and package reviews will appear here later',
    'This page is staged for viewing student feedback on teacher-created learning content.',
    ['Review student ratings', 'Track praise and issues', 'Spot trends across content'],
  ),
  createPlaceholderRoute(
    'earnings',
    'teacher-earnings',
    'nav.earnings',
    'teacher',
    'Earnings',
    'Teacher earnings reporting is prepared for the commerce phase',
    'This section will later show gross sales, net earnings, commission, and revenue trends.',
    ['Gross and net earnings overview', 'Commission breakdowns', 'Content sales snapshots'],
  ),
  createPlaceholderRoute(
    'payouts',
    'teacher-payouts',
    'nav.payoutRequests',
    'teacher',
    'Payout Requests',
    'Payout workflows will arrive later',
    'This page is ready for payout history, pending balances, and request submissions once earnings are enabled.',
    ['Available balance tracking', 'Payout request history', 'Admin decision updates'],
  ),
  createPlaceholderRoute(
    'notifications',
    'teacher-notifications',
    'nav.notifications',
    'teacher',
    'Notifications',
    'Teacher notifications are prepared for later phases',
    'This page will later gather approval updates, student reviews, revision requests, and payout alerts.',
    ['Approval and rejection alerts', 'Student activity updates', 'Platform notices'],
  ),
  createPlaceholderRoute(
    'support',
    'teacher-support',
    'nav.support',
    'teacher',
    'Support',
    'Support workflows will be added in a later phase',
    'This workspace will later help teachers reach admins, manage tickets, and find help resources.',
    ['Open support requests', 'Read responses from admins', 'Review platform guidance'],
  ),
]

const adminChildren: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'admin-dashboard',
    component: AdminDashboardPage,
    meta: { titleKey: 'nav.adminDashboard' },
  },
  createPlaceholderRoute(
    'users',
    'admin-users',
    'nav.userManagement',
    'admin',
    'User Management',
    'User management tools will be added in a later phase',
    'This page is prepared for student and teacher account oversight, filtering, and moderation actions.',
    ['Browse users by role', 'Inspect account health', 'Manage account status safely'],
  ),
  createPlaceholderRoute(
    'teacher-approval',
    'admin-teacher-approval',
    'nav.teacherApproval',
    'admin',
    'Teacher Approval',
    'Teacher verification workflows are reserved for a later phase',
    'This section will later support review of teacher documents, approval decisions, and rejection reasons.',
    ['Review pending teacher accounts', 'Approve or reject verification', 'Send next-step guidance'],
  ),
  createPlaceholderRoute(
    'content-approval',
    'admin-content-approval',
    'nav.contentApproval',
    'admin',
    'Content Approval',
    'Content moderation and approval will arrive later',
    'This workspace is staged for review of teacher-submitted learning materials and moderation requests.',
    ['Approve or reject submitted content', 'Request revisions', 'Archive or hide unsuitable material'],
  ),
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
