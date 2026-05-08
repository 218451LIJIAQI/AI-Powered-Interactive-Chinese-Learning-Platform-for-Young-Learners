<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { useAuthStore } from '../../stores/authStore'

const authStore = useAuthStore()
const { t } = useI18n()

const user = computed(() => authStore.currentUser)
const teacherStatus = computed(() => authStore.teacherStatus)

const statusType = computed(() => {
  if (teacherStatus.value === 'APPROVED') {
    return 'success'
  }

  if (teacherStatus.value === 'REJECTED') {
    return 'error'
  }

  return 'warning'
})

const statusMessage = computed(() => {
  if (teacherStatus.value === 'APPROVED') {
    return t('dashboard.teacherApproved')
  }

  if (teacherStatus.value === 'REJECTED') {
    return t('dashboard.teacherRejected')
  }

  return t('dashboard.teacherPending')
})
</script>

<template>
  <div class="dashboard-stack">
    <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
      <p class="section-label">{{ t('nav.teacherDashboard') }}</p>
      <h1>{{ t('dashboard.teacherTitle', { name: user?.fullName ?? 'Teacher' }) }}</h1>
      <p>{{ t('dashboard.teacherDescription') }}</p>
    </el-card>

    <el-alert :title="statusMessage" :type="statusType" show-icon :closable="false" />

    <div class="dashboard-grid">
      <el-card class="info-card" shadow="hover">
        <h2>{{ t('dashboard.accountOverview') }}</h2>
        <p><strong>{{ t('auth.fields.email') }}:</strong> {{ user?.email }}</p>
        <p><strong>{{ t('dashboard.roles') }}:</strong> {{ user?.roles.join(', ') }}</p>
        <p><strong>{{ t('dashboard.teacherStatus') }}:</strong> {{ teacherStatus ?? 'PENDING' }}</p>
      </el-card>

      <el-card class="info-card" shadow="hover">
        <h2>{{ t('dashboard.teacherProfile') }}</h2>
        <p><strong>{{ t('auth.fields.bio') }}:</strong> {{ user?.teacherProfile?.bio || t('dashboard.noBio') }}</p>
        <p v-if="teacherStatus === 'REJECTED' && user?.teacherProfile?.rejectionReason">
          <strong>{{ t('dashboard.rejectionReason') }}:</strong> {{ user.teacherProfile.rejectionReason }}
        </p>
        <p>{{ t('dashboard.teacherPlaceholder') }}</p>
      </el-card>
    </div>
  </div>
</template>
