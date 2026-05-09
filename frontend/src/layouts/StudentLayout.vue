<script setup lang="ts">
import { computed } from 'vue'

import AppShell from '../components/common/AppShell.vue'
import { useAuthStore } from '../stores/authStore'

const authStore = useAuthStore()

const navItems = computed(() => [
  { key: 'student-dashboard', labelKey: 'nav.studentDashboard', routeName: 'student-dashboard', icon: 'House' },
  { key: 'student-lessons', labelKey: 'nav.studentLessons', routeName: 'student-lessons', icon: 'Reading' },
  { key: 'student-progress', labelKey: 'nav.progress', routeName: 'student-progress', icon: 'DataAnalysis' },
  { key: 'student-rewards', labelKey: 'nav.rewards', routeName: 'student-rewards', icon: 'TrophyBase' },
  { key: 'student-wrong-answers', labelKey: 'nav.wrongAnswers', routeName: 'student-wrong-answers', icon: 'DocumentDelete' },
])

const subtitle = computed(() => {
  const name = authStore.currentUser?.fullName ?? 'young learner'
  return `Welcome back, ${name}. Explore your lessons, track your progress, and celebrate your rewards from one playful dashboard.`
})
</script>

<template>
  <AppShell variant="student" title="Student Learning Space" :subtitle="subtitle" :nav-items="navItems" />
</template>
