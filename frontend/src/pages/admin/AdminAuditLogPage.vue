<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { auditLogService } from '../../services/auditLogService'
import type { AuditLogItem } from '../../types/communication'
import { extractApiError } from '../../utils/apiError'
import { formatDateTime } from '../../utils/datetime'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const logs = ref<AuditLogItem[]>([])
const filters = reactive({
  actionType: '',
  targetType: '',
  actor: '',
})


async function loadLogs() {
  loading.value = true
  try {
    logs.value = await auditLogService.getAuditLogs(filters)
  } catch (error) {
    ElMessage.error(extractApiError(error, t('phase11.messages.loadFailed')))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadLogs()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase11.auditEyebrow')"
      :title="t('phase11.auditLogsTitle')"
      :description="t('phase11.auditLogsDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="content-toolbar">
        <el-input v-model="filters.actionType" class="content-filter" :placeholder="t('phase11.actionType')" />
        <el-input v-model="filters.targetType" class="content-filter" :placeholder="t('phase11.moderationTargetType')" />
        <el-input v-model="filters.actor" class="content-filter" :placeholder="t('phase11.actor')" />
        <el-button type="primary" @click="loadLogs">{{ t('content.applyFilters') }}</el-button>
      </div>

      <el-table v-loading="loading" :data="logs" class="phase-table">
        <el-table-column :label="t('phase11.actionType')" min-width="180">
          <template #default="{ row }">{{ translateEnum('moderationAction', row.actionType) }}</template>
        </el-table-column>
        <el-table-column :label="t('phase11.moderationTargetType')" min-width="160">
          <template #default="{ row }">{{ translateEnum('moderationTargetType', row.targetType) }}</template>
        </el-table-column>
        <el-table-column prop="targetId" :label="t('phase11.targetId')" min-width="120" />
        <el-table-column prop="actorEmail" :label="t('phase11.actor')" min-width="220" />
        <el-table-column prop="description" :label="t('phase11.description')" min-width="320" />
        <el-table-column :label="t('commerce.createdAt')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !logs.length" :description="t('phase11.emptyAuditLogs')" />
    </el-card>
  </div>
</template>
