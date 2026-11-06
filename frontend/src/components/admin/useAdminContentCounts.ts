import { readonly, ref } from 'vue'

import { adminClassesApi } from '@/api/classes'
import { adminLearningApi } from '@/api/learn'
import { shellApi } from '@/api/shell'

interface ContentCounts {
  review: number | null
  classes: number | null
  units: number | null
  practice: number | null
}

/** One shared copy, so the tabs keep their numbers from page to page and follow a change on any of them. */
const counts = ref<ContentCounts>({ review: null, classes: null, units: null, practice: null })

async function count(load: () => Promise<number | undefined>): Promise<number | null> {
  try {
    return (await load()) ?? null
  } catch {
    // The tab then shows no count; each page reports its own loading errors.
    return null
  }
}

/** The counts on the admin Content tabs: items waiting for review, teacher classes and lessons, units, activities. */
export function useAdminContentCounts() {
  /** `withReview` is false on the Review queue page, whose list already says how many wait. */
  async function reloadCounts(withReview = true): Promise<void> {
    const [review, classes, units, practice] = await Promise.all([
      withReview ? count(async () => (await shellApi.navCounts()).reviewQueue) : counts.value.review,
      count(async () => (await adminClassesApi.content({ page: 0, size: 1 })).totalItems),
      count(async () => (await adminLearningApi.path()).unitCount),
      count(
        async () =>
          (await adminLearningApi.practiceSets({ q: '', type: null, status: null, stage: null, page: 0, size: 1 }))
            .totalItems,
      ),
    ])
    counts.value = { review, classes, units, practice }
  }

  return { counts: readonly(counts), reloadCounts }
}
