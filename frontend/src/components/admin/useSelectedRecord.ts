import { computed, watch } from 'vue'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'

import { pauseDuringNavigation, usePageActive } from '@/composables/usePageNavigation'
import { useRecordById } from '@/composables/useResource'
import { parseRecordId } from '@/utils/recordId'

/**
 * The record a list-detail admin page shows beside its list, named in the address (`?<param>=<id>`): `selectedId`,
 * its `detail` (see `useRecordById`), and `select`, which names another one (null: none) and keeps the rest of the
 * query. `listQuery` is the page's own address state (its filters, tab and page): every selection writes it too, so a
 * selection made while a filter change is still on its way into the address (which the selection's navigation would
 * cancel) cannot put the old filters back.
 */
export function useSelectedRecord<T>(
  param: string,
  load: (id: number) => Promise<T>,
  listQuery: () => LocationQueryRaw,
  firstId: () => number | undefined,
) {
  const route = useRoute()
  const router = useRouter()
  const pageName = route.name
  const pageActive = usePageActive()
  const selectedId = computed(() => (route.name === pageName ? parseRecordId(route.query[param]) : null))
  const detail = useRecordById(selectedId, load)

  function select(id: number | null) {
    return router.replace({
      query: { ...route.query, ...listQuery(), [param]: id === null ? undefined : String(id) },
    })
  }

  const selectFirst = watch(
    [firstId, selectedId],
    ([first, id]) => {
      if (pageActive.value && id === null && first !== undefined) void select(first)
    },
    { immediate: true },
  )
  pauseDuringNavigation(selectFirst, pageActive)

  return { selectedId, detail, select }
}
