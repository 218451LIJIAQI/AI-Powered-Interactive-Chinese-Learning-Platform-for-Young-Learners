import { describe, expect, it } from 'vitest'

import { formatTeacherFeedbackType } from './teacherFeedback'

describe('formatTeacherFeedbackType', () => {
  it('formats enum-like backend values into readable labels', () => {
    expect(formatTeacherFeedbackType('QUIZ_ATTEMPT')).toBe('Quiz Attempt')
    expect(formatTeacherFeedbackType(' lesson_note ')).toBe('Lesson Note')
  })

  it('uses a stable fallback for missing feedback types', () => {
    expect(formatTeacherFeedbackType(null)).toBe('General')
    expect(formatTeacherFeedbackType('   ')).toBe('General')
  })
})
