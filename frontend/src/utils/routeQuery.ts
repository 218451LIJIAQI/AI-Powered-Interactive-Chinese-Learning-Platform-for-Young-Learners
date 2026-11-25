import type { LocationQuery } from 'vue-router'

import { parseRecordId } from '@/utils/recordId'

// The paged API accepts a signed Java int and reports the total even for an out-of-range page.
const MAX_PAGE_INDEX = 2_147_483_647

/** The text of a route query value (`?token=…`): trimmed, the first non-empty one of a repeated key, or ''. */
export function firstQueryText(value: unknown): string {
  if (typeof value === 'string') {
    return value.trim()
  }
  if (Array.isArray(value)) {
    const firstText = value.find((entry): entry is string => typeof entry === 'string' && entry.trim().length > 0)
    return firstText?.trim() ?? ''
  }
  return ''
}

/** A single query value that exactly matches an available filter option. */
export function queryOption<T extends string>(value: unknown, options: readonly T[]): T | undefined {
  return options.find((option) => option === value)
}

/** Drop consumed link parameters while keeping every other filter or redirect in the address. */
export function withoutQueryKeys(query: LocationQuery, ...keys: string[]): LocationQuery {
  const remaining = { ...query }
  for (const key of keys) delete remaining[key]
  return remaining
}

/*
 * A paged list keeps its page in the address beside its filters, so that a reload or a link back keeps it. The
 * address counts pages from 1 and leaves the first one out (`?page=3` is the third page); the list counts from 0.
 */

/** The 0-based page an address's `page` names; the first page when it names none. */
export function pageFromQuery(value: unknown): number {
  return Math.min((parseRecordId(value) ?? 1) - 1, MAX_PAGE_INDEX)
}

/** The address's `page` for a 0-based page: none for the first page. */
export function pageQuery(page: number): string | undefined {
  return page > 0 ? String(page + 1) : undefined
}
