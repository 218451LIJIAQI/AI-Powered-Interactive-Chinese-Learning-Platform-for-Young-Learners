<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { reportService } from '../../services/reportService'
import type { ReportItem } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const reports = ref<ReportItem[]>([])

async function loadReports() {
  loading.value = true
  try {
    reports.value = await reportService.getTeacherReports()
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
      :title="t('phase11.teacherReportsTitle')"
      :description="t('phase11.teacherReportsDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="reports" class="phase-table">
        <el-table-column :label="t('phase11.reportTargetType')" min-width="140">
          <template #default="{ row }">{{ translateEnum('reportTargetType', row.targetType) }}</template>
        </el-table-column>
        <el-table-column prop="targetTitle" :label="t('phase11.reportTargetTitle')" min-width="220" />
        <el-table-column :label="t('commerce.status')" min-width="140">
          <template #default="{ row }">
            <el-tag round size="small">{{ translateEnum('reportStatus', row.reportStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('phase11.requestRevision')" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.teacherRevisionRequested" round size="small" type="warning">{{ t('common.yes') }}</el-tag>
            <el-tag v-else round size="small" type="info">{{ t('common.no') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="adminNote" :label="t('phase11.adminNote')" min-width="320" />
      </el-table>

      <el-empty v-if="!loading && !reports.length" :description="t('phase11.emptyReports')" />
    </el-card>
  </div>
</template>
