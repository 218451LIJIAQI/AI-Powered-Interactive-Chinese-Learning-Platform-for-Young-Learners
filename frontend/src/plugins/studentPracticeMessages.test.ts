import { describe, expect, it } from 'vitest'
import { studentPracticeMessages } from './studentPracticeMessages'

type Key = keyof typeof studentPracticeMessages.en

function placeholders(message: string): string[] {
  return Array.from(message.matchAll(/\{(\w+)\}/g), (match) => match[1] ?? '').sort()
}

describe('student practice messages', () => {
  it('keeps every locale complete', () => {
    const englishKeys = Object.keys(studentPracticeMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(studentPracticeMessages[locale]).sort()).toEqual(englishKeys)
      for (const key of englishKeys) expect(studentPracticeMessages[locale][key as Key].trim()).not.toBe('')
    }
  })

  it('uses the same placeholders in every locale', () => {
    for (const key of Object.keys(studentPracticeMessages.en) as Key[]) {
      const expected = placeholders(studentPracticeMessages.en[key])
      expect(placeholders(studentPracticeMessages.ms[key]), `ms.${key}`).toEqual(expected)
      expect(placeholders(studentPracticeMessages['zh-CN'][key]), `zh-CN.${key}`).toEqual(expected)
    }
  })

  it('states that tracing is practice only and never shows a practice time sum', () => {
    expect(studentPracticeMessages.en.tracingPracticeOnly).toMatch(/not graded/)
    expect(studentPracticeMessages.ms.tracingPracticeOnly).toMatch(/tidak dinilai/)
    expect(studentPracticeMessages['zh-CN'].tracingPracticeOnly).toMatch(/不评分/)
    expect(Object.keys(studentPracticeMessages.en)).not.toContain('practiceTime')
  })
})
