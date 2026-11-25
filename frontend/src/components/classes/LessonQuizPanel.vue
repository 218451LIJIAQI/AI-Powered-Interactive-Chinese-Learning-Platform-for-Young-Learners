<template>
  <div class="lesson-with-rail">
    <section ref="quizPanel" class="panel authoring-card" aria-labelledby="lesson-quiz-title">
      <template v-if="questions.length">
        <div class="section-header">
          <h2 id="lesson-quiz-title" class="card-title">{{ t('lessonEditor.quiz.questions') }}</h2>
          <button
            v-if="canEdit"
            type="button"
            class="btn btn-secondary btn-sm"
            :disabled="full || working"
            :aria-describedby="full ? countHintId : undefined"
            data-testid="add-question"
            @click="openQuestion(null)"
          >
            <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.quiz.addQuestion') }}
          </button>
        </div>
        <p v-if="quiz?.questionsLocked" class="callout">
          <IconGlyph name="lock" :size="18" /><span>{{ t('lessonEditor.quiz.locked') }}</span>
        </p>
        <ol ref="questionList" class="row-list lesson-questions">
          <li v-for="(question, index) in questions" :key="question.id" data-testid="quiz-question">
            <span class="authoring-tile is-sm" aria-hidden="true">{{ index + 1 }}</span>
            <div class="lesson-question-copy">
              <span class="lesson-question-tags">
                <span class="tag is-sm">{{ t(questionTypeKey(question.type)) }}</span>
                <span v-if="question.aiDraft" class="tag is-sm" data-testid="ai-draft-tag">
                  {{ t('lessonEditor.quiz.aiDraftTag') }}
                </span>
              </span>
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
                  <span v-if="option.correct" class="sr-only"> ({{ t('lessonEditor.question.correctAnswer') }})</span>
                </span>
              </span>
              <span v-else-if="question.correctAnswerText" class="row-list-meta">
                <CjkText :text="t('lessonEditor.quiz.answer', { answer: question.correctAnswerText })" />
              </span>
              <span v-if="question.explanation" class="row-list-meta">
                <CjkText :text="t('lessonEditor.quiz.explanation', { text: question.explanation })" />
              </span>
            </div>
            <div v-if="canEdit" class="row-list-actions">
              <button
                type="button"
                class="btn btn-ghost btn-icon btn-sm"
                :disabled="working"
                :aria-label="t('lessonEditor.quiz.deleteLabel', { number: index + 1 })"
                :title="t('lessonEditor.quiz.deleteQuestion')"
                @click="deleteQuestion(question)"
              >
                <IconGlyph name="trash" :size="16" />
              </button>
              <!-- An unchecked AI draft: Keep checks it as it is; saving an edit checks it too (A3.28 → A3.37). -->
              <button
                v-if="question.aiDraft"
                type="button"
                class="btn btn-secondary btn-sm"
                :disabled="working"
                :aria-label="t('lessonEditor.quiz.keepLabel', { number: index + 1 })"
                data-testid="keep-question"
                @click="keepQuestion(question, index + 1)"
              >
                <IconGlyph name="check" :size="16" />{{ t('lessonEditor.quiz.keep') }}
              </button>
              <button
                type="button"
                class="btn btn-secondary btn-sm"
                :disabled="working"
                :data-question-edit="index + 1"
                :aria-label="t('lessonEditor.quiz.editLabel', { number: index + 1 })"
                @click="openQuestion(question)"
              >
                <IconGlyph name="edit" :size="16" />{{ t('common.edit') }}
              </button>
            </div>
          </li>
        </ol>
      </template>
      <template v-else>
        <h2 id="lesson-quiz-title" class="sr-only">{{ t('lessonEditor.quiz.questions') }}</h2>
        <!-- Nothing yet: adding the first question is the main task here, so it is the primary button. -->
        <StatePanel
          tone="empty"
          icon="quiz"
          :title="t('lessonEditor.quiz.emptyTitle')"
          :message="t('lessonEditor.quiz.emptyMessage')"
        >
          <template v-if="canEdit" #actions>
            <button
              type="button"
              class="btn btn-primary"
              :disabled="working"
              data-testid="add-first-question"
              @click="openQuestion(null)"
            >
              <IconGlyph name="plus" :size="16" />{{ t('lessonEditor.quiz.addQuestion') }}
            </button>
          </template>
        </StatePanel>
      </template>
    </section>

    <div class="lesson-rail">
      <AdminQuizDraftPanel
        v-if="canEdit"
        :lesson-id="editor.lessonId"
        :count-hint-id="countHintId"
        :remaining="remaining"
        :disabled="busy"
        @quiz-draft="addDraftQuestions"
        @generating="generating = $event"
      />
      <section class="panel authoring-card" aria-labelledby="lesson-quiz-hints-title">
        <div class="authoring-card-head">
          <h2 id="lesson-quiz-hints-title" class="card-title">{{ t('lessonEditor.quiz.hintsTitle') }}</h2>
          <p class="type-caption">{{ t('lessonEditor.quiz.hintsSubtitle') }}</p>
        </div>
        <!-- The quiz's one setting. It is not a question, so it stays editable when the questions are locked. -->
        <div class="lesson-quiz-setting">
          <div class="row-list-copy">
            <label class="row-list-title" :for="`${baseId}-hints`">{{ t('lessonEditor.quiz.hints') }}</label>
            <span :id="`${baseId}-hints-note`" class="row-list-meta">{{ t('lessonEditor.quiz.hintsNote') }}</span>
          </div>
          <span class="lesson-quiz-switch">
            <span aria-hidden="true">{{
              hintsChecked ? t('lessonEditor.quiz.hintsOn') : t('lessonEditor.quiz.hintsOff')
            }}</span>
            <input
              :id="`${baseId}-hints`"
              ref="hintsSwitch"
              class="switch"
              type="checkbox"
              role="switch"
              :checked="hintsChecked"
              :disabled="!editor.editable || working"
              :aria-describedby="`${baseId}-hints-note`"
              data-testid="quiz-hints"
              @change="setHints($event.target as HTMLInputElement)"
            />
          </span>
        </div>
        <p class="type-caption">
          {{ hintsChecked ? t('lessonEditor.quiz.hintsOnNote') : t('lessonEditor.quiz.hintsOffNote') }}
        </p>
      </section>
    </div>

    <LessonQuestionDrawer
      v-if="editingQuestion !== undefined"
      :question="editingQuestion"
      :busy="busy"
      :error="drawerError"
      @close="editingQuestion = undefined"
      @save="saveQuestion"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, useId } from 'vue'

