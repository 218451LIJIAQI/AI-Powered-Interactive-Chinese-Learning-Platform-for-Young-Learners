<script setup lang="ts">
import { computed } from 'vue'

import AppShell from '../components/common/AppShell.vue'
import { useAuthStore } from '../stores/authStore'

const authStore = useAuthStore()

const isLocked = computed(() => authStore.teacherStatus !== 'APPROVED')

const navItems = computed(() => [
  { key: 'teacher-dashboard', labelKey: 'nav.teacherDashboard', routeName: 'teacher-dashboard', icon: 'House' },
  { key: 'teacher-content', labelKey: 'nav.contentManagement', routeName: 'teacher-content', icon: 'Collection' },
  { key: 'teacher-lessons', labelKey: 'nav.lessonManagement', routeName: 'teacher-lessons', icon: 'Notebook' },
  { key: 'teacher-quizzes', labelKey: 'nav.quizManagement', routeName: 'teacher-quizzes', icon: 'DocumentChecked' },
  {
    key: 'teacher-flashcards',
    labelKey: 'nav.flashcardManagement',
    routeName: 'teacher-flashcards',
    icon: 'Files',
  },
  {
    key: 'teacher-matching-games',
    labelKey: 'nav.matchingGameManagement',
    routeName: 'teacher-matching-games',
    icon: 'Connection',
  },
  {
    key: 'teacher-ai-assistant',
    labelKey: 'nav.aiTeacherAssistant',
    routeName: 'teacher-ai-assistant',
    icon: 'MagicStick',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-ai-content-tools',
    labelKey: 'nav.aiContentTools',
    routeName: 'teacher-ai-content-tools',
    icon: 'Opportunity',
    disabled: isLocked.value,
    chip: isLocked.value ? 'Locked' : undefined,
  },
  {
    key: 'teacher-student-progress',
    labelKey: 'nav.studentProgress',
    routeName: 'teacher-student-progress',
    icon: 'Histogram',
  },
  { key: 'teacher-reviews', labelKey: 'nav.reviews', routeName: 'teacher-reviews', icon: 'ChatLineSquare' },
  { key: 'teacher-earnings', labelKey: 'nav.earnings', routeName: 'teacher-earnings', icon: 'Coin' },
  { key: 'teacher-payouts', labelKey: 'nav.payoutRequests', routeName: 'teacher-payouts', icon: 'Wallet' },
  {
    key: 'teacher-notifications',
    labelKey: 'nav.notifications',
    routeName: 'teacher-notifications',
    icon: 'Bell',
  },
  { key: 'teacher-support', labelKey: 'nav.support', routeName: 'teacher-support', icon: 'Service' },
])

const subtitle = computed(() => {
  const status = authStore.teacherStatus ?? 'PENDING'
  return `Manage your teaching workspace, review engagement signals, and track approval status. Current status: ${status}.`
})
</script>

<template>
  <AppShell variant="teacher" title="Teacher Workspace" :subtitle="subtitle" :nav-items="navItems" />
</template>
