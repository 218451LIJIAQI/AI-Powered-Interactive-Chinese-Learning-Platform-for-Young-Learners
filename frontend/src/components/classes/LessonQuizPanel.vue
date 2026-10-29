<template>
  <div class="lesson-quiz-layout">
    <section class="panel lesson-editor-sheet" aria-labelledby="lesson-quiz-title">
      <div class="lesson-editor-section">
        <div class="lesson-quiz-chips" role="group" :aria-label="t('lessonEditor.quiz.inLesson')">
          <button
            v-for="(item, index) in editor.quizzes"
            :key="item.id"
            type="button"
            class="lesson-quiz-chip"
            :aria-pressed="item.id === quiz?.id"
            @click="selectedId = item.id"
          >
            {{ t('lessonEditor.quiz.chip', { number: index + 1, title: item.title }) }}
            <template v-if="item.stage"> · {{ t(STAGE_KEYS[item.stage]) }}</template>
          </button>
          <button
            v-if="editor.editable"
            type="button"
            class="btn btn-ghost btn-sm"
            :disabled="busy"
            data-testid="add-quiz"
            @click="addQuiz"
          >
            <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.quiz.add') }}
          </button>
        </div>

        <div class="lesson-editor-section-head">
          <div>
            <h2 id="lesson-quiz-title" class="card-title">
              {{
                quiz
                  ? t('lessonEditor.quiz.title', { number: quizIndex + 1, title: quiz.title })
                  : t('lessonEditor.tabs.quiz')
              }}
            </h2>
            <p class="type-caption">{{ t('lessonEditor.quiz.intro') }}</p>
          </div>
          <div v-if="quiz && editor.editable" class="lesson-editor-item-actions">
            <button type="button" class="btn btn-ghost btn-sm" :disabled="busy" @click="duplicateQuiz">
              <IconGlyph name="copy" :size="16" />{{ t('lessonEditor.quiz.duplicate') }}
            </button>
            <button type="button" class="btn btn-ghost btn-sm" :disabled="busy" @click="archiveOpen = true">
              <IconGlyph name="archive" :size="16" />{{ t('lessonEditor.quiz.archive') }}
            </button>
          </div>
        </div>
        <p v-if="!quiz" class="lesson-editor-empty">{{ t('lessonEditor.quiz.empty') }}</p>

        <form v-if="quiz" class="lesson-quiz-settings" @submit.prevent="saveSettings">
          <div class="field">
            <label class="field-label" :for="`${baseId}-title`">{{ t('lessonEditor.quiz.settingsTitle') }}</label>
            <input
              :id="`${baseId}-title`"
              v-model="settings.title"
              class="input"
              maxlength="200"
              required
              :disabled="!editor.editable"
            />
          </div>
          <div class="field">
            <label class="field-label" :for="`${baseId}-limit`">{{ t('lessonEditor.quiz.timeLimit') }}</label>
            <input
              :id="`${baseId}-limit`"
              v-model.number="settings.timeLimitMinutes"
              class="input"
              type="number"
              min="1"
              max="180"
              :placeholder="t('lessonEditor.quiz.noLimit')"
              :disabled="!editor.editable"
            />
          </div>
          <div class="field">
            <label class="field-label" :for="`${baseId}-stage`">{{ t('lessonEditor.quiz.stage') }}</label>
            <select :id="`${baseId}-stage`" v-model="settings.stage" class="select" :disabled="!editor.editable">
              <option :value="null">{{ t('lessonEditor.activities.noStage') }}</option>
              <option v-for="value in STAGES" :key="value" :value="value">{{ t(STAGE_KEYS[value]) }}</option>
            </select>
          </div>
          <div class="field">
            <span class="field-label">{{ t('lessonEditor.quiz.marks') }}</span>
            <span class="lesson-quiz-marks">{{ t('classes.marks', quiz.totalPoints) }}</span>
          </div>
          <button
            v-if="editor.editable && settingsChanged"
            type="submit"
            class="btn btn-secondary btn-sm"
            :disabled="busy"
          >
            {{ t('lessonEditor.quiz.saveSettings') }}
          </button>
        </form>
        <p v-if="quiz" class="type-caption lesson-editor-foot-note">
          <IconGlyph name="eyeOff" :size="14" /> {{ t('lessonEditor.quiz.hintsNote') }}
        </p>
      </div>

      <div v-if="quiz" class="lesson-editor-section">
        <div class="lesson-editor-section-head">
          <div>
            <h3 class="card-title">{{ t('lessonEditor.quiz.questions', { count: quiz.questions.length }) }}</h3>
            <p class="type-caption">
              {{ t('lessonEditor.quiz.questionsFacts', { marks: t('classes.marks', quiz.totalPoints) }) }}
            </p>
          </div>
          <button
            v-if="canEditQuestions"
            type="button"
            class="btn btn-secondary btn-sm"
            data-testid="add-question"
            @click="openQuestion(null)"
          >
            <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.quiz.addQuestion') }}
          </button>
        </div>
        <p v-if="quiz.questionsLocked" class="callout is-compact is-warning">
          <IconGlyph name="lock" :size="16" /><span>{{ t('lessonEditor.quiz.locked') }}</span>
        </p>
        <p v-if="!quiz.questions.length" class="lesson-editor-empty">{{ t('lessonEditor.quiz.noQuestions') }}</p>
        <ol v-else class="lesson-editor-list">
          <li v-for="(question, index) in quiz.questions" :key="question.id" class="lesson-editor-item lesson-question">
            <span class="lesson-editor-step" aria-hidden="true">{{ index + 1 }}</span>
            <div class="lesson-editor-item-copy">
              <span class="lesson-question-type">
                {{ t(questionTypeKey(question.type)) }} · {{ t('classes.marks', question.points) }}
              </span>
              <strong><CjkText :text="question.text" /></strong>
              <span v-if="question.options.length" class="lesson-question-options">
                <span
                  v-for="option in question.options"
                  :key="option.id"
                  class="tag"
                  :class="{ 'is-correct': option.correct }"
                >
                  <IconGlyph v-if="option.correct" name="check" :size="12" />
                  <CjkText :text="option.text" />
                </span>
              </span>
              <span v-else-if="question.correctAnswerText" class="type-caption">
                {{ t('lessonEditor.quiz.answer', { answer: question.correctAnswerText }) }}
              </span>
              <span v-if="question.explanation" class="type-caption">
                {{ t('lessonEditor.quiz.explanation', { text: question.explanation }) }}
              </span>
              <span v-if="question.hint" class="type-caption">{{
                t('lessonEditor.quiz.hint', { text: question.hint })
              }}</span>
            </div>
            <div v-if="canEditQuestions" class="lesson-editor-item-actions">
              <button
                type="button"
                class="btn btn-ghost btn-sm"
                :aria-label="t('lessonEditor.quiz.editLabel', { number: index + 1 })"
                @click="openQuestion(question)"
              >
                <IconGlyph name="edit" :size="16" />{{ t('lessonEditor.quiz.edit') }}
              </button>
              <button
                type="button"
                class="btn btn-ghost btn-icon btn-sm"
                :disabled="busy"
                :aria-label="t('lessonEditor.quiz.duplicateLabel', { number: index + 1 })"
                :title="t('lessonEditor.quiz.duplicateQuestion')"
                @click="duplicateQuestion(question.id)"
              >
                <IconGlyph name="copy" :size="16" />
              </button>
              <button
                type="button"
                class="btn btn-ghost btn-icon btn-sm"
                :aria-label="t('lessonEditor.quiz.deleteLabel', { number: index + 1 })"
                :title="t('lessonEditor.quiz.deleteQuestion')"
                @click="deleteTarget = question"
              >
                <IconGlyph name="trash" :size="16" />
              </button>
              <span class="teacher-move">
                <button
                  type="button"
                  class="btn btn-ghost btn-icon btn-sm"
                  :disabled="busy || index === 0"
                  :aria-label="t('lessonEditor.quiz.moveUp', { number: index + 1 })"
                  @click="moveQuestion(index, -1)"
                >
                  <IconGlyph name="arrowUp" :size="16" />
                </button>
                <button
                  type="button"
                  class="btn btn-ghost btn-icon btn-sm"
                  :disabled="busy || index === quiz.questions.length - 1"
                  :aria-label="t('lessonEditor.quiz.moveDown', { number: index + 1 })"
                  @click="moveQuestion(index, 1)"
                >
                  <IconGlyph name="arrowDown" :size="16" />
                </button>
              </span>
            </div>
          </li>
        </ol>
      </div>
    </section>

    <aside class="lesson-quiz-side">
      <section v-if="canEditQuestions" class="panel" aria-labelledby="lesson-quiz-ai-title">
        <h2 id="lesson-quiz-ai-title" class="sr-only">{{ t('lessonEditor.quiz.aiTitle') }}</h2>
        <TeacherAiDraftPanel :disabled="busy" @quiz-draft="addDraftQuestions" />
      </section>
      <section v-if="quiz" class="lesson-quiz-attempts-section" aria-labelledby="lesson-quiz-recent-title">
        <h2 id="lesson-quiz-recent-title" class="card-title">{{ t('lessonEditor.quiz.recent') }}</h2>
        <p v-if="!quiz.attemptCount" class="type-caption">{{ t('lessonEditor.quiz.noAttempts') }}</p>
        <template v-else>
          <p class="type-caption">
            {{
              t('lessonEditor.quiz.recentFacts', {
                attempts: t('lessonEditor.quiz.attempts', quiz.attemptCount),
                average: quiz.averagePercent ?? 0,
              })
            }}
          </p>
          <ul class="lesson-quiz-attempts">
            <li v-for="(attempt, index) in quiz.recentAttempts" :key="index">
              <span>{{ attempt.studentName }} · {{ formatShortDate(attempt.completedAt) }}</span>
              <strong>{{ attempt.percent }}%</strong>
            </li>
          </ul>
        </template>
      </section>
    </aside>

    <LessonQuestionDrawer
      :open="questionOpen"
      :question="editingQuestion"
      :busy="busy"
      :error="drawerError"
      @close="questionOpen = false"
      @save="saveQuestion"
    />
    <ConfirmDialog
      :open="deleteTarget !== null"
      :title="t('lessonEditor.question.deleteTitle')"
      :message="deleteTarget ? t('lessonEditor.question.deleteMessage', { number: deleteTarget.number }) : ''"
      :confirm-label="t('lessonEditor.quiz.deleteQuestion')"
      tone="danger"
      :busy="busy"
      @cancel="deleteTarget = null"
      @confirm="deleteQuestion"
    />
    <ConfirmDialog
      :open="archiveOpen"
      :title="t('lessonEditor.quiz.archiveTitle')"
      :message="quiz ? t('lessonEditor.quiz.archiveMessage', { title: quiz.title }) : ''"
      :confirm-label="t('lessonEditor.quiz.archive')"
      tone="danger"
      :busy="busy"
      @cancel="archiveOpen = false"
      @confirm="archiveQuiz"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'

