import { ref } from 'vue'

import { practiceApi } from '@/api/learn'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import type { AnswerCheck, PracticeItemAnswer, PracticeResult, PracticeSet } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'

/**
 * The server calls of one run of a Practice set: instant checks (a wrong answer becomes a mistake) and the finished
 * run. `busy` covers both; `error` holds the last failure in the interface language.
 */
export function usePracticeRun(set: () => PracticeSet, failure: () => string) {
  const busy = ref(false)
  const error = ref<string | null>(null)

  async function run<T>(call: () => Promise<T>): Promise<T | null> {
    busy.value = true
    error.value = null
    try {
      return await call()
    } catch (reason) {
      error.value = resolveApiError(reason, failure())
      return null
    } finally {
      busy.value = false
    }
  }

  function check(itemKey: string, answer: string | null): Promise<AnswerCheck | null> {
    const current = set()
    return run(() => practiceApi.check(current.type, current.id, { itemKey, answer }))
  }

  /** Several checks at once (matching pairs, story questions), in the order given. */
  function checkAll(answers: readonly PracticeItemAnswer[]): Promise<AnswerCheck[] | null> {
    const current = set()
    return run(() => Promise.all(answers.map((answer) => practiceApi.check(current.type, current.id, answer))))
  }

  async function complete(
    answers: readonly PracticeItemAnswer[],
    strokeCount?: number,
  ): Promise<PracticeResult | null> {
    const current = set()
    const result = await run(() => practiceApi.complete(current.type, current.id, answers, strokeCount))
    if (result) notifyProgressChanged()
    return result
  }

  return { busy, error, check, checkAll, complete }
}
