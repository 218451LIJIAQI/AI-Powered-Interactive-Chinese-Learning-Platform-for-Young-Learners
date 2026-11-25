import { computed, ref } from 'vue'

import { publicClassesApi } from '@/api/classes'
import { useResource } from '@/composables/useResource'
import type { PageResponse } from '@/types/api'
import type { CatalogueQuery, ClassCard } from '@/types/classes'

const PAGE_SIZE = 12
/** The catalogue is small, so it is loaded whole: the theme chips count it and filter it without a round trip. */
const CATALOGUE_PAGE_SIZE = 100

/**
 * A class catalogue page (public Classes, Find classes): the whole catalogue, the themes in it with their counts (in
 * the order the API lists them), the selected theme (null is All) and the page of matching cards.
 */
export function useClassCatalogue(loader: (query: CatalogueQuery) => Promise<PageResponse<ClassCard>>) {
  const baseResource = useResource(async () => {
    const first = await loader({ page: 0, size: CATALOGUE_PAGE_SIZE })
    if (first.totalPages <= 1) return [...first.items]
    const rest = await Promise.all(
      Array.from({ length: first.totalPages - 1 }, (_, index) =>
        loader({ page: index + 1, size: CATALOGUE_PAGE_SIZE }),
      ),
    )
    // Publishing or archiving classes between requests can put the same class on two pages.
    const cards = new Map<number, ClassCard>()
    for (const page of [first, ...rest]) {
      for (const card of page.items) cards.set(card.id, card)
    }
    return [...cards.values()]
  })
  const topics = useResource(publicClassesApi.topics)
  const resource = {
    ...baseResource,
    async reload(): Promise<void> {
      await Promise.all([baseResource.reload(), topics.data.value === null ? topics.reload() : Promise.resolve()])
    },
  }
  const theme = ref<string | null>(null)
  const page = ref(0)

  const allCards = computed(() => resource.data.value ?? [])

  const themes = computed(() => {
    const counts = new Map<string, number>()
    for (const card of allCards.value) {
      if (card.topicName) counts.set(card.topicName, (counts.get(card.topicName) ?? 0) + 1)
    }
    const order = new Map((topics.data.value ?? []).map((topic, index) => [topic.name, index]))
    return [...counts.entries()]
      .sort(([a], [b]) => (order.get(a) ?? Number.MAX_SAFE_INTEGER) - (order.get(b) ?? Number.MAX_SAFE_INTEGER))
      .map(([name, count]) => ({ name, count }))
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

  return { resource, allCards, themes, theme, selectTheme, page, items, totalItems, totalPages }
}
