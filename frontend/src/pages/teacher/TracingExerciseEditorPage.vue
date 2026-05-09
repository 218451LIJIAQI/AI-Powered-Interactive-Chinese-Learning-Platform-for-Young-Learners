<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { teacherActivityService } from '../../services/teacherActivityService'
import { teacherContentService } from '../../services/teacherContentService'
import { useAuthStore } from '../../stores/authStore'
import type { LessonItem } from '../../types/contentManagement'
import type { TracingExercise, TracingExerciseFormPayload } from '../../types/learning'

const { t } = useI18n()
const authStore = useAuthStore()

const loadingLessons = ref(true)
const loadingExercises = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const selectedLessonId = ref<number | null>(null)
const editingId = ref<number | null>(null)
const lessons = ref<LessonItem[]>([])
const exercises = ref<TracingExercise[]>([])

const form = reactive<TracingExerciseFormPayload>({
  title: '',
  targetCharacter: '',
  pinyin: '',
  meaning: '',
  worksheetUrl: '',
  instructions: '',
})

const teacherLocked = computed(() => authStore.teacherStatus !== 'APPROVED')
const selectedLesson = computed(() => lessons.value.find((lesson) => lesson.id === selectedLessonId.value) ?? null)
const lessonEditable = computed(() =>
  selectedLesson.value ? ['DRAFT', 'REJECTED'].includes(selectedLesson.value.contentStatus) : false,
)

function resetForm() {
  editingId.value = null
  form.title = ''
  form.targetCharacter = ''
  form.pinyin = ''
  form.meaning = ''
  form.worksheetUrl = ''
  form.instructions = ''
}

async function loadLessons() {
  loadingLessons.value = true

  try {
    lessons.value = (await teacherContentService.getLessons()).filter((lesson) => lesson.contentStatus !== 'ARCHIVED')
    if (!selectedLessonId.value && lessons.value.length) {
      selectedLessonId.value = lessons.value.find((lesson) => ['DRAFT', 'REJECTED'].includes(lesson.contentStatus))?.id
        ?? lessons.value[0].id
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    loadingLessons.value = false
  }
}

async function loadExercises() {
  if (!selectedLessonId.value) {
    exercises.value = []
    return
  }

  loadingExercises.value = true

  try {
    exercises.value = await teacherActivityService.getTracingExercises(selectedLessonId.value)
  } catch (error) {
    exercises.value = []
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    loadingExercises.value = false
  }
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(exercise: TracingExercise) {
  editingId.value = exercise.id
  form.title = exercise.title
  form.targetCharacter = exercise.targetCharacter
  form.pinyin = exercise.pinyin ?? ''
  form.meaning = exercise.meaning ?? ''
  form.worksheetUrl = exercise.worksheetUrl ?? ''
  form.instructions = exercise.instructions ?? ''
  dialogVisible.value = true
}

async function saveExercise() {
  if (!selectedLessonId.value || teacherLocked.value || !lessonEditable.value) {
    ElMessage.warning(t('learning.teacherActivityLocked'))
    return
  }

  if (!form.title.trim() || !form.targetCharacter.trim()) {
    ElMessage.error(t('learning.validation.tracingRequired'))
    return
  }

  saving.value = true

  try {
    const payload: TracingExerciseFormPayload = {
      title: form.title.trim(),
      targetCharacter: form.targetCharacter.trim(),
      pinyin: form.pinyin?.trim() || undefined,
      meaning: form.meaning?.trim() || undefined,
      worksheetUrl: form.worksheetUrl?.trim() || undefined,
      instructions: form.instructions?.trim() || undefined,
    }

    if (editingId.value) {
      await teacherActivityService.updateTracingExercise(editingId.value, payload)
    } else {
      await teacherActivityService.createTracingExercise(selectedLessonId.value, payload)
    }

    ElMessage.success(t('content.messages.saved'))
    dialogVisible.value = false
    resetForm()
    await loadExercises()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function deleteExercise(exerciseId: number) {
  try {
    await teacherActivityService.deleteTracingExercise(exerciseId)
    ElMessage.success(t('learning.messages.tracingDeleted'))
    await loadExercises()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

watch(selectedLessonId, async () => {
  dialogVisible.value = false
  resetForm()
  await loadExercises()
})

onMounted(async () => {
  await loadLessons()
  await loadExercises()
})
</script>

<template>
  <div class="dashboard-stack teacher-activity-page">
    <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('learning.tracingEyebrow') }}</p>
          <h1>{{ t('learning.tracingTitle') }}</h1>
          <p>{{ t('learning.tracingDescription') }}</p>
        </div>
        <div class="teacher-profile-actions">
          <el-select
            v-model="selectedLessonId"
            class="content-filter"
            :placeholder="t('learning.selectLesson')"
            :loading="loadingLessons"
          >
            <el-option
              v-for="lesson in lessons"
              :key="lesson.id"
              :label="`${lesson.title} (${lesson.contentStatus})`"
              :value="lesson.id"
            />
          </el-select>
          <el-button type="primary" :disabled="teacherLocked || !lessonEditable || !selectedLessonId" @click="openCreateDialog">
            {{ t('learning.addTracingExercise') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <el-alert v-if="teacherLocked" :title="t('content.teacherApprovalRequired')" type="warning" show-icon :closable="false" />
    <el-alert
      v-else-if="selectedLesson && !lessonEditable"
      :title="t('learning.lessonStatusLocked')"
      type="info"
      show-icon
      :closable="false"
    />

    <section class="dashboard-grid">
      <el-card v-for="exercise in exercises" :key="exercise.id" class="dashboard-panel" shadow="hover">
        <div class="panel-header">
          <div>
            <p class="section-label">{{ t('learning.tracing') }}</p>
            <h2>{{ exercise.title }}</h2>
          </div>
          <el-tag round>{{ exercise.targetCharacter }}</el-tag>
        </div>

        <p>{{ exercise.pinyin || '-' }}</p>
        <p>{{ exercise.meaning || '-' }}</p>
        <p>{{ exercise.instructions || '-' }}</p>

        <div class="table-actions">
          <el-button plain size="small" :disabled="teacherLocked || !lessonEditable" @click="openEditDialog(exercise)">
            {{ t('content.edit') }}
          </el-button>
          <el-button plain type="danger" size="small" :disabled="teacherLocked || !lessonEditable" @click="deleteExercise(exercise.id)">
            {{ t('content.archive') }}
          </el-button>
        </div>
      </el-card>
    </section>

    <el-empty v-if="!loadingExercises && !exercises.length" :description="t('learning.noTracingExercises')" />

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('learning.editTracingExercise') : t('learning.addTracingExercise')"
      width="720px"
    >
      <el-form label-position="top">
        <div class="content-form-grid">
          <el-form-item :label="t('content.fields.title')">
            <el-input v-model="form.title" maxlength="200" />
          </el-form-item>
          <el-form-item :label="t('learning.fields.targetCharacter')">
            <el-input v-model="form.targetCharacter" maxlength="100" />
          </el-form-item>
          <el-form-item :label="t('learning.fields.pinyin')">
            <el-input v-model="form.pinyin" maxlength="255" />
          </el-form-item>
          <el-form-item :label="t('learning.fields.meaning')">
            <el-input v-model="form.meaning" maxlength="500" />
          </el-form-item>
          <el-form-item :label="t('learning.fields.worksheetUrl')">
            <el-input v-model="form.worksheetUrl" maxlength="255" />
          </el-form-item>
        </div>

        <el-form-item :label="t('learning.fields.instructions')">
          <el-input v-model="form.instructions" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="table-actions">
          <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="saveExercise">{{ t('content.save') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
