import { computed, ref, type ComputedRef } from 'vue'

import { studentClassesApi } from '@/api/classes'
import type { QuizSession } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { isTypedQuestion } from '@/utils/classes'

const TYPING_SAVE_DELAY_MS = 700

interface UseQuizAttemptOptions {
  session: QuizSession
  /** True while navigation, submission, or account switch locks answer edits. */
  locked: ComputedRef<boolean>
  /** True while discarding the attempt on leave; prevents in-flight saves from racing with discard. */
  leaving: ComputedRef<boolean>
  ownsAttempt: () => boolean
  onPoke: () => void
  onClearSubmitError: () => void
  onActionError: (message: string | null) => void
  saveErrorMessage: () => string
}

/**
 * Owns answer state and server persistence for an active lesson quiz attempt: optimistic choice selection with
 * rollback on failure, per-question debounced and serialized typed-answer saving, and full flush before submit.
 */
export function useQuizAttempt(options: UseQuizAttemptOptions) {
  const chosen = ref<Record<number, number>>({})
  const typed = ref<Record<number, string>>({})
  const savingOption = ref(false)
  const typingTimers = new Map<number, ReturnType<typeof setTimeout>>()
  const pendingTyped = new Map<number, { text: string; promise: Promise<boolean> }>()

  const { session } = options
  const typedQuestionIds = new Set(
    session.questions.filter((item) => isTypedQuestion(item.type)).map((item) => item.id),
  )
  for (const answer of session.answers) {
    if (answer.optionId !== null) chosen.value[answer.questionId] = answer.optionId
    if (answer.answerText !== null && typedQuestionIds.has(answer.questionId))
      typed.value[answer.questionId] = answer.answerText
  }
  const savedTyped = ref<Record<number, string>>({})
  for (const id of typedQuestionIds) savedTyped.value[id] = typed.value[id] ?? ''

  /** A typed answer counts while its box holds text; Submit flushes any unsaved draft before grading. */
  function isAnswered(questionId: number): boolean {
    return chosen.value[questionId] !== undefined || Boolean(typed.value[questionId]?.trim())
  }

  const answeredCount = computed(() => session.questions.filter((item) => isAnswered(item.id)).length)
  const remaining = computed(() => session.questions.length - answeredCount.value)

  function stopTimers(): void {
    typingTimers.forEach((timer) => clearTimeout(timer))
    typingTimers.clear()
  }

  const canPersist = (): boolean => options.ownsAttempt() && !options.leaving.value

  async function choose(questionId: number, optionId: number): Promise<void> {
    if (savingOption.value || options.locked.value || !canPersist()) return
    options.onActionError(null)
    options.onClearSubmitError()
    // No other answer can be chosen while this one saves, so a failure puts back the choices from before it.
    const before = chosen.value
    chosen.value = { ...before, [questionId]: optionId }
    savingOption.value = true
    try {
      await studentClassesApi.saveAnswer(session.attemptId, { questionId, optionId })
    } catch (reason) {
      if (!canPersist()) return
      chosen.value = before
      options.onActionError(resolveApiError(reason, options.saveErrorMessage()))
    } finally {
      if (options.ownsAttempt()) savingOption.value = false
    }
  }

  function typeAnswer(questionId: number, value: string): void {
    if (options.locked.value || !canPersist()) return
    options.onClearSubmitError()
    typed.value = { ...typed.value, [questionId]: value }
    options.onPoke()
    clearTimeout(typingTimers.get(questionId))
    typingTimers.set(
      questionId,
      setTimeout(() => void saveTyped(questionId), TYPING_SAVE_DELAY_MS),
    )
  }

  async function persistTyped(questionId: number, value: string): Promise<boolean> {
    if (!canPersist()) return false
    options.onActionError(null)
    try {
      if (value) await studentClassesApi.saveAnswer(session.attemptId, { questionId, answerText: value })
      else await studentClassesApi.clearAnswer(session.attemptId, questionId)
      if (!canPersist()) return false
      savedTyped.value[questionId] = value
      return true
    } catch (reason) {
      if (canPersist()) {
        // The server may have committed the write before the response was lost.
        delete savedTyped.value[questionId]
        options.onActionError(resolveApiError(reason, options.saveErrorMessage()))
      }
      return false
    }
  }

  /** Saves what the box holds if it changed; returns false when that save failed. */
  async function saveTyped(questionId: number): Promise<boolean> {
    clearTimeout(typingTimers.get(questionId))
    typingTimers.delete(questionId)
    if (!canPersist()) return false
    // Blur, navigation, and debounce may ask to save the same question; serialize them and re-read latest text.
    while (pendingTyped.has(questionId)) {
      const active = pendingTyped.get(questionId)!
      const saved = await active.promise
      if (!canPersist()) return false
      if (!saved && (typed.value[questionId] ?? '').trim() === active.text) return false
    }
    const value = (typed.value[questionId] ?? '').trim()
    if (value === savedTyped.value[questionId]) return true
    const entry = { text: value, promise: persistTyped(questionId, value) }
    pendingTyped.set(questionId, entry)
    try {
      return await entry.promise
    } finally {
      if (pendingTyped.get(questionId) === entry) pendingTyped.delete(questionId)
    }
  }

  /** Flushes each typed answer in quiz order, including restored drafts. Choices were saved when selected. */
  async function flushTypedAnswers(): Promise<boolean> {
    for (const id of typedQuestionIds) {
      if (!(await saveTyped(id))) return false
    }
    return true
  }

  return {
    chosen,
    typed,
    savedTyped,
    savingOption,
    isAnswered,
    answeredCount,
    remaining,
    stopTimers,
    choose,
    typeAnswer,
    saveTyped,
    flushTypedAnswers,
  }
}
