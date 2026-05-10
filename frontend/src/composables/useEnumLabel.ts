import { useI18n } from 'vue-i18n'

/**
 * Format an UPPER_SNAKE_CASE backend enum value into Title Case as a fallback,
 * e.g. "ACTION_TAKEN" -> "Action Taken".
 */
export function formatEnumValue(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

/**
 * Vue composable that returns a small helper for translating backend enum values
 * with a friendly multi-tier fallback chain:
 *
 * 1. `enums.{group}.{value}` for context-specific labels in the unified enums namespace.
 * 2. `{group}.{value}` for callers that want to reuse existing namespaces such as
 *    `notification.status.UNREAD` or `notification.type.SUPPORT_TICKET`.
 * 3. `analytics.enums.{value}` for the shared analytics label set.
 * 4. Title-cased version of the raw value as a last-resort fallback.
 *
 * This keeps tags, columns, and detail panels free from raw enum values while
 * still gracefully handling unknown values without throwing.
 */
export function useEnumLabel() {
  const { t, te } = useI18n()

  return function translateEnum(group: string, value: string | null | undefined): string {
    if (value === null || value === undefined) {
      return ''
    }

    const trimmed = String(value).trim()
    if (trimmed.length === 0) {
      return ''
    }

    const namespacedKey = `enums.${group}.${trimmed}`
    if (te(namespacedKey)) {
      return t(namespacedKey)
    }

    const groupKey = `${group}.${trimmed}`
    if (te(groupKey)) {
      return t(groupKey)
    }

    const sharedKey = `analytics.enums.${trimmed}`
    if (te(sharedKey)) {
      return t(sharedKey)
    }

    return formatEnumValue(trimmed)
  }
}
