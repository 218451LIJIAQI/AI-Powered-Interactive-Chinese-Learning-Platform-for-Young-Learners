<script setup lang="ts">
import { computed } from 'vue'

import AppShell from '../components/common/AppShell.vue'
import { useNotificationUnreadCount } from '../composables/useNotificationUnreadCount'

const { unreadCount } = useNotificationUnreadCount(true)

const navGroups = computed(() => [
  {
    key: 'home',
    labelKey: 'kid.navGroupHome',
    emoji: '\ud83c\udfe0',
    items: [
      { key: 'student-dashboard', labelKey: 'nav.studentDashboard', routeName: 'student-dashboard', icon: 'House' },
    ],
  },
  {
    key: 'learn',
    labelKey: 'kid.navGroupLearn',
    emoji: '\ud83d\udcda',
    items: [
      { key: 'student-lessons', labelKey: 'nav.studentLessons', routeName: 'student-lessons', icon: 'Reading' },
      { key: 'student-live-classes', labelKey: 'nav.liveClasses', routeName: 'student-live-classes', icon: 'VideoPlay' },
      { key: 'student-schedule', labelKey: 'nav.schedule', routeName: 'student-schedule', icon: 'Calendar' },
      { key: 'student-progress', labelKey: 'nav.progress', routeName: 'student-progress', icon: 'DataAnalysis' },
      { key: 'student-rewards', labelKey: 'nav.rewards', routeName: 'student-rewards', icon: 'TrophyBase' },
      { key: 'student-wrong-answers', labelKey: 'nav.wrongAnswers', routeName: 'student-wrong-answers', icon: 'DocumentDelete' },
    ],
  },
  {
    key: 'ai',
    labelKey: 'kid.navGroupAi',
    emoji: '\ud83e\udd16',
    items: [
      { key: 'student-ai-tutor', labelKey: 'nav.aiTutor', routeName: 'student-ai-tutor', icon: 'ChatDotRound' },
      { key: 'student-ai-vocabulary', labelKey: 'nav.aiVocabulary', routeName: 'student-ai-vocabulary', icon: 'CollectionTag' },
      {
        key: 'student-ai-sentence-practice',
        labelKey: 'nav.aiSentencePractice',
        routeName: 'student-ai-sentence-practice',
        icon: 'Edit',
      },
      { key: 'student-ai-quiz-generator', labelKey: 'nav.aiQuizGenerator', routeName: 'student-ai-quiz-generator', icon: 'MagicStick' },
      { key: 'student-ai-story-generator', labelKey: 'nav.aiStoryGenerator', routeName: 'student-ai-story-generator', icon: 'Notebook' },
      {
        key: 'student-ai-pronunciation-feedback',
        labelKey: 'nav.pronunciationFeedback',
        routeName: 'student-ai-pronunciation-feedback',
        icon: 'Microphone',
      },
      {
        key: 'student-ai-writing-feedback',
        labelKey: 'nav.writingFeedback',
        routeName: 'student-ai-writing-feedback',
        icon: 'Brush',
      },
    ],
  },
  {
    key: 'inbox',
    labelKey: 'kid.navGroupInbox',
    emoji: '\ud83d\udc8c',
    items: [
      {
        key: 'student-notifications',
        labelKey: 'nav.notifications',
        routeName: 'student-notifications',
        icon: 'Bell',
        badgeCount: unreadCount.value,
      },
      {
        key: 'student-support-tickets',
        labelKey: 'nav.supportTickets',
        routeName: 'student-support-tickets',
        icon: 'Service',
      },
      { key: 'student-reports', labelKey: 'nav.reports', routeName: 'student-reports', icon: 'Warning' },
      { key: 'student-reviews', labelKey: 'nav.reviews', routeName: 'student-reviews', icon: 'ChatDotRound' },
      { key: 'student-feedback', labelKey: 'phase11.teacherFeedbackNav', routeName: 'student-feedback', icon: 'Memo' },
    ],
  },
  {
    key: 'wallet',
    labelKey: 'kid.navGroupShop',
    emoji: '\ud83d\udcb0',
    items: [
      { key: 'student-cart', labelKey: 'nav.cart', routeName: 'student-cart', icon: 'ShoppingCart' },
      { key: 'student-payments', labelKey: 'nav.paymentHistory', routeName: 'student-payments', icon: 'CreditCard' },
      { key: 'student-subscription', labelKey: 'nav.subscription', routeName: 'student-subscription', icon: 'Tickets' },
      { key: 'student-refunds', labelKey: 'nav.refundManagement', routeName: 'student-refunds', icon: 'RefreshLeft' },
    ],
  },
])

</script>

<template>
  <!-- Mascot bubble (rendered inside AppShell) already greets the student
       in the topbar; the tagline would just duplicate that greeting. -->
  <AppShell variant="student" :nav-groups="navGroups" />
</template>
