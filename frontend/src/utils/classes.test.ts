import { describe, expect, it } from 'vitest'

import { classesMessages } from '@/plugins/classesMessages'
import { QUIZ_QUESTION_TYPES } from '@/types/api'

import { classInitials, isQuestionType, isWebLink, questionTypeKey } from './classes'

describe('question types', () => {
  it('names every type of the question builder in every language', () => {
    expect(QUIZ_QUESTION_TYPES).toHaveLength(10)
    for (const type of QUIZ_QUESTION_TYPES) {
      expect(questionTypeKey(type)).toBe(`classes.questionType.${type}`)
      for (const messages of Object.values(classesMessages)) {
        expect(messages.questionType[type], type).toEqual(expect.any(String))
      }
    }
  })

  it('reads a type it does not know as multiple choice', () => {
    expect(isQuestionType('FILL_BLANK')).toBe(true)
    expect(isQuestionType('ESSAY')).toBe(false)
    expect(isQuestionType('toString')).toBe(false)
    expect(questionTypeKey('ESSAY')).toBe('classes.questionType.MULTIPLE_CHOICE')
  })
})

describe('class initials', () => {
  it('takes the first two words of the title, leaving out its lower-case small words', () => {
    expect(classInitials('Mandarin for Campus Life')).toBe('MC')
    expect(classInitials('Shopping and Prices')).toBe('SP')
    expect(classInitials('Chinese Through Film')).toBe('CT')
    expect(classInitials('Basic Work Mandarin')).toBe('BW')
    expect(classInitials('  Mandarin   Foundations ')).toBe('MF')
  })

  it('reads a title of one word', () => {
    expect(classInitials('Pinyin')).toBe('P')
    expect(classInitials('汉字入门')).toBe('汉字')
    expect(classInitials('everyday mandarin')).toBe('EM')
    expect(classInitials('')).toBe('')
  })
})

describe('isWebLink', () => {
  it('accepts full http and https links and rejects dangerous or non-web URLs', () => {
    expect(isWebLink('https://example.com/lesson')).toBe(true)
    expect(isWebLink('http://example.com:8080/video?id=1')).toBe(true)

    expect(isWebLink('javascript:alert(1)')).toBe(false)
    expect(isWebLink('JAVASCRIPT:alert(1)')).toBe(false)
    expect(isWebLink('data:text/html,<script>alert(1)</script>')).toBe(false)
    expect(isWebLink('vbscript:msgbox(1)')).toBe(false)
    expect(isWebLink('file:///etc/passwd')).toBe(false)
    expect(isWebLink('//example.com/lesson')).toBe(false)
    expect(isWebLink('/student/classes')).toBe(false)
    expect(isWebLink('')).toBe(false)
    expect(isWebLink('not a url')).toBe(false)
  })
})
