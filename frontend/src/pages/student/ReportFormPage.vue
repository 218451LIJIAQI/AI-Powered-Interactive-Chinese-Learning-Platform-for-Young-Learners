<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { APP_EVENTS, dispatchAppEvent } from '../../constants/appEvents'
import { reportService } from '../../services/reportService'
import type { ReportTargetType } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const router = useRouter()
const { t } = useI18n()
const saving = ref(false)
const formRef = ref<FormInstance | null>(null)
const form = reactive<{
  targetType: ReportTargetType
  targetId: number | null
  reason: string
}>({
  targetType: 'LESSON',
  targetId: null,
  reason: '',
})

const rules = computed<FormRules>(() => ({
  targetId: [
    {
      validator: (_rule, value: unknown, callback) => {
        if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) {
          callback(new Error(t('phase11.validation.targetIdRequired')))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  reason: [
    { required: true, message: t('phase11.validation.reasonRequired'), trigger: 'blur' },
    { min: 10, message: t('phase11.validation.reasonMin'), trigger: 'blur' },
  ],
}))

async function submit() {
  if (!formRef.value) {
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await reportService.createReport({
      targetType: form.targetType,
      targetId: form.targetId,
      reason: form.reason.trim(),
    })
    ElMessage.success(t('phase11.messages.reportSubmitted'))
    dispatchAppEvent(APP_EVENTS.NOTIFICATIONS_REFRESH)
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
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
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
        <el-form-item :label="t('phase11.reportTargetId')" prop="targetId">
          <el-input-number v-model="form.targetId" :min="1" />
        </el-form-item>
        <el-form-item :label="t('phase11.reportReason')" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="6" maxlength="2000" show-word-limit />
        </el-form-item>
        <div class="table-actions">
          <el-button @click="router.push('/student/reports')">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" native-type="submit" @click="submit">{{ t('phase11.submitReport') }}</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>
