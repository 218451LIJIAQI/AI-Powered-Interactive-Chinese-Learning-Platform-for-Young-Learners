<script setup lang="ts">
import { computed } from 'vue'

import AppShell from '../components/common/AppShell.vue'
import { useAuthStore } from '../stores/authStore'

const authStore = useAuthStore()

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
])

const subtitle = computed(() => {
  const status = authStore.teacherStatus ?? 'PENDING'
  return `Manage your teaching workspace, review engagement signals, and track approval status. Current status: ${status}.`
})
</script>

<template>
  <AppShell variant="teacher" title="Teacher Workspace" :subtitle="subtitle" :nav-items="navItems" />
</template>
