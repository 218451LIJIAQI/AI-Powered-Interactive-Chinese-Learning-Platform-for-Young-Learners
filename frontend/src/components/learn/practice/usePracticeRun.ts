import { onScopeDispose, ref } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import { practiceApi } from '@/api/learn'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import type { AnswerCheck, PracticeItemAnswer, PracticeResult, PracticeSet } from '@/types/learn'
import { resolveApiError } from '@/utils/apiError'

/**
 * An answer as a player checks it. A Listening skip also says when the item's audio did not play (`audioFailed`): the
 * server then shows the answer but counts nothing, neither a mistake nor an answer.
 */
export interface CheckedAnswer extends PracticeItemAnswer {
  readonly audioFailed?: true
}

/**
 * The server calls of one run of a Practice set: instant checks (a wrong answer becomes a mistake) and the finished
 * run. `busy` covers both; `error` holds the last failure in the interface language (`t` is the player's, with the
 * `practice` bundle).
 */
export function usePracticeRun(set: () => PracticeSet, t: (key: string) => string) {
  const busy = ref(false)
  const error = ref<string | null>(null)
  let active = true
  onScopeDispose(() => {
    active = false
  })

  async function run<T>(call: () => Promise<T>): Promise<T | null> {
    if (busy.value || !active) return null
    busy.value = true
    error.value = null
    try {
      const value = await call()
      return active ? value : null
    } catch (reason) {
      if (active) error.value = resolveApiError(reason, t('practice.saveFailed'))
      return null
    } finally {
      if (active) busy.value = false
    }
  }

  /** One answer, with how sure the student was (the server counts it as a guess after a hint it gave). */
  function check(answer: CheckedAnswer): Promise<AnswerCheck | null> {
    const current = set()
    return run(() => practiceApi.check(current.type, current.id, answer))
  }

  async function complete(
    answers: readonly PracticeItemAnswer[],
    strokeCount?: number,
  ): Promise<PracticeResult | null> {
    const current = set()
    const session = getAuthSessionVersion()
    return run(async () => {
      const result = await practiceApi.complete(current.type, current.id, answers, strokeCount)
      if (session === getAuthSessionVersion()) notifyProgressChanged()
      return result
    })
  }

  return { busy, error, run, check, complete }
}
