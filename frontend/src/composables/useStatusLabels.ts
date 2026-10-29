import { useI18n } from 'vue-i18n'

import { humanizeEnumValue, statusLabelKey, type StatusLabelGroup } from '@/plugins/statusLabelMessages'

/**
 * Translates API enum values (lesson mastery, actions of older audit entries) into the current UI language. Unknown
 * values fall back to a readable title case.
 */
export function useStatusLabels() {
  // The bundle is registered globally (plugins/i18n.ts) so this works inside pages that own a local i18n scope.
  const { t } = useI18n({ useScope: 'global' })

  function statusLabel(group: StatusLabelGroup, value: string | null | undefined): string {
    const key = statusLabelKey(group, value)
    return key ? t(`statusLabels.${key}`) : humanizeEnumValue(value)
  }

  return { statusLabel }
}
