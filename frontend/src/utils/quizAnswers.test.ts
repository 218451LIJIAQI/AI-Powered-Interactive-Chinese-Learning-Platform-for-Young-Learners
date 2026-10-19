import { describe, expect, it } from 'vitest'

import type { QuizAttemptResultQuestionResponse, StudentQuizQuestionResponse } from '@/types/api'

import {
  buildQuizSubmission,
  canRequestHint,
  correctAnswerLabel,
  countAnsweredQuestions,
  firstUnansweredIndex,
  isQuestionAnswered,
  isTextAnswerType,
  selectedAnswerLabel,
  wrongResultAnswers,
} from './quizAnswers'

function question(overrides: Partial<StudentQuizQuestionResponse> = {}): StudentQuizQuestionResponse {
  return {
    id: 1,
    questionType: 'MULTIPLE_CHOICE',
    questionText: 'Which word means "teacher"?',
    points: 2,
    displayOrder: 1,
    hintAvailable: false,
    options: [
      { id: 11, optionText: '老师', displayOrder: 1 },
      { id: 12, optionText: '学生', displayOrder: 2 },
    ],
    ...overrides,
  }
}

function resultQuestion(overrides: Partial<QuizAttemptResultQuestionResponse> = {}): QuizAttemptResultQuestionResponse {
  return {
    questionId: 1,
    questionType: 'MULTIPLE_CHOICE',
    questionText: 'Which word means "teacher"?',
    studentAnswer: '学生',
    correctAnswer: '老师',
    correct: false,
    points: 2,
    explanation: null,
    selectedOptionId: 12,
    correctOptionId: 11,
    hintUsed: false,
    options: [
      { id: 11, optionText: '老师', displayOrder: 1 },
      { id: 12, optionText: '学生', displayOrder: 2 },
    ],
    ...overrides,
  }
}

const optionQuestion = question()
const fillBlank = question({ id: 2, questionType: 'FILL_BLANK', options: [] })
const rearrange = question({ id: 3, questionType: 'REARRANGE_SENTENCE', options: [] })

describe('isTextAnswerType', () => {
  it('recognises the typed-answer question types', () => {
    expect(isTextAnswerType('FILL_BLANK')).toBe(true)
    expect(isTextAnswerType('REARRANGE_SENTENCE')).toBe(true)
    expect(isTextAnswerType(' fill_blank ')).toBe(true)
  })

  it('treats every other type as option-based', () => {
    for (const type of ['MULTIPLE_CHOICE', 'TRUE_FALSE', 'CHOOSE_PINYIN', 'CHOOSE_TONE', 'LISTEN_CHOOSE']) {
      expect(isTextAnswerType(type)).toBe(false)
    }
    expect(isTextAnswerType(null)).toBe(false)
    expect(isTextAnswerType(undefined)).toBe(false)
  })
})

describe('isQuestionAnswered', () => {
  it('needs one of the listed option ids for option questions', () => {
    expect(isQuestionAnswered(optionQuestion, 11)).toBe(true)
    expect(isQuestionAnswered(optionQuestion, 99)).toBe(false)
    expect(isQuestionAnswered(optionQuestion, '老师')).toBe(false)
    expect(isQuestionAnswered(optionQuestion, undefined)).toBe(false)
  })

  it('needs non-blank text for text questions', () => {
    expect(isQuestionAnswered(fillBlank, '好')).toBe(true)
    expect(isQuestionAnswered(fillBlank, '   ')).toBe(false)
    expect(isQuestionAnswered(rearrange, 7)).toBe(false)
    expect(isQuestionAnswered(rearrange, null)).toBe(false)
  })
})

describe('countAnsweredQuestions / firstUnansweredIndex', () => {
  const questions = [optionQuestion, fillBlank, rearrange]

  it('counts only valid answers', () => {
    expect(countAnsweredQuestions(questions, { 1: 11, 2: '  ', 3: '我叫小明' })).toBe(2)
  })

  it('finds the first question still missing an answer', () => {
    expect(firstUnansweredIndex(questions, { 1: 11, 3: '我叫小明' })).toBe(1)
    expect(firstUnansweredIndex(questions, { 1: 11, 2: '好', 3: '我叫小明' })).toBe(-1)
  })
})

describe('buildQuizSubmission', () => {
  it('sends optionId for option types and trimmed answerText for text types', () => {
    expect(buildQuizSubmission([optionQuestion, fillBlank, rearrange], { 1: 12, 2: '  好 ', 3: '我 叫 小明' })).toEqual(
      [
        { questionId: 1, optionId: 12 },
        { questionId: 2, answerText: '好' },
        { questionId: 3, answerText: '我 叫 小明' },
      ],
    )
  })

  it('adds hintUsed only for questions where the hint was opened', () => {
    expect(buildQuizSubmission([optionQuestion, fillBlank], { 1: 11, 2: '好' }, { 1: true, 2: false })).toEqual([
      { questionId: 1, optionId: 11, hintUsed: true },
      { questionId: 2, answerText: '好' },
    ])
  })

  it('never sends an option id that is not listed or text for an option question', () => {
    expect(buildQuizSubmission([optionQuestion], { 1: 99 })).toEqual([{ questionId: 1 }])
    expect(buildQuizSubmission([optionQuestion], { 1: '老师' })).toEqual([{ questionId: 1 }])
    expect(buildQuizSubmission([fillBlank], { 2: '   ' })).toEqual([{ questionId: 2 }])
  })
})

describe('canRequestHint', () => {
  it('allows hints only when available and the quiz is not independent', () => {
    expect(canRequestHint({ hintAvailable: true }, 'GUIDED')).toBe(true)
    expect(canRequestHint({ hintAvailable: true }, 'SCAFFOLDED')).toBe(true)
    expect(canRequestHint({ hintAvailable: true }, null)).toBe(true)
    expect(canRequestHint({ hintAvailable: true }, 'INDEPENDENT')).toBe(false)
    expect(canRequestHint({ hintAvailable: false }, 'GUIDED')).toBe(false)
  })
})

describe('result labels', () => {
  it('uses the option text for option questions', () => {
    const answer = resultQuestion({ studentAnswer: 'stale', correctAnswer: 'stale' })
    expect(selectedAnswerLabel(answer)).toBe('学生')
    expect(correctAnswerLabel(answer)).toBe('老师')
  })

  it('falls back to the stored answer strings for text questions', () => {
    const answer = resultQuestion({
      questionType: 'FILL_BLANK',
      selectedOptionId: null,
      correctOptionId: null,
      options: [],
      studentAnswer: ' 好 ',
      correctAnswer: '好',
      correct: true,
    })
    expect(selectedAnswerLabel(answer)).toBe('好')
    expect(correctAnswerLabel(answer)).toBe('好')
  })

  it('falls back to the stored strings when an option id is not in the option list', () => {
    const answer = resultQuestion({ selectedOptionId: 99, correctOptionId: null, studentAnswer: '学生' })
    expect(selectedAnswerLabel(answer)).toBe('学生')
    expect(correctAnswerLabel(answer)).toBe('老师')
  })

  it('lists the wrong answers of an attempt', () => {
    const right = resultQuestion({ questionId: 2, correct: true })
    const wrong = resultQuestion({ questionId: 3 })
    expect(wrongResultAnswers({ answers: [right, wrong] })).toEqual([wrong])
    expect(wrongResultAnswers(null)).toEqual([])
  })
})