import { lessonEditorApi } from '@/api/adminClasses'
import AdminQuizDraftPanel from '@/components/classes/AdminQuizDraftPanel.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { lessonEditorMessages } from '@/plugins/lessonEditorMessages'
import type { AiQuizGeneratorResponse } from '@/types/api'
import type { EditorQuestion, LessonEditor, QuestionPayload } from '@/types/adminClasses'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { questionTypeKey } from '@/utils/classes'
import { QUIZ_SIZE, lessonQuiz, quizDraftToQuestionPayloads } from '@/utils/lessonEditor'

import LessonQuestionDrawer from './LessonQuestionDrawer.vue'
import { useLessonPanelGuard } from './useLessonPanelGuard'

/**
 * The Quiz tab: the lesson's quiz of exactly 5 questions, with AI-assisted drafts and the "Allow SparkBot hints"
 * switch beside it. At 5 questions, Add question and AI drafts stop until one is deleted.
 */
const props = defineProps<{ editor: LessonEditor }>()
const emit = defineEmits<{
  updated: [editor: LessonEditor]
  notice: [message: string]
  error: [message: string]
  /** A question was deleted: the editor offers Undo, which puts it back in its place. */
  deleted: [question: EditorQuestion, quizId: number]
  /** A write or an AI draft starts (true) or ends (false); the editor's tabs and Publish wait for it. */
  saving: [saving: boolean]
}>()

const { t } = useLocalMessages({ lessonEditor: lessonEditorMessages, classes: classesMessages })
const baseId = useId()
const countHintId = `${baseId}-count-hint`

const quiz = computed(() => lessonQuiz(props.editor))
const questions = computed(() => quiz.value?.questions ?? [])
const canEdit = computed(() => props.editor.editable && !quiz.value?.questionsLocked)
const remaining = computed(() => Math.max(0, QUIZ_SIZE - questions.value.length))
const full = computed(() => remaining.value === 0)
/** The setting being saved; null while no save of it is out. */
const savingHints = ref<boolean | null>(null)
/** What the switch shows: the saved setting, or the new one while it is being saved. */
const hintsChecked = computed(() => savingHints.value ?? quiz.value?.hintsAllowed ?? false)

