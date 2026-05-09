<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { moderationService } from '../../services/moderationService'
import type { ModerationRecordItem } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'

const { t } = useI18n()
const loading = ref(true)
const records = ref<ModerationRecordItem[]>([])

async function loadRecords() {
  loading.value = true
  try {
    records.value = await moderationService.getTeacherModerationRecords()
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
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
      :title="t('phase11.teacherModerationTitle')"
      :description="t('phase11.teacherModerationDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="records" class="phase-table">
        <el-table-column prop="targetType" :label="t('phase11.moderationTargetType')" min-width="140" />
        <el-table-column prop="targetTitle" :label="t('phase11.moderationTargetTitle')" min-width="220" />
        <el-table-column prop="actionTaken" :label="t('phase11.moderationAction')" min-width="180" />
        <el-table-column prop="note" :label="t('phase11.reason')" min-width="320" />
      </el-table>

      <el-empty v-if="!loading && !records.length" :description="t('phase11.emptyModerationRecords')" />
    </el-card>
  </div>
</template>
