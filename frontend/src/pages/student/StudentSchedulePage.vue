<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { scheduleService } from '../../services/scheduleService'
import type { StudentSchedule } from '../../types/liveLearning'
import { formatDateTime } from '../../utils/datetime'

const { t } = useI18n()
const router = useRouter()
const loading = ref(true)
const schedule = ref<StudentSchedule | null>(null)

async function openRoute(routePath: string | null) {
  if (!routePath) {
    return
  }

  await router.push(routePath)
}

async function loadSchedule() {
  loading.value = true
  try {
    schedule.value = await scheduleService.getStudentSchedule()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadSchedule()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('phase10.scheduleEyebrow')"
      :title="t('phase10.studentScheduleTitle')"
      :description="t('phase10.studentScheduleDescription')"
    />

    <el-card v-loading="loading" class="dashboard-panel" shadow="hover">
      <h2>{{ t('phase10.upcomingLiveClasses') }}</h2>
      <el-timeline v-if="schedule?.upcomingLiveClasses.length">
        <el-timeline-item
          v-for="item in schedule?.upcomingLiveClasses"
          :key="`${item.itemType}-${item.itemId}`"
          :timestamp="formatDateTime(item.startTime)"
        >
          <strong>{{ item.title }}</strong>
          <p>{{ item.secondaryLabel }}</p>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else :description="t('phase10.emptySchedule')" />

      <h2>{{ t('phase10.continueWatching') }}</h2>
      <el-table :data="schedule?.continueWatching || []" class="phase-table">
        <el-table-column prop="title" :label="t('phase10.recordedVideo')" min-width="220" />
        <el-table-column prop="secondaryLabel" :label="t('phase10.videoProgress')" min-width="120" />
        <el-table-column width="180">
          <template #default="{ row }">
            <el-button v-if="row.routePath" type="primary" @click="openRoute(row.routePath)">
              {{ t('phase10.continueWatching') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
