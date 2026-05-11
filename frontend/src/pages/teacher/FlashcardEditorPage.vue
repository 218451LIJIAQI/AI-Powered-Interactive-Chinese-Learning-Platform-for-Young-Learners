<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { teacherActivityService } from '../../services/teacherActivityService'
import { teacherContentService } from '../../services/teacherContentService'
import { useAuthStore } from '../../stores/authStore'
import type { LessonItem } from '../../types/contentManagement'
import type { FlashcardFormPayload, FlashcardItem } from '../../types/learning'

const { t } = useI18n()
const authStore = useAuthStore()

const loadingLessons = ref(true)
const loadingFlashcards = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const selectedLessonId = ref<number | null>(null)
const editingId = ref<number | null>(null)
const lessons = ref<LessonItem[]>([])
const flashcards = ref<FlashcardItem[]>([])
const formRef = ref<FormInstance | null>(null)

const form = reactive<FlashcardFormPayload>({
  chineseText: '',
  pinyin: '',
  englishMeaning: '',
  malayMeaning: '',
  imageUrl: '',
  audioUrl: '',
  extraPayload: '',
  displayOrder: 1,
})

const rules = computed<FormRules>(() => ({
  chineseText: [
    { required: true, message: t('learning.validation.flashcardRequired'), trigger: 'blur' },
  ],
  englishMeaning: [
    { required: true, message: t('learning.validation.flashcardRequired'), trigger: 'blur' },
  ],
}))

const teacherLocked = computed(() => authStore.teacherStatus !== 'APPROVED')
const selectedLesson = computed(() => lessons.value.find((lesson) => lesson.id === selectedLessonId.value) ?? null)
const lessonEditable = computed(() =>
  selectedLesson.value ? ['DRAFT', 'REJECTED'].includes(selectedLesson.value.contentStatus) : false,
)

