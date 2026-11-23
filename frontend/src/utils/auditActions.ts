/**
 * How the admin audit log shows an entry: the action's name (full keys of `auditActionMessages`, namespace
 * `auditActions`), one for every action the backend writes or wrote, and its category label. The log keeps what others
 * can notice (A5.08): publish, unpublish, archive, restore and delete, new classes and practice activities, edits of
 * published content, accounts, AI settings and report decisions; edits of drafts are not logged.
 */
import { auditActionMessages } from '@/plugins/auditActionMessages'
import type { AuditCategory } from '@/types/ops'

const WORD_START_PATTERN = /(^|\s)\w/g

/** `SOMETHING_NEW` reads "Something New". */
function inTitleCase(code: string): string {
  return code
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(WORD_START_PATTERN, (match) => match.toUpperCase())
}

/** The full key of the action's name, or null when this bundle does not name it. */
export function auditActionKey(actionType: string): string | null {
  return Object.hasOwn(auditActionMessages.en.action, actionType) ? `auditActions.action.${actionType}` : null
}

/**
 * The name of an audit action. `t` is the page's translate function, whose scope mounts `auditActionMessages` under
 * `auditActions`; a code the bundle does not name (an entry of an action the platform no longer has) reads in title
 * case.
 */
export function auditActionLabel(t: (key: string) => string, actionType: string): string {
  const key = auditActionKey(actionType)
  return key ? t(key) : inTitleCase(actionType)
}

/** The name of an audit category, with the same `t`. */
export function auditCategoryLabel(t: (key: string) => string, category: AuditCategory): string {
  return t(`auditActions.category.${category}`)
}
