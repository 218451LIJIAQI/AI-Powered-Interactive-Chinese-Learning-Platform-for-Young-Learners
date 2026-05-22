export function formatTeacherFeedbackType(value: string | null | undefined) {
  if (!value?.trim()) {
    return 'General'
  }

  return value
    .trim()
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}
