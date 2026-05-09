<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { reportService } from '../../services/reportService'
import type { ReportItem } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const { t } = useI18n()
const loading = ref(true)
const reports = ref<ReportItem[]>([])

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('en-MY')
}

async function loadReports() {
  loading.value = true
  try {
    reports.value = await reportService.getMyReports()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadReports()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase11.reportsEyebrow')"
      :title="t('phase11.myReportsTitle')"
      :description="t('phase11.myReportsDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('phase11.myReports') }}</p>
          <h2>{{ t('phase11.reportHistory') }}</h2>
        </div>
        <router-link :to="{ path: '/student/reports/create' }">
          <el-button type="primary">{{ t('phase11.submitReport') }}</el-button>
        </router-link>
      </div>

      <el-table v-loading="loading" :data="reports" class="phase-table">
        <el-table-column prop="targetType" :label="t('phase11.reportTargetType')" min-width="150" />
        <el-table-column prop="targetTitle" :label="t('phase11.reportTargetTitle')" min-width="220" />
        <el-table-column prop="reportStatus" :label="t('commerce.status')" min-width="160" />
        <el-table-column prop="reason" :label="t('phase11.reportReason')" min-width="320" />
        <el-table-column :label="t('commerce.createdAt')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !reports.length" :description="t('phase11.emptyReports')" />
    </el-card>
  </div>
</template>
