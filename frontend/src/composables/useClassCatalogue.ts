import { computed, ref } from 'vue'

import { publicClassesApi } from '@/api/classes'
import { useResource } from '@/composables/useResource'
import type { PageResponse } from '@/types/api'
import type { CatalogueQuery, ClassCard } from '@/types/classes'

const PAGE_SIZE = 12
/** The catalogue is small, so it is loaded whole: the theme chips count it and filter it without a round trip. */
const CATALOGUE_SIZE = 100

export interface CatalogueTheme {
  name: string
  count: number
}

/**
 * A class catalogue page (public Classes, Find classes): the whole catalogue, the themes in it with their counts (in
 * the order the API lists them), the selected theme (null is All) and the page of matching cards.
 */
export function useClassCatalogue(loader: (query: CatalogueQuery) => Promise<PageResponse<ClassCard>>) {
  const resource = useResource(() => loader({ page: 0, size: CATALOGUE_SIZE }))
  const summary = useResource(() => publicClassesApi.summary())
  const theme = ref<string | null>(null)
  const page = ref(0)

  const allCards = computed(() => resource.data.value?.items ?? [])

  const themes = computed<CatalogueTheme[]>(() => {
    const counts = new Map<string, number>()
    for (const card of allCards.value) {
      if (card.topicName) counts.set(card.topicName, (counts.get(card.topicName) ?? 0) + 1)
    }
    const order = (summary.data.value?.topics ?? []).map((topic) => topic.name)
    const position = (name: string) => (order.includes(name) ? order.indexOf(name) : order.length)
    return [...counts.keys()]
      .sort((a, b) => position(a) - position(b))
      .map((name) => ({ name, count: counts.get(name) ?? 0 }))
  })

  const matching = computed(() =>
    theme.value === null ? allCards.value : allCards.value.filter((card) => card.topicName === theme.value),
  )
  const totalItems = computed(() => matching.value.length)
  const totalPages = computed(() => Math.ceil(totalItems.value / PAGE_SIZE))
  const items = computed(() => matching.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE))

  function selectTheme(next: string | null): void {
    theme.value = next
    page.value = 0
  }

  function goToPage(next: number): void {
    page.value = next
  }

  return { resource, allCards, themes, theme, selectTheme, page, items, totalItems, totalPages, goToPage }
}
