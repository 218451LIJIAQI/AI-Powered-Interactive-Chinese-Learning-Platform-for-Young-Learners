<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

import { teacherActivityService } from '../../services/teacherActivityService'
import { teacherContentService } from '../../services/teacherContentService'
import { useAuthStore } from '../../stores/authStore'
import type { LessonItem } from '../../types/contentManagement'
import type {
  QuizFormPayload,
  QuizOptionFormPayload,
  QuizQuestionFormPayload,
  TeacherQuiz,
  TeacherQuizQuestion,
} from '../../types/learning'

const { t } = useI18n()
const authStore = useAuthStore()

const loadingLessons = ref(true)
const loadingQuizzes = ref(false)
const savingQuiz = ref(false)
const savingQuestion = ref(false)
const quizDialogVisible = ref(false)
const questionDialogVisible = ref(false)
const selectedLessonId = ref<number | null>(null)
const selectedQuizId = ref<number | null>(null)
const editingQuizId = ref<number | null>(null)
const editingQuestionId = ref<number | null>(null)
const lessons = ref<LessonItem[]>([])
const quizzes = ref<TeacherQuiz[]>([])
const options = ref<QuizOptionFormPayload[]>([])

const quizForm = reactive<QuizFormPayload>({
  title: '',
  description: '',
  timeLimitMinutes: 10,
})

const questionForm = reactive<QuizQuestionFormPayload>({
  questionType: 'MULTIPLE_CHOICE',
  questionText: '',
  correctAnswerText: '',
  points: 5,
  displayOrder: 1,
  options: [],
  explanation: '',
})

const teacherLocked = computed(() => authStore.teacherStatus !== 'APPROVED')
const selectedLesson = computed(() => lessons.value.find((lesson) => lesson.id === selectedLessonId.value) ?? null)
const selectedQuiz = computed(() => quizzes.value.find((quiz) => quiz.id === selectedQuizId.value) ?? null)
const lessonEditable = computed(() =>
  selectedLesson.value ? ['DRAFT', 'REJECTED'].includes(selectedLesson.value.contentStatus) : false,
)

const optionBasedTypes = ['MULTIPLE_CHOICE', 'TRUE_FALSE', 'CHOOSE_PINYIN', 'CHOOSE_TONE']
const quizQuestionTypes = [
  'MULTIPLE_CHOICE',
  'TRUE_FALSE',
  'FILL_BLANK',
  'CHOOSE_PINYIN',
  'CHOOSE_TONE',
  'MATCH_WORD_MEANING',
  'MATCH_WORD_IMAGE',
  'LISTEN_CHOOSE',
  'REARRANGE_SENTENCE',
  'CHARACTER_MEANING',
]

function createOption(order: number): QuizOptionFormPayload {
  return {
    optionText: '',
    correctOption: false,
    displayOrder: order,
  }
}

function resetQuizForm() {
  editingQuizId.value = null
  quizForm.title = ''
  quizForm.description = ''
  quizForm.timeLimitMinutes = 10
}

