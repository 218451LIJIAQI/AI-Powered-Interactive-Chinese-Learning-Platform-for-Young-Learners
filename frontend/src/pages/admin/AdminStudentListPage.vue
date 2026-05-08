<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { adminUserService } from '../../services/adminUserService'
import type { AdminStudentListItem, PageResponse } from '../../types/adminManagement'

const { t } = useI18n()
const loading = ref(true)
const students = ref<PageResponse<AdminStudentListItem> | null>(null)

async function loadStudents() {
  loading.value = true
  try {
    students.value = await adminUserService.getStudents({ page: 0, size: 20 })
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadStudents()
})
</script>

<template>
  <div class="dashboard-stack">
    <el-card class="dashboard-hero dashboard-hero--admin" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('teacherVerification.studentManagementEyebrow') }}</p>
          <h1>{{ t('teacherVerification.studentManagementTitle') }}</h1>
          <p>{{ t('teacherVerification.studentManagementDescription') }}</p>
        </div>
      </div>
    </el-card>

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="students?.items || []" class="phase-table">
        <el-table-column prop="fullName" :label="t('auth.fields.fullName')" min-width="180" />
        <el-table-column prop="email" :label="t('auth.fields.email')" min-width="220" />
        <el-table-column prop="preferredLanguage" :label="t('auth.fields.preferredLanguage')" min-width="160" />
        <el-table-column prop="createdAt" :label="t('teacherVerification.createdAt')" min-width="180" />
      </el-table>
    </el-card>
  </div>
</template>
