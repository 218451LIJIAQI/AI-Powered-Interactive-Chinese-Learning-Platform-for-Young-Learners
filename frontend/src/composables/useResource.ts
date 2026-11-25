import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import type { ComputedRef, Ref } from 'vue'

import { pauseDuringNavigation } from '@/composables/usePageNavigation'
import type { PageResponse } from '@/types/api'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'

interface ResourceState<T> {
  readonly data: Ref<T | null>
  readonly error: Ref<string | null>
  readonly errorStatus: Ref<number | null>
  readonly errorReason: Ref<unknown>
  readonly loading: Ref<boolean>
  readonly reload: () => Promise<void>
  readonly replace: (value: T | null) => void
}

/**
 * Data a page reads from the API: `loading` until the first answer (a reload keeps showing the last data), the
 * translated `error` (and HTTP `errorStatus` / raw `errorReason`) of a failed request, and `reload`. Out-of-order
 * answers are dropped: only the latest request updates the state. With `immediate` false the first load waits for an
 * explicit `reload()`.
 */
export function useResource<T>(loader: () => Promise<T>, immediate = true): ResourceState<T> {
  const data = shallowRef<T | null>(null)
  const error = ref<string | null>(null)
  const errorStatus = ref<number | null>(null)
  const errorReason = shallowRef<unknown>(null)
  const loading = ref(immediate)
  let executionSequence: number | null = 0

  async function execute(): Promise<void> {
    if (executionSequence === null) return
    const sequence = ++executionSequence
    loading.value = data.value === null || error.value !== null
    error.value = null
    errorStatus.value = null
    errorReason.value = null

    try {
      const result = await loader()
      if (sequence === executionSequence) data.value = result
    } catch (reason) {
      if (sequence === executionSequence) {
        error.value = resolveApiError(reason)
        errorStatus.value = apiErrorStatus(reason)
        errorReason.value = reason
      }
    }
    if (sequence === executionSequence) loading.value = false
  }

  /** Commits authoritative data (or null to clear), dropping any older in-flight load. */
  function replace(value: T | null): void {
    if (executionSequence === null) return
    executionSequence += 1
    data.value = value
    error.value = null
    errorStatus.value = null
    errorReason.value = null
    loading.value = false
  }

  if (immediate) onMounted(() => void execute())
  onBeforeUnmount(() => {
    executionSequence = null
    loading.value = false
  })

  return { data, error, errorStatus, errorReason, loading, reload: execute, replace }
}

/**
 * The record a key/id names, emptied and loaded again whenever the key changes. A null key clears synchronously and
 * invalidates any in-flight request for the previous key so stale responses or errors never land.
 */
export function useRecordById<T, K = number>(id: Readonly<Ref<K | null>>, load: (id: K) => Promise<T>) {
  const record = useResource(() => (id.value === null ? Promise.resolve(null) : load(id.value)), false)

  watch(
    id,
    (nextId) => {
      if (nextId === null) {
        record.replace(null)
        return
      }
      record.data.value = null
      void record.reload()
    },
    { immediate: true },
  )

  return record
}

/**
 * A filter edit opens the first page and updates the address. Restoring filters from an address
 * keeps its page and selection. A change only to the page keeps the current results while loading.
 */
export function usePageFilters<T>(
  filters: Readonly<Ref<unknown>>[],
  page: Ref<number>,
  resource: Pick<ResourceState<T>, 'data' | 'reload'>,
  onEdit: () => void,
) {
  let committed = filters.map((filter) => filter.value)
  let linkedPage: number | null = null
  watch(filters, () => {
    committed = filters.map((filter) => filter.value)
    page.value = linkedPage ?? 0
    resource.data.value = null
    if (linkedPage === null) onEdit()
    linkedPage = null
    void resource.reload()
  })

  return function followAddress(nextPage: number, applyFilters: () => void): void {
    applyFilters()
    // Compare against the watcher's last committed values so a same-tick change reversed by applyFilters still updates page.
    if (filters.some((filter, index) => !Object.is(filter.value, committed[index]))) {
      linkedPage = nextPage
      void nextTick(() => {
        if (linkedPage === nextPage) linkedPage = null
      })
    } else if (nextPage !== page.value) {
      page.value = nextPage
      void resource.reload()
    }
  }
}

interface ServerPage<T> {
  readonly items: ComputedRef<T[]>
  readonly displayedPage: ComputedRef<number>
  readonly totalItems: ComputedRef<number>
  readonly totalPages: ComputedRef<number>
  readonly goToPage: (next: number) => void
}

interface ServerPageOptions {
  /**
   * A later page that comes back without rows (its last row was answered, moved or removed, or an address points past
   * the end) steps back to the last page that has some and loads it, instead of saying there is nothing.
   */
  readonly stepBackFromEmpty?: boolean
  /** Keeps a paginated address in sync with both navigation and a step back from an empty page. */
  readonly onPageChange?: () => void
}

/**
 * The rows and totals of a server-paged resource, and `goToPage`, which keeps the 0-based `page` within
 * the known pages and reloads. A page that sits inside a larger answer (`{ requests: page, counts }`) comes in as a
 * computed `data`.
 */
export function useServerPage<T>(
  resource: { readonly data: Readonly<Ref<PageResponse<T> | null>>; readonly reload: () => unknown },
  page: Ref<number>,
  { stepBackFromEmpty = false, onPageChange }: ServerPageOptions = {},
): ServerPage<T> {
  const current = resource.data
  const items = computed(() => current.value?.items ?? [])
  const displayedPage = computed(() => current.value?.page ?? page.value)
  const totalItems = computed(() => current.value?.totalItems ?? 0)
  const totalPages = computed(() => Math.max(1, current.value?.totalPages ?? 1))

  function goToPage(next: number): void {
    page.value = Math.min(Math.max(0, next), totalPages.value - 1)
    onPageChange?.()
    void resource.reload()
  }

  if (stepBackFromEmpty) {
    const stepBack = watch(current, (loaded) => {
      if (!loaded || loaded.items.length || page.value === 0) return
      goToPage(page.value - 1)
    })
    if (onPageChange) pauseDuringNavigation(stepBack)
  }

  return { items, displayedPage, totalItems, totalPages, goToPage }
}
