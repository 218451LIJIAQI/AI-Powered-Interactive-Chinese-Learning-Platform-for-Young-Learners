import { computed, onBeforeUnmount, reactive, ref, watch, type Ref } from 'vue'

import { useHintOffer } from '@/composables/useHintOffer'
import { useHintSettings } from '@/composables/useHintSettings'
import type { HintStep, PracticeType } from '@/types/learn'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'

interface StuckHintOptions {
  /** The item on screen; a new item starts over. */
  readonly itemKey: Readonly<Ref<string | null>>
  /** Stable identifier for set-budget deduplication (defaults to `itemKey`, so retries of the same item don't double-count). */
  readonly budgetKey?: Readonly<Ref<string | null>>
  /** True while the item waits for an answer (not checked, not saving). */
  readonly open: Readonly<Ref<boolean>>
  /** Set to false on single-hint boards/sets (Matching, Tracing) so a multi-hint per-set counter is not shown. */
  readonly perSetBudget?: boolean
  /** Loads the current item's hint. */
  readonly load: () => Promise<HintStep>
}

/**
 * Loads one hint per item when the learner accepts the offer from `useHintOffer`. The server chooses a hint for
 * the learner's level and enforces the allowance for the signed run. A successful request counts even if the learner
 * has moved to the next item. A refusal ends the offer for that item, while a failed request can be retried.
 */
export function useStuckHint(options: StuckHintOptions) {
  /** The student's setting and level rule, shared by every player; null while it loads (no hints yet). */
  const { settings } = useHintSettings()
  const hint = ref<HintStep | null>(null)
  const loading = ref(false)
  const failed = ref(false)
  /**
   * Why the server refused the hint, in the interface language ("There are no hints left in this set." when the rule
   * changed since it was read here): none is offered again for this item. Null for a hint that could not be loaded.
   */
  const refusal = ref<string | null>(null)
  /** Distinct items of this run that opened a hint. */
  const usedItems = reactive(new Set<string>())
  /** Bumped on reset() so a new run restarts offer timers and ignores late responses from the previous run. */
  const runVersion = ref(0)
  /** Counts hint requests within a run: moving to the next item stops the previous item's hint from displaying. */
  let itemRequest = 0
  let disposed = false

  const mode = computed(() => settings.value?.mode ?? 'OFF')
  const budgetId = computed(() => options.budgetKey?.value ?? options.itemKey.value)
  const budgetLeft = computed(() => {
    if (options.perSetBudget === false) return null
    const perSet = settings.value?.perSet ?? 0
    return perSet > 0 ? Math.max(perSet - usedItems.size, 0) : null
  })
  /** The item's hint can still be offered: hints are on, it has not opened yet, and the set has budget (or already served this item). */
  const available = computed(
    () =>
      options.itemKey.value !== null &&
      mode.value !== 'OFF' &&
      refusal.value === null &&
      hint.value === null &&
      (budgetLeft.value !== 0 || (budgetId.value !== null && usedItems.has(budgetId.value))),
  )

  function clearItem(): void {
    itemRequest += 1
    loading.value = false
    hint.value = null
    failed.value = false
    refusal.value = null
  }
  watch(options.itemKey, clearItem)
  // The offer goes once the item's hint opens or is refused, or once the set has no hints left (a late hint for the item
  // left behind counts too). Prefixing with runVersion ensures reset() clears any stale offer even on the same itemKey.
  const offer = useHintOffer({
    itemKey: computed(() => (options.itemKey.value === null ? null : `${runVersion.value}:${options.itemKey.value}`)),
    open: computed(() => options.open.value && available.value),
  })

  async function show(): Promise<void> {
    const itemId = options.budgetKey?.value ?? options.itemKey.value
    if (itemId === null || loading.value || !available.value || !options.open.value) return
    const myRun = runVersion.value
    const mine = ++itemRequest
    loading.value = true
    failed.value = false
    try {
      const loaded = await options.load()
      if (disposed || myRun !== runVersion.value) return
      // Given by the server, so it counts against the set's hints even when the student has moved on to another item.
      usedItems.add(itemId)
      // The item may have been answered, completed, or left while the hint was on its way.
      if (mine !== itemRequest || !options.open.value) return
      hint.value = loaded
    } catch (reason) {
      if (disposed || myRun !== runVersion.value || mine !== itemRequest || !options.open.value) return
      failed.value = true
      // A refusal says why and ends this item's hint; a transient failure can be asked for again.
      const status = apiErrorStatus(reason)
      if (status === 400 || status === 403 || status === 404) refusal.value = resolveApiError(reason)
    } finally {
      if (!disposed && myRun === runVersion.value && mine === itemRequest) loading.value = false
    }
  }

  function reset(): void {
    runVersion.value += 1
    usedItems.clear()
    clearItem()
  }

  onBeforeUnmount(() => {
    disposed = true
    itemRequest += 1
  })

  return {
    mode,
    /** The item's hint once it has opened. */
    hint: computed(() => hint.value),
    offered: offer.offered,
    loading,
    failed,
    refusal,
    budgetLeft,
    /** The item's hint has opened: the answer then counts as a guess (the server knows the hints it gave). */
    used: computed(() => hint.value !== null),
    ruledOut: computed(() => hint.value?.ruledOut ?? []),
    show,
    poke: offer.poke,
    reset,
  }
}

export type StuckHint = ReturnType<typeof useStuckHint>

/**
 * What a Practice hint belongs to, for a report to the admins (`PracticeHint`'s `reportContext`): the activity, the
 * set's id and the item's key ("LISTENING #14 / 2"). An identifier, not a sentence: it reads the same in every
 * language.
 */
export function hintReportContext(set: { readonly type: PracticeType; readonly id: number }, itemKey?: string): string {
  return itemKey === undefined ? `${set.type} #${set.id}` : `${set.type} #${set.id} / ${itemKey}`
}
