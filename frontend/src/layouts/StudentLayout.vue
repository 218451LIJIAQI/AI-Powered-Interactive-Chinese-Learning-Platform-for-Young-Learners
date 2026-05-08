<script setup lang="ts">
import { computed } from 'vue'

import AppShell from '../components/common/AppShell.vue'
import { useAuthStore } from '../stores/authStore'

const authStore = useAuthStore()

const navItems = computed(() => [
  { key: 'student-dashboard', labelKey: 'nav.studentDashboard', routeName: 'student-dashboard', icon: 'House' },
  { key: 'student-modules', labelKey: 'nav.learningModules', routeName: 'student-modules', icon: 'Reading' },
  { key: 'student-ai-tutor', labelKey: 'nav.aiTutor', routeName: 'student-ai-tutor', icon: 'ChatDotRound' },
  {
    key: 'student-ai-vocabulary',
    labelKey: 'nav.aiVocabulary',
    routeName: 'student-ai-vocabulary',
    icon: 'MagicStick',
  },
  {
    key: 'student-ai-sentence-practice',
    labelKey: 'nav.aiSentencePractice',
    routeName: 'student-ai-sentence-practice',
    icon: 'EditPen',
  },
  {
    key: 'student-pronunciation-feedback',
    labelKey: 'nav.pronunciationFeedback',
    routeName: 'student-pronunciation-feedback',
    icon: 'Microphone',
  },
  {
    key: 'student-writing-feedback',
    labelKey: 'nav.writingFeedback',
    routeName: 'student-writing-feedback',
    icon: 'Edit',
  },
  { key: 'student-progress', labelKey: 'nav.progress', routeName: 'student-progress', icon: 'DataAnalysis' },
  { key: 'student-rewards', labelKey: 'nav.rewards', routeName: 'student-rewards', icon: 'TrophyBase' },
  { key: 'student-cart', labelKey: 'nav.cart', routeName: 'student-cart', icon: 'ShoppingCart' },
  {
    key: 'student-payment-history',
    labelKey: 'nav.paymentHistory',
    routeName: 'student-payment-history',
    icon: 'Tickets',
  },
  { key: 'student-subscription', labelKey: 'nav.subscription', routeName: 'student-subscription', icon: 'Star' },
  { key: 'student-wishlist', labelKey: 'nav.wishlist', routeName: 'student-wishlist', icon: 'StarFilled' },
  {
    key: 'student-notifications',
    labelKey: 'nav.notifications',
    routeName: 'student-notifications',
    icon: 'Bell',
  },
  { key: 'student-support', labelKey: 'nav.support', routeName: 'student-support', icon: 'Service' },
])

const subtitle = computed(() => {
  const name = authStore.currentUser?.fullName ?? 'young learner'
  return `Welcome back, ${name}. Explore your lessons, progress, and future AI learning tools from one playful dashboard.`
})
</script>

<template>
  <AppShell variant="student" title="Student Learning Space" :subtitle="subtitle" :nav-items="navItems" />
</template>