import { lessonEditorApi } from '@/api/classes'
import TeacherAiDraftPanel from '@/components/ai/TeacherAiDraftPanel.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { AiQuizGeneratorResponse, LearningStage } from '@/types/api'
import type { EditorQuestion, LessonEditor, QuestionPayload } from '@/types/classes'
import { quizDraftToQuestionDrafts } from '@/utils/aiDraft'
import { resolveApiError } from '@/utils/apiError'
import { STAGE_KEYS, questionTypeKey } from '@/utils/classes'
import { formatShortDate } from '@/utils/format'
import { STAGES } from '@/utils/learnLabels'

import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import LessonQuestionDrawer from './LessonQuestionDrawer.vue'

/** The Quiz tab: the lesson's quizzes, their settings and questions, AI-assisted drafts and recent attempts. */
const props = defineProps<{ editor: LessonEditor }>()
const emit = defineEmits<{ updated: [editor: LessonEditor]; notice: [message: string]; error: [message: string] }>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages, classes: classesMessages })
const baseId = useId()
const AI_QUESTION_POINTS = 2

const selectedId = ref<number | null>(null)
const quiz = computed(
  () => props.editor.quizzes.find((item) => item.id === selectedId.value) ?? props.editor.quizzes[0] ?? null,
)
const quizIndex = computed(() => props.editor.quizzes.findIndex((item) => item.id === quiz.value?.id))
const canEditQuestions = computed(() => props.editor.editable && Boolean(quiz.value) && !quiz.value!.questionsLocked)

