<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { useAuthStore } from '../../stores/authStore'

const authStore = useAuthStore()
const { t } = useI18n()
const user = computed(() => authStore.currentUser)
</script>

<template>
  <div class="dashboard-stack">
    <el-card class="dashboard-hero dashboard-hero--student" shadow="hover">
      <p class="section-label">{{ t('nav.studentDashboard') }}</p>
      <h1>{{ t('dashboard.studentTitle', { name: user?.fullName ?? 'Learner' }) }}</h1>
      <p>{{ t('dashboard.studentDescription') }}</p>
    </el-card>

    <div class="dashboard-grid">
      <el-card class="info-card" shadow="hover">
        <h2>{{ t('dashboard.accountOverview') }}</h2>
        <p><strong>{{ t('auth.fields.email') }}:</strong> {{ user?.email }}</p>
        <p><strong>{{ t('dashboard.roles') }}:</strong> {{ user?.roles.join(', ') }}</p>
        <p><strong>{{ t('auth.fields.preferredLanguage') }}:</strong> {{ user?.preferredLanguage ?? 'en' }}</p>
      </el-card>

      <el-card class="info-card" shadow="hover">
        <h2>{{ t('dashboard.studentProgress') }}</h2>
        <p><strong>{{ t('dashboard.currentLevel') }}:</strong> {{ user?.studentProfile?.currentLevel ?? 'Beginner' }}</p>
        <p><strong>{{ t('dashboard.totalPoints') }}:</strong> {{ user?.studentProfile?.totalPoints ?? 0 }}</p>
        <p>{{ t('dashboard.studentPlaceholder') }}</p>
      </el-card>
    </div>
  </div>
</template>
