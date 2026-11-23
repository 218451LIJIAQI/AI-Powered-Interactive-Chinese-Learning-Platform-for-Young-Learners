/** Fits a UTF-16 length limit (including Java @Size fields) without splitting a character's surrogate pair. */
export function truncateText(value: string, maxLength: number): string {
  if (value.length <= maxLength) return value
  return value.slice(0, Math.max(0, maxLength)).replace(/[\uD800-\uDBFF]$/u, '')
}
