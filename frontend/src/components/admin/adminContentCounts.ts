import { readonly, ref } from 'vue'

import { adminClassesApi } from '@/api/adminClasses'
import { AUTH_INVALIDATED_EVENT, getAuthSessionVersion } from '@/api/http'
import { adminLearningApi } from '@/api/learn'

interface ContentCounts {
  classes: number | null
  units: number | null
  practice: number | null
}

const EMPTY_COUNTS: ContentCounts = { classes: null, units: null, practice: null }

/** One shared copy, so the tabs keep their numbers from page to page and follow a change on any of them. */
const counts = ref<ContentCounts>({ ...EMPTY_COUNTS })
export const contentCounts = readonly(counts)
let requestVersion = 0
let countsSessionVersion = getAuthSessionVersion()
const syncedVersion: Record<keyof ContentCounts, number> = { classes: 0, units: 0, practice: 0 }

function resetCounts(sessionVersion: number): void {
  countsSessionVersion = sessionVersion
  counts.value = { ...EMPTY_COUNTS }
  syncedVersion.classes = 0
  syncedVersion.units = 0
  syncedVersion.practice = 0
}

/** Clears cached counts from a previous login synchronously before a tab strip renders. */
export function ensureCurrentCountSession(): void {
  const sessionVersion = getAuthSessionVersion()
  if (sessionVersion !== countsSessionVersion) resetCounts(sessionVersion)
}

if (typeof window !== 'undefined') {
  window.addEventListener(AUTH_INVALIDATED_EVENT, () => {
    requestVersion += 1
    resetCounts(getAuthSessionVersion())
  })
}

async function count(load: () => Promise<number | undefined>): Promise<number | null> {
  try {
    return (await load()) ?? null
  } catch {
    // The tab then shows no count; each page reports its own loading errors.
    return null
  }
}

export function syncContentCount(key: keyof ContentCounts, value: number): void {
  ensureCurrentCountSession()
  syncedVersion[key] += 1
  counts.value = { ...counts.value, [key]: value }
}

/** The counts on the admin Content tabs: classes and lessons, Learn path units, practice activities. */
export async function reloadContentCounts(options: { keepUnits?: boolean; skipUnits?: boolean } = {}): Promise<void> {
  const sessionVersion = getAuthSessionVersion()
  const sameSession = sessionVersion === countsSessionVersion
  if (!sameSession) resetCounts(sessionVersion)
  const shouldLoadUnits = !options.skipUnits && !(options.keepUnits && sameSession && counts.value.units !== null)
  const version = ++requestVersion
  const startSynced = { ...syncedVersion }
  const [classes, units, practice] = await Promise.all([
    count(async () => (await adminClassesApi.list({ page: 0, size: 1 })).totalItems),
    shouldLoadUnits ? count(async () => (await adminLearningApi.path()).unitCount) : Promise.resolve(null),
    count(async () => (await adminLearningApi.practiceSets({ page: 0, size: 1 })).totalItems),
  ])
  if (version === requestVersion && sessionVersion === getAuthSessionVersion()) {
    countsSessionVersion = sessionVersion
    counts.value = {
      classes: syncedVersion.classes === startSynced.classes ? classes : counts.value.classes,
      units: shouldLoadUnits && syncedVersion.units === startSynced.units ? units : counts.value.units,
      practice: syncedVersion.practice === startSynced.practice ? practice : counts.value.practice,
    }
  }
}
