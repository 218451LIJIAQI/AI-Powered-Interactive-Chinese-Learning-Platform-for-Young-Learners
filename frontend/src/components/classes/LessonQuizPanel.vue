<template>
  <div class="lesson-with-rail" :class="{ 'is-single': !canEdit }">
    <section class="panel teacher-card" aria-labelledby="lesson-quiz-title">
      <div class="section-header">
        <div class="title-with-count">
          <h2 id="lesson-quiz-title" class="card-title">{{ t('lessonEditor.quiz.questions') }}</h2>
          <span v-if="questions.length" class="count-badge">{{ questions.length }}</span>
        </div>
        <button
          v-if="canEdit"
          type="button"
          class="btn btn-secondary btn-sm"
          data-testid="add-question"
          @click="openQuestion(null)"
        >
          <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.quiz.addQuestion') }}
        </button>
      </div>
      <p v-if="quiz?.questionsLocked" class="callout">
        <IconGlyph name="lock" :size="18" /><span>{{ t('lessonEditor.quiz.locked') }}</span>
      </p>
      <p v-if="!questions.length" class="type-caption">{{ t('lessonEditor.quiz.noQuestions') }}</p>
      <ol v-else class="row-list lesson-questions">
        <li v-for="(question, index) in questions" :key="question.id">
          <span class="teacher-tile is-sm" aria-hidden="true">{{ index + 1 }}</span>
          <div class="lesson-question-copy">
            <span class="tag is-sm lesson-question-type">{{ t(questionTypeKey(question.type)) }}</span>
            <strong class="row-list-title"><CjkText :text="question.text" /></strong>
            <span v-if="question.audioUrl" class="row-list-meta">
              {{ t('lessonEditor.quiz.audio', { file: fileName(question.audioUrl) }) }}
            </span>
            <span v-if="question.options.length" class="lesson-question-options">
              <span
                v-for="option in question.options"
                :key="option.id"
                class="tag"
                :class="option.correct ? 'is-strong' : 'is-outline'"
              >
                <IconGlyph v-if="option.correct" name="check" :size="14" />
                <CjkText :text="option.text" />
              </span>
            </span>
            <span v-else-if="question.correctAnswerText" class="row-list-meta">
              <CjkText :text="t('lessonEditor.quiz.answer', { answer: question.correctAnswerText })" />
            </span>
            <span v-if="question.explanation" class="row-list-meta">
              <CjkText :text="t('lessonEditor.quiz.explanation', { text: question.explanation })" />
            </span>
            <span v-if="question.hint" class="row-list-meta">
              <CjkText :text="t('lessonEditor.quiz.hint', { text: question.hint })" />
            </span>
          </div>
          <div v-if="canEdit" class="row-list-actions">
            <button
              type="button"
              class="btn btn-secondary btn-sm"
              :aria-label="t('lessonEditor.quiz.editLabel', { number: index + 1 })"
              @click="openQuestion(question)"
            >
              <IconGlyph name="edit" :size="16" />{{ t('lessonEditor.quiz.edit') }}
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
          </div>
        </li>
      </ol>
    </section>

    <section v-if="canEdit" class="panel teacher-card" aria-labelledby="lesson-quiz-ai-title">
      <TeacherAiDraftPanel heading-id="lesson-quiz-ai-title" :disabled="busy" @quiz-draft="addDraftQuestions" />
    </section>

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
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { lessonEditorApi } from '@/api/classes'
import TeacherAiDraftPanel from '@/components/ai/TeacherAiDraftPanel.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { AiQuizGeneratorResponse } from '@/types/api'
import type { EditorQuestion, LessonEditor, QuestionPayload } from '@/types/classes'
import { quizDraftToQuestionDrafts } from '@/utils/aiDraft'
import { resolveApiError } from '@/utils/apiError'
import { questionTypeKey } from '@/utils/classes'
import { lessonQuiz } from '@/utils/teacherLesson'

import LessonQuestionDrawer from './LessonQuestionDrawer.vue'

/** The Quiz tab: the lesson's one quiz as a list of questions, with AI-assisted drafts beside it. */
const props = defineProps<{ editor: LessonEditor }>()
const emit = defineEmits<{ updated: [editor: LessonEditor]; notice: [message: string]; error: [message: string] }>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages, classes: classesMessages })
const AI_QUESTION_POINTS = 2

const quiz = computed(() => lessonQuiz(props.editor))
const questions = computed(() => quiz.value?.questions ?? [])
const canEdit = computed(() => props.editor.editable && !quiz.value?.questionsLocked)

const busy = ref(false)
const questionOpen = ref(false)
const editingQuestion = ref<EditorQuestion | null>(null)
const drawerError = ref<string | null>(null)
const deleteTarget = ref<EditorQuestion | null>(null)

/** "meitian.mp3" for a link to an audio file. */
function fileName(url: string): string {
  try {
    return new URL(url).pathname.split('/').pop() || url
  } catch {
    return url
  }
}

/**
 * The id of the lesson's quiz; a new lesson gets it with its first question. It carries the lesson's title, no time
 * limit, and the stage that lets students open hints.
 */
async function ensureQuiz(): Promise<number> {
  if (quiz.value) return quiz.value.id
  const next = await lessonEditorApi.addQuiz(props.editor.lessonId, {
    title: props.editor.title,
    timeLimitMinutes: null,
    learningStage: 'GUIDED',
  })
  emit('updated', next)
  return next.quizzes[0]!.id
}

function openQuestion(question: EditorQuestion | null): void {
  editingQuestion.value = question
  drawerError.value = null
  questionOpen.value = true
}

async function saveQuestion(payload: QuestionPayload): Promise<void> {
  busy.value = true
  drawerError.value = null
  try {
    const next = editingQuestion.value
      ? await lessonEditorApi.updateQuestion(props.editor.lessonId, editingQuestion.value.id, payload)
      : await lessonEditorApi.addQuestion(props.editor.lessonId, await ensureQuiz(), payload)
    emit('updated', next)
    emit('notice', t('lessonEditor.question.saved'))
    questionOpen.value = false
  } catch (reason) {
    drawerError.value = resolveApiError(reason, t('lessonEditor.question.error'))
  } finally {
    busy.value = false
  }
}

async function deleteQuestion(): Promise<void> {
  const target = deleteTarget.value
  if (!target) return
  busy.value = true
  try {
    emit('updated', await lessonEditorApi.deleteQuestion(props.editor.lessonId, target.id))
    emit('notice', t('lessonEditor.question.deleted'))
  } catch (reason) {
    emit('error', resolveApiError(reason, t('lessonEditor.quiz.error')))
  } finally {
    busy.value = false
    deleteTarget.value = null
  }
}

/** AI questions are added to the quiz one by one; the teacher checks each before submitting. */
async function addDraftQuestions(draft: AiQuizGeneratorResponse): Promise<void> {
  busy.value = true
  try {
    const quizId = await ensureQuiz()
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
