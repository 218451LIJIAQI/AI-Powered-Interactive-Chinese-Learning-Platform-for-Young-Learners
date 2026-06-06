import { describe, expect, it } from 'vitest'

import { formatTeacherFeedbackType } from './teacherFeedback'

describe('formatTeacherFeedbackType', () => {
  it.each([
    ['QUIZ_ATTEMPT', 'Quiz Attempt'],
    [' lesson_note ', 'Lesson Note'],
    [null, 'General'],
    ['   ', 'General'],
  ])('formats %s as %s', (input, output) => {
    expect(formatTeacherFeedbackType(input)).toBe(output)
  })
})
