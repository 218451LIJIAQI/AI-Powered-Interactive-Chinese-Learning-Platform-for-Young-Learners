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
    <el-card class="dashboard-hero dashboard-hero--admin" shadow="hover">
      <p class="section-label">{{ t('nav.adminDashboard') }}</p>
      <h1>{{ t('dashboard.adminTitle', { name: user?.fullName ?? 'Administrator' }) }}</h1>
      <p>{{ t('dashboard.adminDescription') }}</p>
    </el-card>

    <div class="dashboard-grid">
      <el-card class="info-card" shadow="hover">
        <h2>{{ t('dashboard.accountOverview') }}</h2>
        <p><strong>{{ t('auth.fields.email') }}:</strong> {{ user?.email }}</p>
        <p><strong>{{ t('dashboard.roles') }}:</strong> {{ user?.roles.join(', ') }}</p>
      </el-card>

      <el-card class="info-card" shadow="hover">
        <h2>{{ t('dashboard.adminAccess') }}</h2>
        <p>{{ t('dashboard.adminPlaceholder') }}</p>
      </el-card>
    </div>
  </div>
</template>
