import type { LocationQuery } from 'vue-router'

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
