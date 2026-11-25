import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch, type Ref } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import type { AnswerCheck, HintStep, QuestionAnswer, QuestionView } from '@/types/learn'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { questionShortcut } from '@/utils/questionShortcut'

import { useStuckHint } from './practice/useStuckHint'

interface QuestionRunOptions<TResult> {
  questions: Readonly<Ref<readonly QuestionView[]>>
  grade: (answer: QuestionAnswer) => Promise<AnswerCheck>
  /** Sends every answer once the run is over; null skips committing a result (e.g. missing lesson id). */
  saveResult: (answers: readonly QuestionAnswer[]) => Promise<TResult | null>
  /** The page's, with the `learn` bundle (for the failure messages). */
  t: (key: string) => string
  onChecked?: (question: QuestionView, verdict: AnswerCheck) => void
  /** Continue after the last question; by default it saves the result (`complete`). */
  onLastQuestion?: () => void | Promise<void>
  keysBlocked: () => boolean
  /** Whether the run has hints (it has by default); a question offers none once it is checked or being checked. */
  hintsAllowed?: () => boolean
  loadHint: (questionId: number) => Promise<HintStep>
}

/**
 * The server-graded questions of a Learn lesson and the mistakes review: the question on screen and its answer, the
 * verdict after Check or Skip, the answers sent so far and the result saved at the end, the current question's hint,
 * and the keys on the document (1–9 pick an answer, Enter checks it or goes on).
 */
export function useQuestionRun<TResult>(options: QuestionRunOptions<TResult>) {
  const index = ref(0)
  const answer = ref<string | null>(null)
  const check = ref<AnswerCheck | null>(null)
  const result = shallowRef<TResult | null>(null)
  const pending = ref<'answer' | 'result' | null>(null)
  const busy = computed(() => pending.value !== null)
  const savingResult = computed(() => pending.value === 'result')
  const actionError = ref<string | null>(null)
  const actionErrorStatus = ref<number | null>(null)
  const answers = ref<QuestionAnswer[]>([])
  const question = computed(() => options.questions.value[index.value] ?? null)
  const isLast = computed(() => index.value >= options.questions.value.length - 1)
  let runVersion = 0

  const hint = useStuckHint({
    itemKey: computed(() => (question.value ? `${index.value}:${question.value.id}` : null)),
    open: computed(() => question.value !== null && !check.value && !busy.value && (options.hintsAllowed?.() ?? true)),
    load: () => options.loadHint(question.value?.id ?? 0),
  })
  watch(answer, hint.poke)

  async function submit(given: string | null): Promise<void> {
    const current = question.value
    if (!current || busy.value || check.value) return
    const version = runVersion
    pending.value = 'answer'
    actionError.value = null
    actionErrorStatus.value = null
    const sent = { questionId: current.id, answer: given }
    try {
      const verdict = await options.grade(sent)
      if (version !== runVersion) return
      check.value = verdict
      answer.value = given
      answers.value.push(sent)
      options.onChecked?.(current, verdict)
    } catch (reason) {
      if (version === runVersion) {
        actionError.value = resolveApiError(reason, options.t('learn.checkFailed'))
        actionErrorStatus.value = apiErrorStatus(reason)
      }
    } finally {
      if (version === runVersion) pending.value = null
    }
  }

  async function next(): Promise<void> {
    if (busy.value || !check.value) return
    if (isLast.value) {
      if (options.onLastQuestion) await options.onLastQuestion()
      else await complete()
      return
    }
    index.value += 1
    answer.value = null
    check.value = null
  }

  /** Saves the result with every answer (after the last question, or the page's own last step). */
  async function complete(): Promise<void> {
    if (busy.value) return
    const version = runVersion
    const session = getAuthSessionVersion()
    pending.value = 'result'
    actionError.value = null
    actionErrorStatus.value = null
    try {
      const saved = await options.saveResult(answers.value)
      if (saved === null) return
      if (session === getAuthSessionVersion()) notifyProgressChanged()
      if (version === runVersion && session === getAuthSessionVersion()) result.value = saved
    } catch (reason) {
      if (version === runVersion) {
        actionError.value = resolveApiError(reason, options.t('learn.completeFailed'))
        actionErrorStatus.value = apiErrorStatus(reason)
      }
    } finally {
      if (version === runVersion) pending.value = null
    }
  }

  function reset(): void {
    runVersion += 1
    index.value = 0
    answer.value = null
    check.value = null
    result.value = null
    pending.value = null
    actionError.value = null
    actionErrorStatus.value = null
    answers.value = []
    hint.reset()
  }

  function onKeydown(event: KeyboardEvent): void {
    const current = question.value
    if (busy.value || result.value !== null || options.keysBlocked() || !current) return
    const shortcut = questionShortcut(event)
    if (shortcut === null) return
    if (shortcut === 'enter') {
      if (check.value) {
        event.preventDefault()
        void next()
      } else if (answer.value !== null) {
        event.preventDefault()
        void submit(answer.value)
      }
      return
    }
    if (check.value || current.answerKind !== 'CHOICE') return
    const option = current.options[shortcut]
    if (option && !hint.ruledOut.value.includes(option.text)) answer.value = option.text
  }

  onMounted(() => document.addEventListener('keydown', onKeydown))
  onBeforeUnmount(() => {
    runVersion += 1
    document.removeEventListener('keydown', onKeydown)
  })

  return {
    index,
    answer,
    check,
    result,
    busy,
    savingResult,
    actionError,
    actionErrorStatus,
    answers,
    question,
    isLast,
    hint,
    submit,
    next,
    complete,
    reset,
  }
}