const busy = ref(false)
const generating = ref(false)
/** A write or an AI draft is out: no other change starts meanwhile. */
const working = computed(() => busy.value || generating.value)
/** Undefined closes the drawer; null opens it for a new question. */
const editingQuestion = ref<EditorQuestion | null>()
const drawerError = ref<string | null>(null)
const quizPanel = ref<HTMLElement | null>(null)
const hintsSwitch = ref<HTMLInputElement | null>(null)
const questionList = ref<HTMLOListElement | null>(null)

const guard = useLessonPanelGuard({
  lessonId: () => props.editor.lessonId,
  saving: working,
  onSaving: (saving) => emit('saving', saving),
  onReset: () => {
    editingQuestion.value = undefined
    drawerError.value = null
    savingHints.value = null
    busy.value = false
    generating.value = false
  },
})

async function runBusyAction(
  work: (isCurrent: () => boolean) => Promise<void>,
  options: {
    errorMessage: string
    onError?: (message: string) => void
    onFinally?: () => void
    afterSuccess?: () => void
    afterSettled?: () => void
  },
): Promise<void> {
  const version = guard.capture()
  busy.value = true
  const succeeded = await guard.run(work, {
    onError: (reason) => {
      const message = resolveApiError(reason, options.errorMessage)
      if (options.onError) options.onError(message)
      else emit('error', message)
    },
    onFinally: () => {
      options.onFinally?.()
      busy.value = false
    },
  })
  const after = succeeded ? (options.afterSuccess ?? options.afterSettled) : options.afterSettled
  if (!after || !guard.isCurrent(version)) return
  await nextTick()
  if (guard.isCurrent(version)) after()
}

/** "meitian.mp3" for a link to an audio file; the server only stores full web addresses. */
function fileName(url: string): string {
  try {
    return new URL(url).pathname.split('/').pop() || url
  } catch {
    return url
  }
}

/**
 * Gives the lesson its quiz. The server refuses a second one (409) when the quiz was made elsewhere, in another tab:
 * this editor is then out of date, so it is read again and the quiz shows, and the next action uses it. The refusal
 * still reaches the caller, which says so.
 */
async function addQuiz(current: LessonEditor, hints: boolean, isCurrent: () => boolean): Promise<LessonEditor> {
  try {
    return await lessonEditorApi.addQuiz(current.lessonId, { title: current.title, hintsAllowed: hints })
  } catch (reason) {
    if (apiErrorStatus(reason) === 409 && isCurrent()) {
      const fresh = await lessonEditorApi.get(current.lessonId).catch(() => null)
      if (fresh && isCurrent()) emit('updated', fresh)
    }
    throw reason
  }
}

/** The id of the lesson's quiz; a new lesson gets it, hints off, with its first question. */
async function ensureQuiz(current: LessonEditor, isCurrent: () => boolean): Promise<number | null> {
  const existing = lessonQuiz(current)
  if (existing) return existing.id
  const next = await addQuiz(current, false, isCurrent)
  if (!isCurrent()) return null
  emit('updated', next)
  return next.quizzes[0]!.id
}

/**
 * Turns SparkBot hints on or off for this lesson's quiz. Like every edit it is saved on the working copy, so a
 * published quiz keeps its setting for learners until the lesson is published again. A lesson without a quiz yet gets
 * its quiz here, with the setting chosen.
 */
async function setHints(input: HTMLInputElement): Promise<void> {
  const allowed = input.checked
  if (working.value || !props.editor.editable) {
    input.checked = hintsChecked.value
    return
  }
  const hadFocus = document.activeElement === input
  const current = props.editor
  const existing = lessonQuiz(current)
  savingHints.value = allowed
  await runBusyAction(
    async (isCurrent) => {
      const next = existing
        ? await lessonEditorApi.updateQuiz(current.lessonId, existing.id, { hintsAllowed: allowed })
        : await addQuiz(current, allowed, isCurrent)
      if (!isCurrent()) return
      emit('updated', next)
      emit('notice', t('lessonEditor.quiz.hintsSaved'))
    },
    {
      errorMessage: t('lessonEditor.quiz.error'),
      // From here the switch shows the quiz's setting: the saved one, or the one it still has when the save failed.
      onFinally: () => {
        savingHints.value = null
      },
      afterSettled: hadFocus ? () => hintsSwitch.value?.focus() : undefined,
    },
  )
}

function openQuestion(question: EditorQuestion | null): void {
  if (working.value || !canEdit.value || (!question && full.value)) return
  editingQuestion.value = question
  drawerError.value = null
}

