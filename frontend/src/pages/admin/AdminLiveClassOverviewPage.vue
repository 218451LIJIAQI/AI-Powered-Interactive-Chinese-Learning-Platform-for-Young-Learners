<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { liveClassService } from '../../services/liveClassService'
import type { LiveClass } from '../../types/liveLearning'

const { t } = useI18n()
const loading = ref(true)
const items = ref<LiveClass[]>([])

function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString('en-MY') : '-'
}

async function loadClasses() {
  loading.value = true
  try {
    items.value = await liveClassService.getAdminLiveClasses()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadClasses()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase10.adminLiveClassesEyebrow')"
      :title="t('phase10.adminLiveClassesTitle')"
      :description="t('phase10.adminLiveClassesDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="items" class="phase-table">
        <el-table-column prop="title" :label="t('phase10.liveClasses')" min-width="220" />
        <el-table-column :label="t('phase10.teacherLabel')" min-width="180">
          <template #default="{ row }">{{ row.teacher?.displayName || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('phase10.startTime')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column prop="liveClassStatus" :label="t('commerce.status')" min-width="140" />
        <el-table-column prop="enrolledCount" :label="t('phase10.enrolledStudents')" min-width="140" />
      </el-table>
    </el-card>
  </div>
</template>
