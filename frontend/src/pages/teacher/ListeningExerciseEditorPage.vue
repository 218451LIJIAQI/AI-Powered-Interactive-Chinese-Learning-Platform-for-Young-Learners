<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { teacherActivityService } from '../../services/teacherActivityService'
import { teacherContentService } from '../../services/teacherContentService'
import { useAuthStore } from '../../stores/authStore'
import type { LessonItem } from '../../types/contentManagement'
import type {
  ListeningExercise,
  ListeningExerciseFormPayload,
  ListeningOptionFormPayload,
} from '../../types/learning'

const { t } = useI18n()
const authStore = useAuthStore()

const loadingLessons = ref(true)
const loadingExercises = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const selectedLessonId = ref<number | null>(null)
const editingId = ref<number | null>(null)
const lessons = ref<LessonItem[]>([])
const exercises = ref<ListeningExercise[]>([])
const options = ref<ListeningOptionFormPayload[]>([])

const form = reactive<ListeningExerciseFormPayload>({
  title: '',
  chineseText: '',
  pinyin: '',
  audioUrl: '',
  options: [],
  explanation: '',
})

const teacherLocked = computed(() => authStore.teacherStatus !== 'APPROVED')
const selectedLesson = computed(() => lessons.value.find((lesson) => lesson.id === selectedLessonId.value) ?? null)
const lessonEditable = computed(() =>
  selectedLesson.value ? ['DRAFT', 'REJECTED'].includes(selectedLesson.value.contentStatus) : false,
)

function createOption(order: number): ListeningOptionFormPayload {
  return {
    optionText: '',
    correctOption: false,
    displayOrder: order,
  }
}

function resetForm() {
  editingId.value = null
  form.title = ''
  form.chineseText = ''
  form.pinyin = ''
  form.audioUrl = ''
  form.explanation = ''
  options.value = [createOption(1), createOption(2), createOption(3)]
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
    exercises.value = await teacherActivityService.getListeningExercises(selectedLessonId.value)
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

function openEditDialog(exercise: ListeningExercise) {
  editingId.value = exercise.id
  form.title = exercise.title
  form.chineseText = exercise.chineseText ?? ''
  form.pinyin = exercise.pinyin ?? ''
  form.audioUrl = exercise.audioUrl ?? ''
  form.explanation = exercise.explanation ?? ''
  options.value = exercise.options.map((option) => ({
    optionText: option.optionText,
    correctOption: option.correctOption,
    displayOrder: option.displayOrder,
  }))
  dialogVisible.value = true
}

function addOption() {
  options.value.push(createOption(options.value.length + 1))
}

function removeOption(index: number) {
  if (options.value.length <= 2) {
    return
  }

  options.value.splice(index, 1)
  options.value.forEach((option, optionIndex) => {
    option.displayOrder = optionIndex + 1
  })
}

async function saveExercise() {
  if (!selectedLessonId.value || teacherLocked.value || !lessonEditable.value) {
    ElMessage.warning(t('learning.teacherActivityLocked'))
    return
  }

  if (!form.title.trim() || !form.chineseText.trim()) {
    ElMessage.error(t('learning.validation.listeningRequired'))
    return
  }

  if (options.value.some((option) => !option.optionText.trim()) || !options.value.some((option) => option.correctOption)) {
    ElMessage.error(t('learning.validation.listeningOptionsRequired'))
    return
  }

  saving.value = true

  try {
    const payload: ListeningExerciseFormPayload = {
      title: form.title.trim(),
      chineseText: form.chineseText.trim(),
      pinyin: form.pinyin?.trim() || undefined,
      audioUrl: form.audioUrl?.trim() || undefined,
      explanation: form.explanation?.trim() || undefined,
      options: options.value.map((option, index) => ({
        optionText: option.optionText.trim(),
        correctOption: option.correctOption,
        displayOrder: Number(option.displayOrder ?? index + 1),
      })),
    }

    if (editingId.value) {
      await teacherActivityService.updateListeningExercise(editingId.value, payload)
    } else {
      await teacherActivityService.createListeningExercise(selectedLessonId.value, payload)
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
    await teacherActivityService.deleteListeningExercise(exerciseId)
    ElMessage.success(t('learning.messages.listeningDeleted'))
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
  resetForm()
  await loadLessons()
  await loadExercises()
})
</script>

<template>
  <div class="dashboard-stack teacher-activity-page">
    <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('learning.listeningEyebrow') }}</p>
          <h1>{{ t('learning.listeningTitle') }}</h1>
          <p>{{ t('learning.listeningDescription') }}</p>
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
            {{ t('learning.addListeningExercise') }}
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
            <p class="section-label">{{ t('learning.listening') }}</p>
            <h2>{{ exercise.title }}</h2>
          </div>
          <el-tag round>{{ exercise.options.length }} {{ t('learning.options') }}</el-tag>
        </div>

        <p>{{ exercise.chineseText }}</p>
        <p>{{ exercise.pinyin || '-' }}</p>

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

    <el-empty v-if="!loadingExercises && !exercises.length" :description="t('learning.noListeningExercises')" />

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('learning.editListeningExercise') : t('learning.addListeningExercise')"
      width="820px"
    >
      <el-form label-position="top">
        <div class="content-form-grid">
          <el-form-item :label="t('content.fields.title')">
            <el-input v-model="form.title" maxlength="200" />
          </el-form-item>
          <el-form-item :label="t('learning.fields.chineseText')">
            <el-input v-model="form.chineseText" maxlength="500" />
          </el-form-item>
          <el-form-item :label="t('learning.fields.pinyin')">
            <el-input v-model="form.pinyin" maxlength="255" />
          </el-form-item>
          <el-form-item :label="t('learning.fields.audioUrl')">
            <el-input v-model="form.audioUrl" maxlength="255" />
          </el-form-item>
        </div>

        <el-form-item :label="t('learning.fields.explanation')">
          <el-input v-model="form.explanation" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>

        <div class="stacked-summary-list">
          <section v-for="(option, index) in options" :key="`${index}-${option.displayOrder}`" class="workspace-placeholder__panel">
            <div class="content-inline-form">
              <el-input v-model="option.optionText" :placeholder="`${t('learning.fields.optionText')} ${index + 1}`" />
              <el-checkbox v-model="option.correctOption">{{ t('learning.fields.correctOption') }}</el-checkbox>
              <el-input-number v-model="option.displayOrder" :min="0" />
              <el-button plain type="danger" :disabled="options.length <= 2" @click="removeOption(index)">
                {{ t('learning.removeOption') }}
              </el-button>
            </div>
          </section>
        </div>

        <el-button plain @click="addOption">{{ t('learning.addOption') }}</el-button>
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