const settings = ref<{ title: string; timeLimitMinutes: number | null; stage: LearningStage | null }>({
  title: '',
  timeLimitMinutes: null,
  stage: null,
})
watch(
  quiz,
  (current) => {
    settings.value = {
      title: current?.title ?? '',
      timeLimitMinutes: current?.timeLimitMinutes ?? null,
      stage: current?.stage ?? null,
    }
  },
  { immediate: true },
)
const settingsChanged = computed(
  () =>
    Boolean(quiz.value) &&
    (settings.value.title.trim() !== quiz.value!.title ||
      (settings.value.timeLimitMinutes || null) !== quiz.value!.timeLimitMinutes ||
      settings.value.stage !== quiz.value!.stage),
)

const busy = ref(false)
const questionOpen = ref(false)
const editingQuestion = ref<EditorQuestion | null>(null)
const drawerError = ref<string | null>(null)
const deleteTarget = ref<EditorQuestion | null>(null)
const archiveOpen = ref(false)

async function act(action: () => Promise<LessonEditor>, notice: string): Promise<LessonEditor | null> {
  busy.value = true
  try {
    const next = await action()
    emit('updated', next)
    emit('notice', notice)
    return next
  } catch (reason) {
    emit('error', resolveApiError(reason, t('lessonEditor.quiz.error')))
    return null
  } finally {
    busy.value = false
  }
}