function resetQuestionForm() {
  editingQuestionId.value = null
  questionForm.questionType = 'MULTIPLE_CHOICE'
  questionForm.questionText = ''
  questionForm.correctAnswerText = ''
  questionForm.points = 5
  questionForm.displayOrder = (selectedQuiz.value?.questions.length ?? 0) + 1
  questionForm.explanation = ''
  options.value = [createOption(1), createOption(2), createOption(3), createOption(4)]
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

async function loadQuizzes() {
  if (!selectedLessonId.value) {
    quizzes.value = []
    selectedQuizId.value = null
    return
  }

  loadingQuizzes.value = true

  try {
    quizzes.value = await teacherActivityService.getQuizzes(selectedLessonId.value)
    selectedQuizId.value = quizzes.value[0]?.id ?? null
  } catch (error) {
    quizzes.value = []
    selectedQuizId.value = null
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    loadingQuizzes.value = false
  }
}

function openCreateQuizDialog() {
  resetQuizForm()
  quizDialogVisible.value = true
}

function openEditQuizDialog(quiz: TeacherQuiz) {
  editingQuizId.value = quiz.id
  quizForm.title = quiz.title
  quizForm.description = quiz.description ?? ''
  quizForm.timeLimitMinutes = quiz.timeLimitMinutes ?? 10
  quizDialogVisible.value = true
}

async function saveQuiz() {
  if (!selectedLessonId.value || teacherLocked.value || !lessonEditable.value) {
    ElMessage.warning(t('learning.teacherActivityLocked'))
    return
  }

  if (!quizForm.title.trim()) {
    ElMessage.error(t('content.validation.titleRequired'))
    return
  }

  savingQuiz.value = true

  try {
    const payload: QuizFormPayload = {
      title: quizForm.title.trim(),
      description: quizForm.description?.trim() || undefined,
      timeLimitMinutes: Number(quizForm.timeLimitMinutes ?? 0),
    }

    if (editingQuizId.value) {
      await teacherActivityService.updateQuiz(editingQuizId.value, payload)
    } else {
      await teacherActivityService.createQuiz(selectedLessonId.value, payload)
    }

    ElMessage.success(t('content.messages.saved'))
    quizDialogVisible.value = false
    resetQuizForm()
    await loadQuizzes()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    savingQuiz.value = false
  }
}

async function deleteQuiz(quizId: number) {
  try {
    await teacherActivityService.deleteQuiz(quizId)
    ElMessage.success(t('learning.messages.quizDeleted'))
    await loadQuizzes()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

function openCreateQuestionDialog() {
  resetQuestionForm()
  questionDialogVisible.value = true
}

function openEditQuestionDialog(question: TeacherQuizQuestion) {
  editingQuestionId.value = question.id
  questionForm.questionType = question.questionType
  questionForm.questionText = question.questionText
  questionForm.correctAnswerText = question.correctAnswerText ?? ''
  questionForm.points = question.points
  questionForm.displayOrder = question.displayOrder
  questionForm.explanation = question.explanation ?? ''
  options.value = question.options.map((option) => ({
    optionText: option.optionText,
    correctOption: option.correctOption,
    displayOrder: option.displayOrder,
  }))
  questionDialogVisible.value = true
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

async function saveQuestion() {
  if (!selectedQuizId.value || teacherLocked.value || !lessonEditable.value) {
    ElMessage.warning(t('learning.teacherActivityLocked'))
    return
  }

  if (!questionForm.questionText.trim()) {
    ElMessage.error(t('learning.validation.quizQuestionRequired'))
    return
  }

  if (optionBasedTypes.includes(questionForm.questionType)) {
    if (options.value.some((option) => !option.optionText.trim()) || !options.value.some((option) => option.correctOption)) {
      ElMessage.error(t('learning.validation.quizOptionsRequired'))
      return
    }
  } else if (!questionForm.correctAnswerText?.trim()) {
    ElMessage.error(t('learning.validation.correctAnswerRequired'))
    return
  }

  savingQuestion.value = true

  try {
    const payload: QuizQuestionFormPayload = {
      questionType: questionForm.questionType,
      questionText: questionForm.questionText.trim(),
      correctAnswerText: optionBasedTypes.includes(questionForm.questionType)
        ? (options.value.find((option) => option.correctOption)?.optionText.trim() ?? '')
        : (questionForm.correctAnswerText?.trim() ?? ''),
      points: Number(questionForm.points ?? 1),
      displayOrder: Number(questionForm.displayOrder ?? 0),
      explanation: questionForm.explanation?.trim() || undefined,
      options: optionBasedTypes.includes(questionForm.questionType)
        ? options.value.map((option, index) => ({
            optionText: option.optionText.trim(),
            correctOption: option.correctOption,
            displayOrder: Number(option.displayOrder ?? index + 1),
          }))
        : [],
    }

    if (editingQuestionId.value) {
      await teacherActivityService.updateQuizQuestion(editingQuestionId.value, payload)
    } else {
      await teacherActivityService.addQuizQuestion(selectedQuizId.value, payload)
    }

    ElMessage.success(t('content.messages.saved'))
    questionDialogVisible.value = false
    resetQuestionForm()
    await loadQuizzes()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  } finally {
    savingQuestion.value = false
  }
}

async function deleteQuestion(questionId: number) {
  try {
    await teacherActivityService.deleteQuizQuestion(questionId)
    ElMessage.success(t('learning.messages.questionDeleted'))
    await loadQuizzes()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : t('content.messages.saveFailed'))
  }
}

watch(selectedLessonId, async () => {
  quizDialogVisible.value = false
  questionDialogVisible.value = false
  resetQuizForm()
  resetQuestionForm()
  await loadQuizzes()
})

watch(
  () => questionForm.questionType,
  (type) => {
    if (type === 'TRUE_FALSE') {
      options.value = [
        { optionText: 'True', correctOption: false, displayOrder: 1 },
        { optionText: 'False', correctOption: false, displayOrder: 2 },
      ]
    } else if (!optionBasedTypes.includes(type)) {
      options.value = []
    } else if (!options.value.length) {
      options.value = [createOption(1), createOption(2), createOption(3), createOption(4)]
    }
  },
)

onMounted(async () => {
  resetQuizForm()
  resetQuestionForm()
  await loadLessons()
  await loadQuizzes()
})
</script>

<template>
  <div class="dashboard-stack teacher-activity-page">
    <el-card class="dashboard-hero dashboard-hero--teacher" shadow="hover">
      <div class="dashboard-hero__split">
        <div>
          <p class="section-label">{{ t('learning.quizzesEyebrow') }}</p>
          <h1>{{ t('learning.quizzesTitle') }}</h1>
          <p>{{ t('learning.quizzesDescription') }}</p>
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
          <el-button type="primary" :disabled="teacherLocked || !lessonEditable || !selectedLessonId" @click="openCreateQuizDialog">
            {{ t('learning.addQuiz') }}
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
      <el-card v-for="quiz in quizzes" :key="quiz.id" class="dashboard-panel" shadow="hover">
        <div class="panel-header">
          <div>
            <p class="section-label">{{ t('learning.quizzes') }}</p>
            <h2>{{ quiz.title }}</h2>
          </div>
          <el-tag round>{{ quiz.questions.length }} {{ t('learning.quizQuestions') }}</el-tag>
        </div>

        <p>{{ quiz.description || t('learning.noQuizDescription') }}</p>
        <p>{{ t('learning.timeLimit') }}: {{ quiz.timeLimitMinutes ?? 0 }} {{ t('learning.minutes') }}</p>

        <div class="table-actions">
          <el-button plain size="small" :disabled="teacherLocked || !lessonEditable" @click="openEditQuizDialog(quiz)">
            {{ t('content.edit') }}
          </el-button>
          <el-button plain type="primary" size="small" :disabled="teacherLocked || !lessonEditable" @click="selectedQuizId = quiz.id; openCreateQuestionDialog()">
            {{ t('learning.addQuestion') }}
          </el-button>
          <el-button plain type="danger" size="small" :disabled="teacherLocked || !lessonEditable" @click="deleteQuiz(quiz.id)">
            {{ t('content.archive') }}
          </el-button>
        </div>

        <div class="stacked-summary-list quiz-question-preview">
          <article v-for="question in quiz.questions" :key="question.id" class="summary-row">
            <div>
              <strong>{{ question.questionText }}</strong>
              <p>{{ question.questionType }} · {{ question.points }} {{ t('learning.pointsLabel') }}</p>
            </div>
            <div class="table-actions">
              <el-button plain size="small" :disabled="teacherLocked || !lessonEditable" @click="selectedQuizId = quiz.id; openEditQuestionDialog(question)">
                {{ t('content.edit') }}
              </el-button>
              <el-button plain type="danger" size="small" :disabled="teacherLocked || !lessonEditable" @click="deleteQuestion(question.id)">
                {{ t('learning.deleteQuestion') }}
              </el-button>
            </div>
          </article>
        </div>
      </el-card>
    </section>

    <el-empty v-if="!loadingQuizzes && !quizzes.length" :description="t('learning.noQuizzes')" />

    <el-dialog
      v-model="quizDialogVisible"
      :title="editingQuizId ? t('learning.editQuiz') : t('learning.addQuiz')"
      width="720px"
    >
      <el-form label-position="top">
        <el-form-item :label="t('content.fields.title')">
          <el-input v-model="quizForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item :label="t('content.fields.description')">
          <el-input v-model="quizForm.description" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('learning.timeLimit')">
          <el-input-number v-model="quizForm.timeLimitMinutes" :min="0" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="table-actions">
          <el-button @click="quizDialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="savingQuiz" @click="saveQuiz">{{ t('content.save') }}</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="questionDialogVisible"
      :title="editingQuestionId ? t('learning.editQuestion') : t('learning.addQuestion')"
      width="860px"
    >
      <el-form label-position="top">
        <div class="content-form-grid">
          <el-form-item :label="t('learning.fields.questionType')">
            <el-select v-model="questionForm.questionType">
              <el-option v-for="type in quizQuestionTypes" :key="type" :label="type" :value="type" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('learning.fields.points')">
            <el-input-number v-model="questionForm.points" :min="1" />
          </el-form-item>
          <el-form-item :label="t('content.displayOrder')">
            <el-input-number v-model="questionForm.displayOrder" :min="0" />
          </el-form-item>
        </div>

        <el-form-item :label="t('learning.fields.questionText')">
          <el-input v-model="questionForm.questionText" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>

        <el-form-item v-if="!optionBasedTypes.includes(questionForm.questionType)" :label="t('learning.fields.correctAnswer')">
          <el-input v-model="questionForm.correctAnswerText" maxlength="500" />
        </el-form-item>

        <el-form-item :label="t('learning.fields.explanation')">
          <el-input v-model="questionForm.explanation" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>

        <div v-if="optionBasedTypes.includes(questionForm.questionType)" class="stacked-summary-list">
          <section v-for="(option, index) in options" :key="`${index}-${option.displayOrder}`" class="workspace-placeholder__panel">
            <div class="content-inline-form">
              <el-input v-model="option.optionText" :placeholder="`${t('learning.fields.optionText')} ${index + 1}`" />
              <el-checkbox v-model="option.correctOption">{{ t('learning.fields.correctOption') }}</el-checkbox>
              <el-input-number v-model="option.displayOrder" :min="0" />
              <el-button plain type="danger" :disabled="options.length <= 2 || questionForm.questionType === 'TRUE_FALSE'" @click="removeOption(index)">
                {{ t('learning.removeOption') }}
              </el-button>
            </div>
          </section>
          <el-button v-if="questionForm.questionType !== 'TRUE_FALSE'" plain @click="addOption">{{ t('learning.addOption') }}</el-button>
        </div>
      </el-form>

      <template #footer>
        <div class="table-actions">
          <el-button @click="questionDialogVisible = false">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="savingQuestion" @click="saveQuestion">{{ t('content.save') }}</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
