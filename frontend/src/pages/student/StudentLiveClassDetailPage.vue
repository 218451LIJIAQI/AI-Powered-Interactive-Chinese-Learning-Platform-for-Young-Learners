<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { liveClassService } from '../../services/liveClassService'
import type { LiveClass } from '../../types/liveLearning'

const route = useRoute()
const { t } = useI18n()

const loading = ref(true)
const item = ref<LiveClass | null>(null)

function formatDateTime(value: string | null) {
  if (!value) return '-'
  return new Date(value).toLocaleString('en-MY')
}

async function loadDetail() {
  loading.value = true
  try {
    item.value = await liveClassService.getStudentLiveClass(Number(route.params.id))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    loading.value = false
  }
}

async function joinNow() {
  if (!item.value) return
  try {
    const result = await liveClassService.joinLiveClass(item.value.id)
    window.open(result.meetingLink, '_blank', 'noopener')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('phase10.joinUnavailable'))
  }
}

onMounted(() => {
  loadDetail()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('phase10.liveClassDetailEyebrow')"
      :title="item?.title || t('phase10.liveClasses')"
      :description="item?.description || t('phase10.liveClassDetailDescription')"
    />

    <el-card v-loading="loading" class="dashboard-panel" shadow="hover">
      <template v-if="item">
        <div class="detail-grid">
          <div>
            <p class="section-label">{{ t('phase10.startTime') }}</p>
            <strong>{{ formatDateTime(item.startTime) }}</strong>
          </div>
          <div>
            <p class="section-label">{{ t('phase10.endTime') }}</p>
            <strong>{{ formatDateTime(item.endTime) }}</strong>
          </div>
          <div>
            <p class="section-label">{{ t('phase10.meetingPlatform') }}</p>
            <strong>{{ item.meetingPlatform || '-' }}</strong>
          </div>
          <div>
            <p class="section-label">{{ t('phase10.enrolledStudents') }}</p>
            <strong>{{ item.enrolledCount }}</strong>
          </div>
        </div>

        <el-alert
          v-if="!item.accessAllowed"
          :title="t('phase10.joinLockedMessage')"
          type="warning"
          :closable="false"
          show-icon
        />

        <div class="table-actions">
          <el-button v-if="item.canJoin" type="success" @click="joinNow">{{ t('phase10.joinClass') }}</el-button>
          <el-tag round :type="item.enrolled ? 'success' : 'info'">
            {{ item.enrolled ? t('phase10.enrolled') : t('phase10.notEnrolled') }}
          </el-tag>
        </div>
      </template>
    </el-card>
  </div>
</template>
