<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { liveClassService } from '../../services/liveClassService'
import type { LiveClass } from '../../types/liveLearning'

const { t } = useI18n()
const loading = ref(true)
const items = ref<LiveClass[]>([])

function formatDateTime(value: string | null) {
  if (!value) return '-'
  return new Date(value).toLocaleString('en-MY')
}

function statusType(status: string | null) {
  if (status === 'ONGOING') return 'success'
  if (status === 'CANCELLED') return 'danger'
  if (status === 'RESCHEDULED') return 'warning'
  return 'info'
}

async function loadLiveClasses() {
  loading.value = true
  try {
    items.value = await liveClassService.getStudentLiveClasses()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    loading.value = false
  }
}

async function enrol(item: LiveClass) {
  try {
    await liveClassService.enrolLiveClass(item.id)
    ElMessage.success(t('phase10.messages.enrolSuccess'))
    await loadLiveClasses()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

async function cancelEnrolment(item: LiveClass) {
  try {
    await liveClassService.cancelLiveClassEnrolment(item.id)
    ElMessage.success(t('phase10.messages.cancelEnrolmentSuccess'))
    await loadLiveClasses()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

async function join(item: LiveClass) {
  try {
    const result = await liveClassService.joinLiveClass(item.id)
    window.open(result.meetingLink, '_blank', 'noopener')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('phase10.joinUnavailable'))
  }
}

onMounted(() => {
  loadLiveClasses()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('phase10.liveClassesEyebrow')"
      :title="t('phase10.liveClassesTitle')"
      :description="t('phase10.liveClassesDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <el-table v-loading="loading" :data="items" class="phase-table">
        <el-table-column prop="title" :label="t('phase10.liveClasses')" min-width="220" />
        <el-table-column :label="t('phase10.meetingPlatform')" min-width="140">
          <template #default="{ row }">{{ row.meetingPlatform || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('phase10.startTime')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('phase10.endTime')" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column :label="t('commerce.status')" min-width="140">
          <template #default="{ row }">
            <el-tag round :type="statusType(row.liveClassStatus)">{{ row.liveClassStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('phase10.enrolment')" min-width="120">
          <template #default="{ row }">
            <el-tag round :type="row.enrolled ? 'success' : row.accessAllowed ? 'info' : 'warning'">
              {{ row.enrolled ? t('phase10.enrolled') : row.accessAllowed ? t('phase10.available') : t('content.locked') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column width="320">
          <template #default="{ row }">
            <div class="table-actions">
              <router-link :to="{ name: 'student-live-class-detail', params: { id: row.id } }">
                <el-button plain type="primary">{{ t('content.reviewDetail') }}</el-button>
              </router-link>
              <el-button v-if="row.canEnrol" type="primary" @click="enrol(row)">{{ t('phase10.enrol') }}</el-button>
              <el-button v-else-if="row.enrolled" plain type="warning" @click="cancelEnrolment(row)">
                {{ t('phase10.cancelEnrolment') }}
              </el-button>
              <el-button v-if="row.canJoin" type="success" @click="join(row)">{{ t('phase10.joinClass') }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !items.length" :description="t('phase10.emptyLiveClasses')" />
    </el-card>
  </div>
</template>