async function saveQuestion(payload: QuestionPayload): Promise<void> {
  const question = editingQuestion.value
  if (question === undefined || busy.value || !canEdit.value) return
  const current = props.editor
  const targetNumber = question ? question.number : questions.value.length + 1
  drawerError.value = null
  await runBusyAction(
    async (isCurrent) => {
      let next: LessonEditor
      if (question) {
        // Saved by the admin: the server checks an AI draft too.
        next = await lessonEditorApi.updateQuestion(current.lessonId, question.id, payload)
      } else {
        const quizId = await ensureQuiz(current, isCurrent)
        if (quizId === null || !isCurrent()) return
        next = await lessonEditorApi.addQuestion(current.lessonId, quizId, payload)
      }
      if (!isCurrent()) return
      emit('updated', next)
      // X1.18: one confirmation for a new question and an edited one.
      emit('notice', t('lessonEditor.question.saved'))
      editingQuestion.value = undefined
    },
    {
      errorMessage: t('lessonEditor.question.error'),
      onError: (message) => {
        drawerError.value = message
      },
      // Re-focuses the saved question's Edit button even if the 1st edit cloned ids or adding the 5th disabled Add.
      afterSuccess: () => {
        const focusNumber = Math.min(targetNumber, questions.value.length)
        if (focusNumber > 0) {
          questionList.value?.querySelector<HTMLButtonElement>(`[data-question-edit="${focusNumber}"]`)?.focus()
        }
      },
    },
  )
}

/** Deleting runs at once; the editor's toast offers Undo, which puts the question back in its place. */
async function deleteQuestion(question: EditorQuestion): Promise<void> {
  const quizId = quiz.value?.id
  if (working.value || !canEdit.value || quizId === undefined) return
  const deletedNumber = question.number
  await runBusyAction(
    async (isCurrent) => {
      const next = await lessonEditorApi.deleteQuestion(props.editor.lessonId, question.id)
      if (!isCurrent()) return
      emit('updated', next)
      emit('deleted', question, quizId)
    },
    {
      errorMessage: t('lessonEditor.quiz.error'),
      // After Delete (success or failure), restore focus to the surviving question's Edit button (or Add question when empty).
      afterSettled: () => {
        const remainingCount = questions.value.length
        if (remainingCount > 0) {
          const focusNumber = Math.min(deletedNumber, remainingCount)
          questionList.value?.querySelector<HTMLButtonElement>(`[data-question-edit="${focusNumber}"]`)?.focus()
        } else {
          quizPanel.value?.querySelector<HTMLButtonElement>('[data-testid="add-first-question"]')?.focus()
        }
      },
    },
  )
}

/**
 * Keep: the admin checked an AI draft and keeps it as it is, so it is no longer an AI draft and stops holding back
 * publishing. A published lesson's first change gives the quiz new ids, so the row is found again by its number.
 */
async function keepQuestion(question: EditorQuestion, number: number): Promise<void> {
  if (working.value || !canEdit.value) return
  await runBusyAction(
    async (isCurrent) => {
      const next = await lessonEditorApi.keepQuestion(props.editor.lessonId, question.id)
      if (!isCurrent()) return
      emit('updated', next)
    },
    {
      errorMessage: t('lessonEditor.quiz.error'),
      // Once the buttons are enabled again (success or failure), restore focus to this row's Edit button.
      afterSettled: () => {
        questionList.value?.querySelector<HTMLButtonElement>(`[data-question-edit="${number}"]`)?.focus()
      },
    },
  )
}

/**
 * AI questions are added to the quiz one by one, never past its 5, each marked as an AI draft until the admin keeps
 * or edits it. Each shows as it is saved, so when the server refuses one (an answer key that matches no option), those
 * before it are on the page.
 */
async function addDraftQuestions(draft: AiQuizGeneratorResponse): Promise<void> {
  if (busy.value || !canEdit.value) return
  const current = props.editor
  const toAdd = quizDraftToQuestionPayloads(draft).slice(0, remaining.value)
  if (!toAdd.length) return
  await runBusyAction(
    async (isCurrent) => {
      const quizId = await ensureQuiz(current, isCurrent)
      if (quizId === null || !isCurrent()) return
      for (const payload of toAdd) {
        const next = await lessonEditorApi.addQuestion(current.lessonId, quizId, payload)
        if (!isCurrent()) return
        emit('updated', next)
      }
      emit('notice', t('lessonEditor.quiz.aiAdded'))
    },
    { errorMessage: t('lessonEditor.question.error') },
  )
}
</script>
