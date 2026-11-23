import { ref, watch } from 'vue'

import { usePageActive } from '@/composables/usePageNavigation'

const SEARCH_DELAY_MS = 300

/**
 * A search box for a server-side search: `keyword` takes the trimmed `searchTerm` once typing has paused for
 * 300 ms, so a request is not sent on every key. Other filters can update the URL while a search is being typed.
 * Restore the text from the URL only when its applied keyword changes.
 */
export function useDebouncedSearch(initial = '') {
  const searchTerm = ref(initial)
  const keyword = ref(initial.trim())
  watch(
    [searchTerm, usePageActive()],
    ([value, active], _previous, onCleanup) => {
      const next = value.trim()
      if (!active || next === keyword.value) return
      const timer = setTimeout(() => {
        keyword.value = next
      }, SEARCH_DELAY_MS)
      onCleanup(() => clearTimeout(timer))
    },
    { flush: 'sync' },
  )

  function setTerm(value: string): void {
    keyword.value = value.trim()
    searchTerm.value = value
  }

  function restore(value: string): void {
    if (value.trim() !== keyword.value) setTerm(value)
  }

  function clear(): void {
    setTerm('')
  }

  return { searchTerm, keyword, restore, clear }
}
