<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import { recordedLessonService } from '../../services/recordedLessonService'
import { teacherContentService } from '../../services/teacherContentService'
import type { CoursePackageItem, LessonItem } from '../../types/contentManagement'
import type { RecordedLessonVideo, RecordedLessonVideoPayload } from '../../types/liveLearning'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const selectedLessonId = ref<number | null>(null)
const lessons = ref<LessonItem[]>([])
const packages = ref<CoursePackageItem[]>([])
const videos = ref<RecordedLessonVideo[]>([])
const sourceType = ref<'UPLOADED_FILE' | 'EXTERNAL_URL'>('EXTERNAL_URL')
const uploadFile = ref<File | null>(null)
const editingVideoId = ref<number | null>(null)

const form = reactive<RecordedLessonVideoPayload>({
  title: '',
  description: '',
  sourceType: 'EXTERNAL_URL',
  externalVideoUrl: '',
  durationSeconds: null,
  sortOrder: 0,
})

const selectedLesson = computed(() => lessons.value.find((lesson) => lesson.id === selectedLessonId.value) || null)

async function loadLessons() {
  const [lessonList, packageList] = await Promise.all([
    teacherContentService.getLessons(),
    teacherContentService.getPackages(),
  ])
  lessons.value = lessonList
  packages.value = packageList
  if (!selectedLessonId.value && lessons.value.length) {
    selectedLessonId.value = lessons.value[0].id
  }
}

async function loadVideos() {
  if (!selectedLessonId.value) return
  loading.value = true
  try {
    videos.value = await recordedLessonService.getTeacherRecordedVideos(selectedLessonId.value)
  } finally {
    loading.value = false
  }
}

async function addVideo() {
  if (!selectedLessonId.value) return
  saving.value = true
  try {
    form.sourceType = sourceType.value
    if (editingVideoId.value) {
      await recordedLessonService.updateRecordedVideo(selectedLessonId.value, editingVideoId.value, form, uploadFile.value)
      ElMessage.success(t('phase10.messages.recordedVideoUpdated'))
    } else {
      await recordedLessonService.createRecordedVideo(selectedLessonId.value, form, uploadFile.value)
      ElMessage.success(t('phase10.messages.recordedVideoAdded'))
    }
    resetForm()
    await loadVideos()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    saving.value = false
  }
}

function editVideo(video: RecordedLessonVideo) {
  editingVideoId.value = video.id
  sourceType.value = video.sourceType
  form.title = video.title
  form.description = video.description || ''
  form.sourceType = video.sourceType
  form.packageId = video.packageId
  form.externalVideoUrl = video.externalVideoUrl || ''
  form.durationSeconds = video.durationSeconds
  form.sortOrder = video.sortOrder
  uploadFile.value = null
}

function resetForm() {
  editingVideoId.value = null
  sourceType.value = 'EXTERNAL_URL'
  form.title = ''
  form.description = ''
  form.sourceType = 'EXTERNAL_URL'
  form.packageId = null
  form.externalVideoUrl = ''
  form.durationSeconds = null
  form.sortOrder = 0
  uploadFile.value = null
}

async function archiveVideo(video: RecordedLessonVideo) {
  if (!selectedLessonId.value) return
  try {
    await recordedLessonService.archiveRecordedVideo(selectedLessonId.value, video.id)
    ElMessage.success(t('phase10.messages.recordedVideoArchived'))
    await loadVideos()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

onMounted(async () => {
  await loadLessons()
  await loadVideos()
})
</script>

<template>
  <div class="dashboard-stack">
    <PageHero
      :eyebrow="t('phase10.recordedLessonsEyebrow')"
      :title="t('phase10.teacherRecordedLessonsTitle')"
      :description="t('phase10.teacherRecordedLessonsDescription')"
    />

    <el-card class="dashboard-panel" shadow="hover">
      <div class="content-toolbar">
        <el-select v-model="selectedLessonId" class="content-filter content-filter--wide" @change="loadVideos">
          <el-option v-for="lesson in lessons" :key="lesson.id" :label="lesson.title" :value="lesson.id" />
        </el-select>
      </div>

      <el-form label-position="top">
        <el-form-item :label="t('content.fields.title')">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item :label="t('content.fields.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <div class="content-toolbar">
          <el-form-item :label="t('phase10.videoSourceType')" class="content-filter">
            <el-select v-model="sourceType" class="w-full">
              <el-option label="EXTERNAL_URL" value="EXTERNAL_URL" />
              <el-option label="UPLOADED_FILE" value="UPLOADED_FILE" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('content.package')" class="content-filter content-filter--wide">
            <el-select v-model="form.packageId" class="w-full" clearable>
              <el-option v-for="item in packages" :key="item.id" :label="item.title" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('phase10.duration')" class="content-filter">
            <el-input-number v-model="form.durationSeconds" :min="0" />
          </el-form-item>
          <el-form-item :label="t('content.displayOrder')" class="content-filter">
            <el-input-number v-model="form.sortOrder" :min="0" />
          </el-form-item>
        </div>
        <el-form-item v-if="sourceType === 'EXTERNAL_URL'" :label="t('phase10.externalVideoUrl')">
          <el-input v-model="form.externalVideoUrl" />
        </el-form-item>
        <el-form-item v-else :label="t('phase10.uploadRecordedVideo')">
          <input type="file" accept="video/mp4,video/webm,video/quicktime" @change="uploadFile = ($event.target as HTMLInputElement).files?.[0] || null" />
        </el-form-item>
        <el-button type="primary" :loading="saving" @click="addVideo">{{ t('phase10.addRecordedVideo') }}</el-button>
        <el-button plain @click="resetForm">{{ t('common.cancel') }}</el-button>
      </el-form>
    </el-card>

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ selectedLesson?.title || t('content.lesson') }}</p>
          <h2>{{ t('phase10.recordedVideosList') }}</h2>
        </div>
      </div>
      <el-table v-loading="loading" :data="videos" class="phase-table">
        <el-table-column prop="title" :label="t('phase10.recordedVideo')" min-width="220" />
        <el-table-column prop="sourceType" :label="t('phase10.videoSourceType')" min-width="140" />
        <el-table-column prop="durationSeconds" :label="t('phase10.duration')" min-width="120" />
        <el-table-column prop="sortOrder" :label="t('content.displayOrder')" min-width="120" />
        <el-table-column width="240">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button plain type="primary" size="small" @click="editVideo(row)">{{ t('content.edit') }}</el-button>
              <el-button plain type="danger" size="small" @click="archiveVideo(row)">{{ t('content.archive') }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !videos.length" :description="t('phase10.emptyRecordedVideos')" />
    </el-card>
  </div>
</template>
