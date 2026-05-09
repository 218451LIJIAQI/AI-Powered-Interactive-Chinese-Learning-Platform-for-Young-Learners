<script setup lang="ts">
import { computed } from 'vue'

import AppShell from '../components/common/AppShell.vue'
import { useNotificationUnreadCount } from '../composables/useNotificationUnreadCount'
import { useAuthStore } from '../stores/authStore'

const authStore = useAuthStore()
const { unreadCount } = useNotificationUnreadCount(true)

const isLocked = computed(() => authStore.teacherStatus !== 'APPROVED')

const navItems = computed(() => [
  { key: 'teacher-dashboard', labelKey: 'nav.teacherDashboard', routeName: 'teacher-dashboard', icon: 'House' },
  { key: 'teacher-profile', labelKey: 'nav.teacherProfile', routeName: 'teacher-profile', icon: 'User' },
  {
    key: 'teacher-verification-status',
    labelKey: 'nav.teacherVerificationStatus',
    routeName: 'teacher-verification-status',
    icon: 'CircleCheck',
  },
  {
    key: 'teacher-verification-documents',
    labelKey: 'nav.uploadVerificationDocuments',
    routeName: 'teacher-verification-documents',
    icon: 'Upload',
  },
  { key: 'teacher-earnings', labelKey: 'nav.earnings', routeName: 'teacher-earnings', icon: 'Coin' },
  { key: 'teacher-payouts', labelKey: 'nav.payoutRequests', routeName: 'teacher-payouts', icon: 'Wallet' },
  {
    key: 'teacher-live-classes',
    labelKey: 'nav.liveClasses',
    routeName: 'teacher-live-classes',
    icon: 'VideoCamera',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-schedule',
    labelKey: 'nav.schedule',
    routeName: 'teacher-schedule',
    icon: 'Calendar',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-recorded-lessons',
    labelKey: 'nav.recordedLessons',
    routeName: 'teacher-recorded-lessons',
    icon: 'Film',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  { key: 'notifications', labelKey: 'nav.notifications', routeName: 'notifications', icon: 'Bell', badgeCount: unreadCount.value },
  { key: 'support-tickets', labelKey: 'nav.supportTickets', routeName: 'support-tickets', icon: 'Service' },
  {
    key: 'teacher-reviews',
    labelKey: 'nav.reviews',
    routeName: 'teacher-reviews',
    icon: 'ChatDotRound',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-feedback',
    labelKey: 'phase11.teacherFeedbackNav',
    routeName: 'teacher-feedback',
    icon: 'Memo',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-reports',
    labelKey: 'nav.reports',
    routeName: 'teacher-reports',
    icon: 'Warning',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-moderation-records',
    labelKey: 'phase11.moderationNav',
    routeName: 'teacher-moderation-records',
    icon: 'Finished',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-modules',
    labelKey: 'nav.moduleManagement',
    routeName: 'teacher-modules',
    icon: 'Collection',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-lessons',
    labelKey: 'nav.lessonManagement',
    routeName: 'teacher-lessons',
    icon: 'Notebook',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-packages',
    labelKey: 'nav.packageManagement',
    routeName: 'teacher-packages',
    icon: 'Tickets',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-quizzes',
    labelKey: 'nav.quizManagement',
    routeName: 'teacher-quizzes',
    icon: 'DocumentChecked',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-flashcards',
    labelKey: 'nav.flashcardManagement',
    routeName: 'teacher-flashcards',
    icon: 'Files',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-matching-games',
    labelKey: 'nav.matchingGameManagement',
    routeName: 'teacher-matching-games',
    icon: 'Connection',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-listening-exercises',
    labelKey: 'nav.listeningExerciseManagement',
    routeName: 'teacher-listening-exercises',
    icon: 'Headset',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-tracing-exercises',
    labelKey: 'nav.tracingExerciseManagement',
    routeName: 'teacher-tracing-exercises',
    icon: 'EditPen',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  { key: 'teacher-quiz-results', labelKey: 'nav.quizResults', routeName: 'teacher-quiz-results', icon: 'Histogram' },
  {
    key: 'teacher-ai-assistant',
    labelKey: 'nav.aiTeacherAssistant',
    routeName: 'teacher-ai-assistant',
    icon: 'ChatLineRound',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-ai-vocabulary',
    labelKey: 'nav.aiVocabulary',
    routeName: 'teacher-ai-vocabulary',
    icon: 'CollectionTag',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-ai-quiz-generator',
    labelKey: 'nav.aiQuizGenerator',
    routeName: 'teacher-ai-quiz-generator',
    icon: 'MagicStick',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-ai-story-generator',
    labelKey: 'nav.aiStoryGenerator',
    routeName: 'teacher-ai-story-generator',
    icon: 'Notebook',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
])

const subtitle = computed(() => {
  const status = authStore.teacherStatus ?? 'PENDING'
  return `Manage your teaching workspace, schedule live lessons, prepare recorded materials, and track approval status. Current status: ${status}.`
})
</script>

<template>
  <AppShell variant="teacher" title="Teacher Workspace" :subtitle="subtitle" :nav-items="navItems" />
</template>
