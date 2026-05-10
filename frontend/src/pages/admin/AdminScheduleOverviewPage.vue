<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useEnumLabel } from '../../composables/useEnumLabel'
import { scheduleService } from '../../services/scheduleService'
import type { AdminSchedule } from '../../types/liveLearning'
import { formatDateTime } from '../../utils/datetime'

const { t } = useI18n()
const translateEnum = useEnumLabel()
const loading = ref(true)
const schedule = ref<AdminSchedule | null>(null)


async function loadSchedule() {
  loading.value = true
  try {
    schedule.value = await scheduleService.getAdminSchedule()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadSchedule()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase10.scheduleEyebrow')"
      :title="t('phase10.adminScheduleTitle')"
      :description="t('phase10.adminScheduleDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="schedule?.liveClasses || []" class="phase-table">
        <el-table-column prop="title" :label="t('phase10.liveClasses')" min-width="220" />
        <el-table-column :label="t('phase10.startTime')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column prop="secondaryLabel" :label="t('phase10.teacherLabel')" min-width="180" />
        <el-table-column :label="t('commerce.status')" min-width="140">
          <template #default="{ row }">
            <el-tag round size="small">{{ translateEnum('liveClassStatus', row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
