import { parseRecordId } from '@/utils/recordId'

// The paged API accepts a signed Java int and reports the total even for an out-of-range page.
const MAX_PAGE_INDEX = 2_147_483_647

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
