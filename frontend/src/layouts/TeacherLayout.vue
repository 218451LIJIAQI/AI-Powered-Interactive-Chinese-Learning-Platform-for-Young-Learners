<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import AppShell from '../components/common/AppShell.vue'
import { useNotificationUnreadCount } from '../composables/useNotificationUnreadCount'
import { useAuthStore } from '../stores/authStore'

const authStore = useAuthStore()
const { unreadCount } = useNotificationUnreadCount(true)
const { t } = useI18n()

const isLocked = computed(() => authStore.teacherStatus !== 'APPROVED')
const lockedChip = computed(() => (isLocked.value ? t('shell.lockedChip') : undefined))

const navGroups = computed(() => [
  {
    key: 'home',
    labelKey: 'kid.navGroupHome',
    emoji: '\ud83c\udfe0',
    items: [
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
    ],
  },
  {
    key: 'teach',
    labelKey: 'kid.navGroupTeach',
    emoji: '\ud83c\udf93',
    items: [
      {
        key: 'teacher-live-classes',
        labelKey: 'nav.liveClasses',
        routeName: 'teacher-live-classes',
        icon: 'VideoCamera',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-schedule',
        labelKey: 'nav.schedule',
        routeName: 'teacher-schedule',
        icon: 'Calendar',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-recorded-lessons',
        labelKey: 'nav.recordedLessons',
        routeName: 'teacher-recorded-lessons',
        icon: 'Film',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-quiz-results',
        labelKey: 'nav.quizResults',
        routeName: 'teacher-quiz-results',
        icon: 'Histogram',
      },
    ],
  },
  {
    key: 'studio',
    labelKey: 'kid.navGroupContent',
    emoji: '\ud83d\udcdd',
    items: [
      {
        key: 'teacher-modules',
        labelKey: 'nav.moduleManagement',
        routeName: 'teacher-modules',
        icon: 'Collection',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-lessons',
        labelKey: 'nav.lessonManagement',
        routeName: 'teacher-lessons',
        icon: 'Notebook',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-packages',
        labelKey: 'nav.packageManagement',
        routeName: 'teacher-packages',
        icon: 'Tickets',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-quizzes',
        labelKey: 'nav.quizManagement',
        routeName: 'teacher-quizzes',
        icon: 'DocumentChecked',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-flashcards',
        labelKey: 'nav.flashcardManagement',
        routeName: 'teacher-flashcards',
        icon: 'Files',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-matching-games',
        labelKey: 'nav.matchingGameManagement',
        routeName: 'teacher-matching-games',
        icon: 'Connection',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-listening-exercises',
        labelKey: 'nav.listeningExerciseManagement',
        routeName: 'teacher-listening-exercises',
        icon: 'Headset',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-tracing-exercises',
        labelKey: 'nav.tracingExerciseManagement',
        routeName: 'teacher-tracing-exercises',
        icon: 'EditPen',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
    ],
  },
  {
    key: 'ai-tools',
    labelKey: 'kid.navGroupAiTools',
    emoji: '\ud83e\udd16',
    items: [
      {
        key: 'teacher-ai-assistant',
        labelKey: 'nav.aiTeacherAssistant',
        routeName: 'teacher-ai-assistant',
        icon: 'ChatLineRound',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-ai-vocabulary',
        labelKey: 'nav.aiVocabulary',
        routeName: 'teacher-ai-vocabulary',
        icon: 'CollectionTag',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-ai-quiz-generator',
        labelKey: 'nav.aiQuizGenerator',
        routeName: 'teacher-ai-quiz-generator',
        icon: 'MagicStick',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-ai-story-generator',
        labelKey: 'nav.aiStoryGenerator',
        routeName: 'teacher-ai-story-generator',
        icon: 'Notebook',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
    ],
  },
  {
    key: 'inbox',
    labelKey: 'kid.navGroupInbox',
    emoji: '\ud83d\udc8c',
    items: [
      {
        key: 'teacher-notifications',
        labelKey: 'nav.notifications',
        routeName: 'teacher-notifications',
        icon: 'Bell',
        badgeCount: unreadCount.value,
      },
      {
        key: 'teacher-support-tickets',
        labelKey: 'nav.supportTickets',
        routeName: 'teacher-support-tickets',
        icon: 'Service',
      },
      {
        key: 'teacher-reviews',
        labelKey: 'nav.reviews',
        routeName: 'teacher-reviews',
        icon: 'ChatDotRound',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-feedback',
        labelKey: 'phase11.teacherFeedbackNav',
        routeName: 'teacher-feedback',
        icon: 'Memo',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-reports',
        labelKey: 'nav.reports',
        routeName: 'teacher-reports',
        icon: 'Warning',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
      {
        key: 'teacher-moderation-records',
        labelKey: 'phase11.moderationNav',
        routeName: 'teacher-moderation-records',
        icon: 'Finished',
        disabled: isLocked.value,
        chip: lockedChip.value,
      },
    ],
  },
  {
    key: 'earnings',
    labelKey: 'kid.navGroupEarnings',
    emoji: '\ud83d\udcb0',
    items: [
      { key: 'teacher-earnings', labelKey: 'nav.earnings', routeName: 'teacher-earnings', icon: 'Coin' },
      { key: 'teacher-payouts', labelKey: 'nav.payoutRequests', routeName: 'teacher-payouts', icon: 'Wallet' },
    ],
  },
])

</script>

<template>
  <!-- The shell omits its tagline so the topbar reads as `Brand · actions`,
       Linear/Vercel style. Each page owns its own PageHero. -->
  <AppShell variant="teacher" :nav-groups="navGroups" />
</template>
