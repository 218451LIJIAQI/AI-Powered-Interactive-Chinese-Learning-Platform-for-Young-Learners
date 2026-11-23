import { computed } from 'vue'

import { publicClassesApi } from '@/api/classes'
import { adminLearningApi } from '@/api/learn'
import { useResource } from '@/composables/useResource'

/** A Learn path unit a class can be recommended after; `title` is null while the path is not known. */
export interface LearnUnitOption {
  readonly number: number
  readonly title: string | null
}

/** The Learn path units the server accepts for "best after" (1 to 8). */
const LEARN_PATH_UNITS = [1, 2, 3, 4, 5, 6, 7, 8] as const

/**
 * The choices of a class's details form: the class themes and the Learn path units ("Unit 3 · First sentences"),
 * loaded when the form opens. Successful results are kept; a later opening retries any missing list.
 */
export function useClassFormOptions() {
  const topics = useResource(publicClassesApi.topics, false)
  const path = useResource(adminLearningApi.path, false)

  const themes = computed(() => topics.data.value ?? [])
  const learnUnits = computed<LearnUnitOption[]>(() => {
    const titles = new Map(
      (path.data.value?.levels ?? []).flatMap((level) => level.units).map((unit) => [unit.number, unit.title]),
    )
    return LEARN_PATH_UNITS.map((number) => ({ number, title: titles.get(number) ?? null }))
  })

  function load(): void {
    if (!topics.data.value && !topics.loading.value) void topics.reload()
    if (!path.data.value && !path.loading.value) void path.reload()
  }

  return {
    themes,
    topicsError: topics.error,
    reloadTopics: topics.reload,
    learnUnits,
    load,
  }
}
