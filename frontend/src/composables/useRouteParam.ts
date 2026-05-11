import { computed, type ComputedRef } from 'vue'
import { useRoute } from 'vue-router'

/**
 * Reads a route parameter as a string, transparently flattening the
 * `string | string[]` union vue-router exposes (the array form is only used by
 * catch-all routes, but our types still surface it).
 *
 * Returns the empty string when the param is missing or empty.
 */
export function useStringRouteParam(name: string): ComputedRef<string> {
  const route = useRoute()
  return computed(() => {
    const raw = route.params[name]
    if (Array.isArray(raw)) {
      return raw[0] ?? ''
    }
    return raw ?? ''
  })
}

/**
 * Reads a numeric route parameter and validates that it is a finite number.
 *
 * Returns `NaN` if the parameter is missing or non-numeric, which callers can
 * test with `Number.isFinite(...)` before issuing an API request — preventing
 * accidental `GET /students/lessons/NaN` calls that always 400.
 */
export function useNumericRouteParam(name: string): ComputedRef<number> {
  const stringValue = useStringRouteParam(name)
  return computed(() => {
    const trimmed = stringValue.value.trim()
    if (!trimmed) return Number.NaN
    const parsed = Number(trimmed)
    return Number.isFinite(parsed) ? parsed : Number.NaN
  })
}
