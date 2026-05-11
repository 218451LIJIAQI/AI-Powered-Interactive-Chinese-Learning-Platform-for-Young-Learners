<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { APP_EVENTS, dispatchAppEvent } from '../../constants/appEvents'
import { moderationService } from '../../services/moderationService'
import type { ModerationAction, ModerationRecordItem } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const saving = ref(false)
const records = ref<ModerationRecordItem[]>([])
const form = reactive({
  targetType: 'LESSON',
  targetId: 0,
  reason: '',
  action: 'REQUEST_REVISION' as ModerationAction,
})

async function loadRecords() {
  loading.value = true
  try {
    records.value = await moderationService.getAdminModerationRecords()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

async function submit() {
  saving.value = true
  try {
    await moderationService.applyAction(form.action, {
      targetType: form.targetType as 'LESSON' | 'PACKAGE' | 'REVIEW' | 'TEACHER_PROFILE' | 'OTHER',
      targetId: form.targetId,
      reason: form.reason,
    })
    ElMessage.success(t('phase11.messages.moderationApplied'))
    form.reason = ''
    dispatchAppEvent(APP_EVENTS.NOTIFICATIONS_REFRESH)
    await loadRecords()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.actionFailed')))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadRecords()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase11.moderationEyebrow')"
      :title="t('phase11.adminModerationTitle')"
      :description="t('phase11.adminModerationDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-form label-position="top">
        <div class="content-toolbar">
          <el-form-item :label="t('phase11.moderationTargetType')" class="content-filter">
            <el-select v-model="form.targetType" class="w-full">
              <el-option label="LESSON" value="LESSON" />
              <el-option label="PACKAGE" value="PACKAGE" />
              <el-option label="REVIEW" value="REVIEW" />
              <el-option label="TEACHER_PROFILE" value="TEACHER_PROFILE" />
              <el-option label="OTHER" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('phase11.targetId')" class="content-filter">
            <el-input-number v-model="form.targetId" :min="1" />
          </el-form-item>
          <el-form-item :label="t('phase11.moderationAction')" class="content-filter content-filter--wide">
            <el-select v-model="form.action" class="w-full">
              <el-option label="HIDE" value="HIDE" />
              <el-option label="UNPUBLISH" value="UNPUBLISH" />
              <el-option label="ARCHIVE" value="ARCHIVE" />
              <el-option label="REQUEST_REVISION" value="REQUEST_REVISION" />
              <el-option label="RESTORE" value="RESTORE" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item :label="t('phase11.reason')">
          <el-input v-model="form.reason" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-button type="primary" :loading="saving" @click="submit">{{ t('phase11.applyModerationAction') }}</el-button>
      </el-form>
    </el-card>

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="records" class="phase-table">
        <el-table-column :label="t('phase11.moderationTargetType')" min-width="140">
          <template #default="{ row }">{{ translateEnum('moderationTargetType', row.targetType) }}</template>
        </el-table-column>
        <el-table-column prop="targetTitle" :label="t('phase11.moderationTargetTitle')" min-width="220" />
        <el-table-column :label="t('phase11.moderationAction')" min-width="180">
          <template #default="{ row }">
            <el-tag round size="small" type="warning">{{ translateEnum('moderationAction', row.actionTaken) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="adminName" :label="t('phase11.actor')" min-width="180" />
        <el-table-column prop="note" :label="t('phase11.reason')" min-width="320" />
      </el-table>
    </el-card>
  </div>
</template>
