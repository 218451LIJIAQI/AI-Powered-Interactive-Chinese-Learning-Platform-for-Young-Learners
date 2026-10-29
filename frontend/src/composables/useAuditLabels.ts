import { useStatusLabels } from '@/composables/useStatusLabels'
import type { AuditCategory } from '@/types/ops'
import { auditActionKey, auditCategoryKey } from '@/utils/auditActions'

/**
 * Names of audit actions and categories. `t` is the page's translate function, whose scope mounts
 * `auditActionMessages` under `auditActions`; actions other modules log use the shared status labels, and a code
 * nobody names reads in title case.
 */
export function useAuditLabels(t: (key: string) => string) {
  const { statusLabel } = useStatusLabels()

  function actionLabel(actionType: string): string {
    const key = auditActionKey(actionType)
    return key ? t(key) : statusLabel('auditAction', actionType)
  }

  function categoryLabel(category: AuditCategory): string {
    return t(auditCategoryKey(category))
  }

  return { actionLabel, categoryLabel }
}
