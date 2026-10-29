import { computed, onMounted, ref, shallowRef } from 'vue'
import type { ComputedRef, Ref } from 'vue'

import type { PageResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatNumber } from '@/utils/format'

interface ResourceState<T> {
  readonly data: Ref<T | null>
  readonly error: Ref<string | null>
  readonly loading: Ref<boolean>
  readonly reload: () => Promise<void>
}

/**
 * Data a page reads from the API: `loading` until the first answer (a reload keeps showing the last data), the
 * translated `error` of a failed request, and `reload`. Out-of-order answers are dropped: only the latest request
 * updates the state. With `immediate` false the first load waits for an explicit `reload()`.
 */
export function useResource<T>(loader: () => Promise<T>, immediate = true): ResourceState<T> {
  const data = shallowRef<T | null>(null)
  const error = ref<string | null>(null)
  const loading = ref(immediate)
  let executionSequence = 0

  async function execute(): Promise<void> {
    const sequence = ++executionSequence
    loading.value = data.value === null
    error.value = null

    try {
      const result = await loader()
      if (sequence !== executionSequence) return
      data.value = result
    } catch (reason) {
      if (sequence !== executionSequence) return
      error.value = resolveApiError(reason)
    } finally {
      if (sequence === executionSequence) {
        loading.value = false
      }
    }
  }

  if (immediate) onMounted(() => void execute())

  return { data, error, loading, reload: execute }
}

interface ServerPageOptions {
  /** The page's own reload, e.g. one that also clears a success message (`resource.reload` by default). */
  reload?: () => unknown
}

interface ServerPage<T> {
  readonly items: ComputedRef<T[]>
  readonly totalItems: ComputedRef<number>
  readonly totalPages: ComputedRef<number>
  /** `{ from, to, total }` for a "Showing 11–20 of 45" line, formatted; 0–0 when the page has no rows. */
  readonly range: ComputedRef<{ from: string; to: string; total: string }>
  readonly goToPage: (next: number) => void
}

/**
 * The rows, totals and shown range of a server-paged resource, and `goToPage`, which keeps the 0-based `page` within
 * the known pages and reloads. When the page sits inside a larger answer (`{ requests: page, counts }`), `select`
 * picks it out.
 */
export function useServerPage<T>(
  resource: Pick<ResourceState<PageResponse<T> | null>, 'data' | 'reload'>,
  page: Ref<number>,
  options?: ServerPageOptions,
): ServerPage<T>
export function useServerPage<R, T>(
  resource: Pick<ResourceState<R>, 'data' | 'reload'>,
  page: Ref<number>,
  options: ServerPageOptions & { select: (data: R) => PageResponse<T> },
): ServerPage<T>
export function useServerPage<R, T>(
  resource: Pick<ResourceState<R>, 'data' | 'reload'>,
  page: Ref<number>,
  { select, reload = resource.reload }: ServerPageOptions & { select?: (data: R) => PageResponse<T> } = {},
): ServerPage<T> {
  const current = computed(() => {
    const data = resource.data.value
    if (data === null) return null
    return select ? select(data) : (data as PageResponse<T> | null)
  })
  const items = computed(() => current.value?.items ?? [])
  const totalItems = computed(() => current.value?.totalItems ?? 0)
  const totalPages = computed(() => Math.max(1, current.value?.totalPages ?? 1))
  const range = computed(() => {
    const shown = items.value.length
    const first = shown ? current.value!.page * current.value!.size + 1 : 0
    return {
      from: formatNumber(first),
      to: formatNumber(shown ? first + shown - 1 : 0),
      total: formatNumber(totalItems.value),
    }
  })

  function goToPage(next: number): void {
    page.value = Math.min(Math.max(0, next), totalPages.value - 1)
    void reload()
  }

  return { items, totalItems, totalPages, range, goToPage }
}
