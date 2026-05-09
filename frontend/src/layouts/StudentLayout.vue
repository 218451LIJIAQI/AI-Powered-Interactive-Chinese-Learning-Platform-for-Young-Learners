<script setup lang="ts">
import { computed } from 'vue'

import AppShell from '../components/common/AppShell.vue'
import { useAuthStore } from '../stores/authStore'

const authStore = useAuthStore()

const navItems = computed(() => [
  { key: 'student-dashboard', labelKey: 'nav.studentDashboard', routeName: 'student-dashboard', icon: 'House' },
  { key: 'student-lessons', labelKey: 'nav.studentLessons', routeName: 'student-lessons', icon: 'Reading' },
  { key: 'student-cart', labelKey: 'nav.cart', routeName: 'student-cart', icon: 'ShoppingCart' },
  { key: 'student-payments', labelKey: 'nav.paymentHistory', routeName: 'student-payments', icon: 'CreditCard' },
  { key: 'student-subscription', labelKey: 'nav.subscription', routeName: 'student-subscription', icon: 'Tickets' },
  { key: 'student-refunds', labelKey: 'nav.refundManagement', routeName: 'student-refunds', icon: 'RefreshLeft' },
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
  { key: 'student-progress', labelKey: 'nav.progress', routeName: 'student-progress', icon: 'DataAnalysis' },
  { key: 'student-rewards', labelKey: 'nav.rewards', routeName: 'student-rewards', icon: 'TrophyBase' },
  { key: 'student-wrong-answers', labelKey: 'nav.wrongAnswers', routeName: 'student-wrong-answers', icon: 'DocumentDelete' },
])

const subtitle = computed(() => {
  const name = authStore.currentUser?.fullName ?? 'young learner'
  return `Welcome back, ${name}. Explore lessons, practise with AI, track progress, and celebrate rewards from one playful dashboard.`
})
</script>

<template>
  <AppShell variant="student" title="Student Learning Space" :subtitle="subtitle" :nav-items="navItems" />
</template>
