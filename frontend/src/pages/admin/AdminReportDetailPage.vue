<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { useNumericRouteParam } from '../../composables/useRouteParam'
import { APP_EVENTS, dispatchAppEvent } from '../../constants/appEvents'
import { reportService } from '../../services/reportService'
import type { ReportItem } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const reportId = useNumericRouteParam('id')
const loading = ref(true)
const report = ref<ReportItem | null>(null)

async function loadReport() {
  loading.value = true
  try {
    report.value = await reportService.getAdminReport(reportId.value)
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function promptAction(action: 'review' | 'dismiss' | 'actionTaken' | 'requestRevision') {
  try {
    const prompt = await ElMessageBox.prompt(t('phase11.adminNotePrompt'), t('phase11.adminNote'))
    if (!report.value) return
    if (action === 'review') {
      await reportService.reviewReport(report.value.id, { note: prompt.value })
    } else if (action === 'dismiss') {
      await reportService.dismissReport(report.value.id, { note: prompt.value })
    } else if (action === 'actionTaken') {
      await reportService.markActionTaken(report.value.id, { note: prompt.value })
    } else {
      await reportService.requestRevision(report.value.id, { note: prompt.value })
    }
    ElMessage.success(t('phase11.messages.reportUpdated'))
    dispatchAppEvent(APP_EVENTS.NOTIFICATIONS_REFRESH)
    await loadReport()
  } catch {
    // Dialog cancellation can be ignored.
  }
}

onMounted(() => {
  loadReport()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase11.reportsEyebrow')"
      :title="report?.targetTitle || t('phase11.reportDetailTitle')"
      :description="t('phase11.reportDetailDescription')"
    />

    <el-card v-loading="loading" class="dashboard-panel" shadow="hover">
      <el-descriptions v-if="report" :column="2" border>
        <el-descriptions-item :label="t('phase11.reportTargetType')">{{ translateEnum('reportTargetType', report.targetType) }}</el-descriptions-item>
        <el-descriptions-item :label="t('commerce.status')">{{ translateEnum('reportStatus', report.reportStatus) }}</el-descriptions-item>
        <el-descriptions-item :label="t('phase11.reporter')">{{ report.reporterName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('phase11.relatedTeacher')">{{ report.relatedTeacherName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('phase11.reportReason')" :span="2">{{ report.reason }}</el-descriptions-item>
        <el-descriptions-item :label="t('phase11.adminNote')" :span="2">{{ report.adminNote || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="table-actions">
        <el-button plain type="primary" @click="promptAction('review')">{{ t('phase11.markReviewed') }}</el-button>
        <el-button plain type="warning" @click="promptAction('requestRevision')">{{ t('phase11.requestRevision') }}</el-button>
        <el-button plain type="success" @click="promptAction('actionTaken')">{{ t('phase11.actionTaken') }}</el-button>
        <el-button plain type="danger" @click="promptAction('dismiss')">{{ t('phase11.dismiss') }}</el-button>
      </div>
    </el-card>
  </div>
</template>
