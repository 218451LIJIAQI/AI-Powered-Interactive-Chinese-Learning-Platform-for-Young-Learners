<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { useNumericRouteParam } from '../../composables/useRouteParam'
import { recordedLessonService } from '../../services/recordedLessonService'
import { videoProgressService } from '../../services/videoProgressService'
import type { RecordedLessonVideo, VideoProgress } from '../../types/liveLearning'

const route = useRoute()
const { t } = useI18n()

const loading = ref(true)
const video = ref<RecordedLessonVideo | null>(null)
const progress = ref<VideoProgress | null>(null)
const watchedSeconds = ref(0)

const videoId = useNumericRouteParam('id')

async function loadVideo() {
  loading.value = true
  try {
    const lessonId = Number(route.query.lessonId || 0)
    if (!lessonId) {
      throw new Error(t('phase10.lessonContextRequired'))
    }
    video.value = await recordedLessonService.getStudentRecordedVideo(lessonId, videoId.value)
    progress.value = await videoProgressService.getVideoProgress(videoId.value)
    watchedSeconds.value = progress.value.watchedSeconds ?? 0
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    loading.value = false
  }
}

async function saveProgress() {
  try {
    progress.value = await videoProgressService.saveVideoProgress(videoId.value, {
      watchedSeconds: watchedSeconds.value,
      totalSeconds: video.value?.durationSeconds ?? progress.value?.totalSeconds ?? 0,
    })
    ElMessage.success(t('phase10.messages.progressSaved'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

async function markCompleted() {
  try {
    progress.value = await videoProgressService.completeVideo(videoId.value)
    watchedSeconds.value = progress.value.totalSeconds
    ElMessage.success(t('phase10.messages.completed'))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

onMounted(() => {
  loadVideo()
})
</script>

<template>
  <div class="page-stack student-learning-page">
    <PageHero
      :eyebrow="t('phase10.recordedLessonsEyebrow')"
      :title="video?.title || t('phase10.recordedLessons')"
      :description="video?.description || t('phase10.recordedLessonsDescription')"
    />

    <el-card v-loading="loading" class="dashboard-panel" shadow="hover">
      <template v-if="video">
        <video
          v-if="video.sourceType === 'UPLOADED_FILE' && video.streamUrl"
          controls
          class="recorded-player"
          :src="video.streamUrl"
        />
        <div v-else class="table-actions">
          <el-button
            tag="a"
            type="primary"
            :href="video.externalVideoUrl || undefined"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ t('phase10.openRecordedVideo') }}
          </el-button>
        </div>

        <div class="dashboard-bubble">
          <span class="dashboard-bubble__label">{{ t('phase10.videoProgress') }}</span>
          <strong>{{ progress?.progressPercentage ?? 0 }}%</strong>
        </div>

        <el-progress :percentage="progress?.progressPercentage ?? 0" :stroke-width="16" />

        <div class="content-toolbar">
          <el-input-number v-model="watchedSeconds" :min="0" :max="video.durationSeconds || 99999" />
          <el-button type="primary" @click="saveProgress">{{ t('phase10.saveProgress') }}</el-button>
          <el-button type="success" @click="markCompleted">{{ t('phase10.markCompleted') }}</el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>
