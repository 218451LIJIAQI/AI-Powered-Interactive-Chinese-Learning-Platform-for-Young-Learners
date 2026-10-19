import type { AuditLogQuery } from '@/api/services'

/** Admin audit-log filter form state (D16). Dates are `yyyy-MM-dd` from `<input type="date">`. */
export interface AuditLogFilterForm {
  from: string
  to: string
  actionType: string
  /** Actor email fragment, or a numeric user id. */
  actor: string
}

const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/
const USER_ID_PATTERN = /^\d+$/

function isoDate(value: string): string | undefined {
  const trimmed = value.trim()
  return ISO_DATE_PATTERN.test(trimmed) ? trimmed : undefined
}

/** `from` after `to` is rejected by the backend (400), so the form checks it first. */
export function isValidAuditDateRange(form: Pick<AuditLogFilterForm, 'from' | 'to'>): boolean {
  const from = isoDate(form.from)
  const to = isoDate(form.to)
  return !from || !to || from <= to
}

/** Maps the filter form onto the server-side query; a numeric actor is sent as `actorId`, text as `actor`. */
export function auditLogQuery(form: AuditLogFilterForm, page: number, size: number): AuditLogQuery {
  const actor = form.actor.trim()
  const actionType = form.actionType.trim().toUpperCase().replace(/\s+/g, '_')
  const query: AuditLogQuery = { page: Math.max(0, page), size }

  const from = isoDate(form.from)
  const to = isoDate(form.to)
  if (from) query.from = from
  if (to) query.to = to
  if (actionType) query.actionType = actionType
  if (actor) {
    if (USER_ID_PATTERN.test(actor)) query.actorId = Number(actor)
    else query.actor = actor
  }
  return query
}
