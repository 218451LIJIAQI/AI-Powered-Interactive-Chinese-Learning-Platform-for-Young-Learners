import { computed, reactive, ref, shallowRef } from 'vue'

import { aiHelperApi } from '@/api/ai'
import { getAuthSessionVersion } from '@/api/http'
import { appLocale } from '@/plugins/i18n'
import type { XiaomeiMemoryNote, XiaomeiScenario, XiaomeiSummary, XiaomeiTurn } from '@/types/ai'
import { resolveApiError } from '@/utils/apiError'
import { serverMessageOr } from '@/utils/serverMessages'

export interface UseXiaomeiSummaryMemoryOptions {
  getConversation: () => number
  getScenario: () => XiaomeiScenario
  getTurns: () => XiaomeiTurn[]
  isEndedOnScreen: () => boolean
  hasSummaryView: () => boolean
  t: (key: string) => string
  onUsed: () => void
  onToast: (message: string, action?: { label: string; run: () => void }) => void
}

/**
 * Owns Xiaomei's end-of-conversation summary request and serialized memory-note Forget/Restore operations under the
 * signing-in account that started the tab.
 */
export function useXiaomeiSummaryMemory(options: UseXiaomeiSummaryMemoryOptions) {
  const owner = getAuthSessionVersion()
  const ownsSession = () => getAuthSessionVersion() === owner
  const summary = ref<XiaomeiSummary | null>(null)
  const summaryLoading = ref(false)
  const summaryError = ref<string | null>(null)
  const summaryReported = ref(false)
  /** The last server snapshot, with pending removals hidden until the server confirms or refuses them. */
  const remembered = shallowRef<XiaomeiMemoryNote[] | null>(null)
  const forgetting = reactive(new Set<number>())
  const memory = computed(() => remembered.value?.filter((note) => !forgetting.has(note.id)) ?? null)
  const memoryError = ref<string | null>(null)

  /** Forget and Undo reach the server one at a time, under the account that owns these notes. */
  let memoryChanges: Promise<unknown> = Promise.resolve()

  function reset(): void {
    summary.value = null
    summaryError.value = null
    summaryLoading.value = false
    summaryReported.value = false
    remembered.value = null
    memoryError.value = null
  }

  /** Read the latest notes without overwriting a newer summary or a completed Forget/Undo. */
  async function loadMemory(mine: number): Promise<void> {
    if (!ownsSession()) return
    const previous = remembered.value
    try {
      const notes = await aiHelperApi.xiaomeiMemory()
      if (ownsSession() && mine === options.getConversation() && remembered.value === previous) remembered.value = notes
    } catch {
      // Nothing is known about the notes: the summary goes on without them.
    }
  }

  async function loadSummary(): Promise<void> {
    if (summaryLoading.value || !ownsSession()) return
    const mine = options.getConversation()
    const previousNotes = remembered.value
    summaryLoading.value = true
    summaryError.value = null
    summary.value = null
    try {
      const result = await aiHelperApi.xiaomeiSummary({
        scenario: options.getScenario(),
        turns: options.getTurns(),
        languagePreference: appLocale.value,
      })
      if (!ownsSession() || mine !== options.getConversation()) return
      if (result.status === 'SUCCESS') summary.value = result
      else summaryError.value = serverMessageOr(result.message, options.t('aiHelper.summaryError'))
      if (result.memory && remembered.value === previousNotes) remembered.value = result.memory
      else void loadMemory(mine)
    } catch (reason) {
      if (!ownsSession() || mine !== options.getConversation()) return
      summaryError.value = resolveApiError(reason, options.t('aiHelper.summaryError'))
      void loadMemory(mine)
    } finally {
      if (mine === options.getConversation()) summaryLoading.value = false
      if (ownsSession()) options.onUsed()
    }
  }

  function changeMemory(id: number, change: () => Promise<XiaomeiMemoryNote[]>): Promise<boolean> {
    const applied = memoryChanges.then(async () => {
      if (!ownsSession()) return false
      const previous = remembered.value
      const notes = await change()
      if (!ownsSession()) return false
      const current = remembered.value
      if (current === previous || current === null) {
        remembered.value = notes
      } else {
        // A summary or notes read finished during the write. Keep its other facts until the latest snapshot arrives.
        const others = current.filter((note) => note.id !== id)
        const restored = notes.find((note) => note.id === id)
        remembered.value = restored ? [...others, restored] : others
        if (options.hasSummaryView()) void loadMemory(options.getConversation())
      }
      return true
    })
    memoryChanges = applied.catch(() => undefined)
    return applied
  }

  async function forgetNote(note: XiaomeiMemoryNote): Promise<void> {
    const mine = options.getConversation()
    memoryError.value = null
    forgetting.add(note.id)
    try {
      const forgotten = await changeMemory(note.id, () => aiHelperApi.forgetXiaomeiNote(note.id))
      if (!forgotten || mine !== options.getConversation()) return
      options.onToast(options.t('aiHelper.memoryForgotten'), {
        label: options.t('common.undo'),
        run: () => void restoreNote(note, mine),
      })
    } catch (reason) {
      if (ownsSession() && mine === options.getConversation()) {
        memoryError.value = resolveApiError(reason, options.t('aiHelper.memoryForgetError'))
      }
    } finally {
      forgetting.delete(note.id)
    }
  }

  async function restoreNote(note: XiaomeiMemoryNote, originConversation: number): Promise<void> {
    if (originConversation === options.getConversation()) memoryError.value = null
    try {
      await changeMemory(note.id, () => aiHelperApi.restoreXiaomeiNote(note.id))
    } catch (reason) {
      if (!ownsSession()) return
      const problem = resolveApiError(reason, options.t('aiHelper.memoryRestoreError'))
      if (originConversation === options.getConversation() && options.isEndedOnScreen()) memoryError.value = problem
      else options.onToast(problem)
    }
  }

  return {
    summary,
    summaryLoading,
    summaryError,
    summaryReported,
    memory,
    memoryError,
    reset,
    loadSummary,
    forgetNote,
  }
}
