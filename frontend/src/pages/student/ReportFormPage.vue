<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { reportService } from '../../services/reportService'
import type { ReportTargetType } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const router = useRouter()
const { t } = useI18n()
const saving = ref(false)
const form = reactive<{
  targetType: ReportTargetType
  targetId: number | null
  reason: string
}>({
  targetType: 'LESSON',
  targetId: null,
  reason: '',
})

async function submit() {
  saving.value = true
  try {
    await reportService.createReport(form)
    ElMessage.success(t('phase11.messages.reportSubmitted'))
    window.dispatchEvent(new CustomEvent('notifications:refresh'))
    await router.push('/student/reports')
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.actionFailed')))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase11.reportsEyebrow')"
      :title="t('phase11.submitReportTitle')"
      :description="t('phase11.submitReportDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-form label-position="top">
        <el-form-item :label="t('phase11.reportTargetType')">
          <el-select v-model="form.targetType" class="w-full">
            <el-option label="LESSON" value="LESSON" />
            <el-option label="PACKAGE" value="PACKAGE" />
            <el-option label="REVIEW" value="REVIEW" />
            <el-option label="TEACHER" value="TEACHER" />
            <el-option label="TECHNICAL" value="TECHNICAL" />
            <el-option label="OTHER" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('phase11.reportTargetId')">
          <el-input-number v-model="form.targetId" :min="1" />
        </el-form-item>
        <el-form-item :label="t('phase11.reportReason')">
          <el-input v-model="form.reason" type="textarea" :rows="6" maxlength="2000" show-word-limit />
        </el-form-item>
        <div class="table-actions">
          <el-button @click="router.push('/student/reports')">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="submit">{{ t('phase11.submitReport') }}</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>
