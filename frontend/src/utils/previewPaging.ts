import type { PageResponse } from '@/types/api'

/** Pages already filtered and sorted preview fixtures the way the server does (0-based page, clamped to the last). */
export function pagePreviewItems<T>(items: readonly T[], page: number, size: number): PageResponse<T> {
  const safeSize = Math.max(1, Math.floor(size))
  const totalPages = Math.max(1, Math.ceil(items.length / safeSize))
  const safePage = Math.min(Math.max(0, Math.floor(page)), totalPages - 1)
  return {
    items: items.slice(safePage * safeSize, safePage * safeSize + safeSize),
    page: safePage,
    size: safeSize,
    totalItems: items.length,
    totalPages,
  }
}
