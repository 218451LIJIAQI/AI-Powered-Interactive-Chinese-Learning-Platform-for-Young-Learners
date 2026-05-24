/**
 * Translates the backend {@code TeacherFeedbackType} enum constant
 * (e.g. {@code "QUIZ_ATTEMPT"}) into a Title-Case label
 * (e.g. {@code "Quiz Attempt"}) for the dashboard chips and history
 * lists. Returns {@code "General"} for null/blank to avoid empty
 * UI labels.
 */
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
