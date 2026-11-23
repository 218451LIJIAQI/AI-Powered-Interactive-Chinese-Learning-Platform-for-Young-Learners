/** A positive decimal record id from a route or query, representable exactly by the browser. */
export function parseRecordId(value: unknown): number | null {
  if (typeof value !== 'number' && (typeof value !== 'string' || !/^\d+$/.test(value.trim()))) return null
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : null
}

/** All record parameters in the route table are named classId, lessonId, quizId, etc. */
export function hasInvalidRecordId(params: Record<string, unknown>): boolean {
  return Object.entries(params).some(([name, value]) => name.endsWith('Id') && parseRecordId(value) === null)
}
