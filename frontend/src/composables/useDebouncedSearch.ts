import { onBeforeUnmount, ref, watch } from 'vue'

const SEARCH_DELAY_MS = 300

/**
 * A search box for a server-side search: `keyword` takes the trimmed `searchTerm` once typing has paused for
 * 300 ms, so a request is not sent on every key. `clear()` empties both at once.
 */
export function useDebouncedSearch() {
  const searchTerm = ref('')
  const keyword = ref('')
  let timer: ReturnType<typeof setTimeout> | null = null

  function cancel(): void {
    if (timer) clearTimeout(timer)
    timer = null
  }

  watch(searchTerm, (value) => {
    cancel()
    timer = setTimeout(() => {
      timer = null
      keyword.value = value.trim()
    }, SEARCH_DELAY_MS)
  })
  onBeforeUnmount(cancel)

  function clear(): void {
    cancel()
    searchTerm.value = ''
    keyword.value = ''
  }

  return { searchTerm, keyword, clear }
}
