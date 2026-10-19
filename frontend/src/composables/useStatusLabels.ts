import { useI18n } from 'vue-i18n'

import { humanizeEnumValue, statusLabelKey, type StatusLabelGroup } from '@/plugins/statusLabelMessages'

/**
 * Translates API enum values (feedback types, mastery, enrolment / live-class status, notification types, practice
 * submission states, ...) into the current UI language. Unknown values fall back to a readable title case.
 */
export function useStatusLabels() {
  // The bundle is registered globally (plugins/i18n.ts) so this works inside pages that own a local i18n scope.
  const { t } = useI18n({ useScope: 'global' })

  function statusLabel(group: StatusLabelGroup, value: string | null | undefined, fallback = ''): string {
    const key = statusLabelKey(group, value)
    if (key) return t(`statusLabels.${key}`)
    return humanizeEnumValue(value) || fallback
  }

  return { statusLabel }
}