function resetForm() {
  editingId.value = null
  form.chineseText = ''
  form.pinyin = ''
  form.englishMeaning = ''
  form.malayMeaning = ''
  form.imageUrl = ''
  form.audioUrl = ''
  form.extraPayload = ''
  form.displayOrder = flashcards.value.length + 1
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

async function loadFlashcards() {
  if (!selectedLessonId.value) {
    flashcards.value = []
    return
  }

  loadingFlashcards.value = true

  try {
    flashcards.value = await teacherActivityService.getFlashcards(selectedLessonId.value)
  } catch (error) {
    flashcards.value = []
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    loadingFlashcards.value = false
  }
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(flashcard: FlashcardItem) {
  editingId.value = flashcard.id
  form.chineseText = flashcard.chineseText
  form.pinyin = flashcard.pinyin ?? ''
  form.englishMeaning = flashcard.englishMeaning
  form.malayMeaning = flashcard.malayMeaning ?? ''
  form.imageUrl = flashcard.imageUrl ?? ''
  form.audioUrl = flashcard.audioUrl ?? ''
  form.extraPayload = ''
  form.displayOrder = flashcard.displayOrder ?? 0
  dialogVisible.value = true
}

async function saveFlashcard() {
  if (!selectedLessonId.value || teacherLocked.value || !lessonEditable.value) {
    ElMessage.warning(t('learning.teacherActivityLocked'))
    return
  }

  if (!formRef.value) {
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true

  try {
    const payload: FlashcardFormPayload = {
      chineseText: form.chineseText.trim(),
      pinyin: form.pinyin?.trim() || undefined,
      englishMeaning: form.englishMeaning.trim(),
      malayMeaning: form.malayMeaning?.trim() || undefined,
      imageUrl: form.imageUrl?.trim() || undefined,
      audioUrl: form.audioUrl?.trim() || undefined,
      extraPayload: form.extraPayload?.trim() || undefined,
      displayOrder: Number(form.displayOrder ?? 0),
    }

    if (editingId.value) {
      await teacherActivityService.updateFlashcard(editingId.value, payload)
    } else {
      await teacherActivityService.createFlashcard(selectedLessonId.value, payload)
    }

    ElMessage.success(t('content.messages.saved'))
    dialogVisible.value = false
    resetForm()
    await loadFlashcards()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function deleteFlashcard(flashcardId: number) {
  try {
    await teacherActivityService.deleteFlashcard(flashcardId)
    ElMessage.success(t('learning.messages.flashcardDeleted'))
    await loadFlashcards()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

watch(selectedLessonId, async () => {
  dialogVisible.value = false
  resetForm()
  await loadFlashcards()
})

// `loadLessons()` sets `selectedLessonId` which triggers the watch above and
// already calls `loadFlashcards()`. Re-calling it here would double-fetch.
onMounted(() => {
  loadLessons()
})
</script>

<template>
  <div class="dashboard-stack teacher-activity-page">
    <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('learning.flashcardsEyebrow') }}</p>
          <h1>{{ t('learning.flashcardsTitle') }}</h1>
          <p>{{ t('learning.flashcardsDescription') }}</p>
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
            {{ t('learning.addFlashcard') }}
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

    <el-card class="dashboard-panel" shadow="hover">
      <div class="panel-header">
        <div>
          <p class="section-label">{{ t('learning.flashcards') }}</p>
          <h2>{{ selectedLesson?.title || t('learning.selectLesson') }}</h2>
        </div>
      </div>

      <el-table v-loading="loadingFlashcards" :data="flashcards" class="phase-table">
        <el-table-column prop="chineseText" :label="t('learning.fields.chineseText')" min-width="180" />
        <el-table-column prop="pinyin" :label="t('learning.fields.pinyin')" min-width="160" />
        <el-table-column prop="englishMeaning" :label="t('learning.fields.englishMeaning')" min-width="220" />
        <el-table-column prop="malayMeaning" :label="t('learning.fields.malayMeaning')" min-width="220" />
        <el-table-column prop="displayOrder" :label="t('content.displayOrder')" width="120" />
        <el-table-column width="220">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button plain size="small" :disabled="teacherLocked || !lessonEditable" @click="openEditDialog(row)">
                {{ t('content.edit') }}
              </el-button>
              <el-button plain type="danger" size="small" :disabled="teacherLocked || !lessonEditable" @click="deleteFlashcard(row.id)">
                {{ t('content.archive') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loadingFlashcards && !flashcards.length" :description="t('learning.noFlashcards')" />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? t('learning.editFlashcard') : t('learning.addFlashcard')"
      width="720px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="content-form-grid">
          <el-form-item :label="t('learning.fields.chineseText')" prop="chineseText">
            <el-input v-model="form.chineseText" maxlength="500" />
          </el-form-item>
          <el-form-item :label="t('learning.fields.pinyin')">
            <el-input v-model="form.pinyin" maxlength="255" />
          </el-form-item>
          <el-form-item :label="t('learning.fields.englishMeaning')" prop="englishMeaning">
            <el-input v-model="form.englishMeaning" maxlength="500" />
          </el-form-item>
          <el-form-item :label="t('learning.fields.malayMeaning')">
            <el-input v-model="form.malayMeaning" maxlength="500" />
          </el-form-item>
          <el-form-item :label="t('learning.fields.imageUrl')">
            <el-input v-model="form.imageUrl" maxlength="255" />
          </el-form-item>
          <el-form-item :label="t('learning.fields.audioUrl')">
            <el-input v-model="form.audioUrl" maxlength="255" />
          </el-form-item>
          <el-form-item :label="t('content.displayOrder')">
            <el-input-number v-model="form.displayOrder" :min="0" />
          </el-form-item>
        </div>

        <el-form-item :label="t('learning.fields.extraPayload')">
          <el-input v-model="form.extraPayload" type="textarea" :rows="3" maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="table-actions">
          <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="saving" @click="saveFlashcard">{{ t('content.save') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
