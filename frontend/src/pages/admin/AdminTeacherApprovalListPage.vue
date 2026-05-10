<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { useEnumLabel } from '../../composables/useEnumLabel'
import { adminTeacherService } from '../../services/adminTeacherService'
import type { AdminTeacherListItem } from '../../types/adminManagement'
import { formatDateTime } from '../../utils/datetime'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const items = ref<AdminTeacherListItem[]>([])

async function loadPendingTeachers() {
  loading.value = true
  try {
    items.value = await adminTeacherService.getPendingTeachers()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadPendingTeachers()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-card class="dashboard-hero dashboard-hero--admin" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('teacherVerification.adminApprovalEyebrow') }}</p>
          <h1>{{ t('teacherVerification.adminApprovalTitle') }}</h1>
          <p>{{ t('teacherVerification.adminApprovalDescription') }}</p>
        </div>
      </div>
    </el-card>

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('teacherVerification.pendingTeachers') }}</p>
          <h2>{{ t('teacherVerification.pendingTeachersTitle') }}</h2>
        </div>
      </div>

      <el-table v-loading="loading" :data="items" class="phase-table">
        <el-table-column prop="fullName" :label="t('auth.fields.fullName')" min-width="180" />
        <el-table-column prop="email" :label="t('auth.fields.email')" min-width="220" />
        <el-table-column :label="t('dashboard.teacherStatus')" width="140">
          <template #default="{ row }">
            <el-tag type="warning" round>{{ translateEnum('teacherStatus', row.teacherStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="verificationDocumentCount" :label="t('teacherVerification.documentCount')" width="140" />
        <el-table-column :label="t('teacherVerification.latestSubmission')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.latestSubmissionTime) }}</template>
        </el-table-column>
        <el-table-column width="140">
          <template #default="{ row }">
            <router-link :to="{ name: 'admin-teacher-approval-detail', params: { id: row.teacherId } }">
              <el-button type="primary" plain>{{ t('teacherVerification.reviewTeacher') }}</el-button>
            </router-link>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !items.length" :description="t('teacherVerification.noPendingTeachers')" />
    </el-card>
  </div>
</template>
