import { computed, onBeforeUnmount, ref, watch } from 'vue'

import type { CatalogueFilters } from '@/components/classes/ClassCatalogueFilters.vue'
import { useResource, useServerPage } from '@/composables/useResource'
import type { PageResponse } from '@/types/api'
import type { CatalogueQuery, ClassCard } from '@/types/classes'

const PAGE_SIZE = 12
const SEARCH_DELAY_MS = 300

const EMPTY_FILTERS: CatalogueFilters = {
  keyword: '',
  topicId: null,
  levelId: null,
  learningType: null,
  sort: 'RECENT',
}

/**
 * A server-side class catalogue page: the filters reload the first page (typing waits for a pause), and
 * `goToPage` moves through the result pages.
 */
export function useClassCatalogue(loader: (query: CatalogueQuery) => Promise<PageResponse<ClassCard>>) {
  const filters = ref<CatalogueFilters>({ ...EMPTY_FILTERS })
  const page = ref(0)

  const resource = useResource(() =>
    loader({
      keyword: filters.value.keyword.trim() || undefined,
      topicId: filters.value.topicId ?? undefined,
      levelId: filters.value.levelId ?? undefined,
      learningType: filters.value.learningType ?? undefined,
      sort: filters.value.sort,
      page: page.value,
      size: PAGE_SIZE,
    }),
  )
  const { items, totalItems, totalPages, goToPage } = useServerPage(resource, page)

  let timer: ReturnType<typeof setTimeout> | null = null
  watch(filters, (next, previous) => {
    if (timer) clearTimeout(timer)
    const reload = () => {
      timer = null
      page.value = 0
      void resource.reload()
    }
    if (next.keyword !== previous.keyword) timer = setTimeout(reload, SEARCH_DELAY_MS)
    else reload()
  })
  onBeforeUnmount(() => {
    if (timer) clearTimeout(timer)
  })

  const filtered = computed(
    () =>
      Boolean(filters.value.keyword.trim()) ||
      filters.value.topicId !== null ||
      filters.value.levelId !== null ||
      filters.value.learningType !== null,
  )

  function clearFilters(): void {
    filters.value = { ...EMPTY_FILTERS }
  }

  return { filters, page, resource, items, totalItems, totalPages, goToPage, filtered, clearFilters }
}
