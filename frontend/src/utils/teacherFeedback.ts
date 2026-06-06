const WORD_START_PATTERN = /(^|\s)\w/g

export function formatTeacherFeedbackType(value: string | null | undefined): string {
  if (!value?.trim()) {
    return 'General'
  }

  return value
    .trim()
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(WORD_START_PATTERN, (match) => match.toUpperCase())
}
