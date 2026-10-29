<template>
  <TeacherTeachingDashboard v-if="auth.isApprovedTeacher" />
  <TeacherApplicantDashboard v-else />
</template>

<script setup lang="ts">
import TeacherApplicantDashboard from '@/components/teacher/TeacherApplicantDashboard.vue'
import TeacherTeachingDashboard from '@/components/teacher/TeacherTeachingDashboard.vue'
import { useAuthStore } from '@/stores/auth'

/** An approved teacher sees the teaching dashboard; an applicant sees where the application stands. */
const auth = useAuthStore()
</script>
