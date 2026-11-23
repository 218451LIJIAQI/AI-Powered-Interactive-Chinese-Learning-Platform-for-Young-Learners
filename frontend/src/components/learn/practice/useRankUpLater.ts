import { readonly, ref, watch } from 'vue'

import type { XpResult } from '@/types/learn'

/** How long a finished set or review shows its result before the rank-up dialog opens over it. */
const RANK_UP_DELAY_MS = 600

/**
 * The rank-up dialog of Practice (a finished set or the mistakes review): it opens a moment after a result that took
 * the learner past a rank appears, once for that result, and closes with it (Practise again, another set).
 */
export function useRankUpLater(xp: () => XpResult | null) {
  const rankingUp = ref(false)
  let timer: ReturnType<typeof setTimeout> | undefined

  function dismiss(): void {
    clearTimeout(timer)
    timer = undefined
    rankingUp.value = false
  }

  watch(xp, (current, _, onCleanup) => {
    dismiss()
    if (!current?.rankUp) return
    timer = setTimeout(() => {
      timer = undefined
      rankingUp.value = true
    }, RANK_UP_DELAY_MS)
    onCleanup(dismiss)
  })

  return { rankingUp: readonly(rankingUp), dismiss }
}