async function addQuiz(): Promise<void> {
  const before = new Set(props.editor.quizzes.map((item) => item.id))
  const next = await act(
    () =>
      lessonEditorApi.addQuiz(props.editor.lessonId, {
        title: t('lessonEditor.quiz.newQuizTitle'),
        timeLimitMinutes: 10,
        learningStage: 'GUIDED',
      }),
    t('lessonEditor.quiz.added'),
  )
  if (next) selectedId.value = next.quizzes.find((item) => !before.has(item.id))?.id ?? selectedId.value
}

function saveSettings(): void {
  if (!quiz.value) return
  void act(
    () =>
      lessonEditorApi.updateQuiz(props.editor.lessonId, quiz.value!.id, {
        title: settings.value.title.trim(),
        description: quiz.value!.description,
        timeLimitMinutes: settings.value.timeLimitMinutes || null,
        learningStage: settings.value.stage,
      }),
    t('lessonEditor.quiz.saved'),
  )
}

function duplicateQuiz(): void {
  if (!quiz.value) return
  void act(
    () => lessonEditorApi.duplicateQuiz(props.editor.lessonId, quiz.value!.id),
    t('lessonEditor.quiz.duplicated'),
  )
}

async function archiveQuiz(): Promise<void> {
  if (!quiz.value) return
  const next = await act(
    () => lessonEditorApi.archiveQuiz(props.editor.lessonId, quiz.value!.id),
    t('lessonEditor.quiz.archived'),
  )
  archiveOpen.value = false
  if (next) selectedId.value = null
}

function openQuestion(question: EditorQuestion | null): void {
  editingQuestion.value = question
  drawerError.value = null
  questionOpen.value = true
}

async function saveQuestion(payload: QuestionPayload): Promise<void> {
  if (!quiz.value) return
  busy.value = true
  drawerError.value = null
  try {
    const next = editingQuestion.value
      ? await lessonEditorApi.updateQuestion(props.editor.lessonId, editingQuestion.value.id, payload)
      : await lessonEditorApi.addQuestion(props.editor.lessonId, quiz.value.id, payload)
    emit('updated', next)
    emit('notice', t('lessonEditor.question.saved'))
    questionOpen.value = false
  } catch (reason) {
    drawerError.value = resolveApiError(reason, t('lessonEditor.question.error'))
  } finally {
    busy.value = false
  }
}

function duplicateQuestion(questionId: number): void {
  void act(
    () => lessonEditorApi.duplicateQuestion(props.editor.lessonId, questionId),
    t('lessonEditor.question.duplicated'),
  )
}

async function deleteQuestion(): Promise<void> {
  const target = deleteTarget.value
  if (!target) return
  await act(() => lessonEditorApi.deleteQuestion(props.editor.lessonId, target.id), t('lessonEditor.question.deleted'))
  deleteTarget.value = null
}

function moveQuestion(index: number, step: -1 | 1): void {
  if (!quiz.value) return
  const ids = quiz.value.questions.map((question) => question.id)
  const [moved] = ids.splice(index, 1)
  ids.splice(index + step, 0, moved!)
  void act(() => lessonEditorApi.reorderQuestions(props.editor.lessonId, quiz.value!.id, ids), t('lessonEditor.saved'))
}

/** AI questions are added to the selected quiz one by one; the teacher checks each before submitting. */
async function addDraftQuestions(draft: AiQuizGeneratorResponse): Promise<void> {
  if (!quiz.value) return
  const quizId = quiz.value.id
  busy.value = true
  try {
    let latest: LessonEditor | null = null
    for (const question of quizDraftToQuestionDrafts(draft)) {
      latest = await lessonEditorApi.addQuestion(props.editor.lessonId, quizId, {
        questionType: question.questionType,
        questionText: question.questionText,
        points: AI_QUESTION_POINTS,
        correctAnswerText: question.correctAnswerText || null,
        explanation: question.explanation || null,
        options: question.options,
      })
    }
    if (latest) {
      emit('updated', latest)
      emit('notice', t('lessonEditor.quiz.aiAdded'))
    }
  } catch (reason) {
    emit('error', resolveApiError(reason, t('lessonEditor.question.error')))
  } finally {
    busy.value = false
  }
}
</script>
