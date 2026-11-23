import { describe, expect, it } from 'vitest'

import { expectRequests, installAxiosCapture, restoreAxiosAdapterAfterEach } from '@/test/axiosCapture'
import type { HintReport, LevelCheckQuestionPayload, PracticeSetPayload } from '@/types/learn'

import { adminLearningApi, hintApi, learnApi, levelCheckApi, mistakesApi, practiceApi, xpApi } from './learn'

restoreAxiosAdapterAfterEach()

const RUN = { runStartedAt: '2026-11-15T09:00:00', runSignature: 'signed-run' }

const QUESTION: LevelCheckQuestionPayload = {
  prompt: 'What does 你好 mean?',
  hanzi: '你好',
  pinyin: 'nǐ hǎo',
  audioText: null,
  skill: 'VOCABULARY',
  difficulty: 'EASY',
  level: 'BEGINNER_1',
  status: 'DRAFT',
  options: [
    { text: 'Hello', correct: true, explanation: null },
    { text: 'Goodbye', correct: false, explanation: null },
  ],
}

const HINT_REPORT: HintReport = {
  reasonCategory: 'INCORRECT_CONTENT',
  description: null,
  promptSnapshot: 'LISTENING #14 / 2 · /student/practice/listening?set=14',
  aiResponseSnapshot: 'It isn’t answer 2: 你好 (nǐ hǎo).',
}

const SET: PracticeSetPayload = {
  title: 'Greetings',
  stage: null,
  hskLevel: 1,
  items: [],
  strokeCount: null,
  characterStrokes: [],
}

describe('Learn API', () => {
  it('maps the Learn path, Practice, hint, Mistakes, XP and level check endpoints', async () => {
    await expectRequests([
      [() => learnApi.path(), 'GET /students/learn/path'],
      [() => learnApi.guidebook(2), 'GET /students/learn/units/2/guidebook'],
      [() => learnApi.lesson(5), 'GET /students/learn/lessons/5'],
      [() => learnApi.checkLesson(5, { questionId: 1, answer: '你好' }), 'POST /students/learn/lessons/5/check'],
      [() => learnApi.completeLesson(5, []), 'POST /students/learn/lessons/5/complete'],
      [() => learnApi.hint(5, 1, RUN), 'POST /students/learn/lessons/5/hint'],
      [() => practiceApi.sets('DRAG_DROP'), 'GET /students/practice/drag-drop/sets'],
      [() => practiceApi.set('FLASHCARDS', 3), 'GET /students/practice/flashcards/sets/3'],
      [
        () => practiceApi.check('LISTENING', 3, { itemKey: 'q1', answer: 'A', confidence: 'SURE' }),
        'POST /students/practice/listening/sets/3/check',
      ],
      [() => practiceApi.complete('TRACING', 3, [], 12), 'POST /students/practice/tracing/sets/3/complete'],
      [() => practiceApi.hint('MATCHING', 3, 'w1', RUN), 'POST /students/practice/matching/sets/3/hint'],
      [() => practiceApi.candidates(3, 'w1', 'nihao'), 'POST /students/practice/typing/sets/3/candidates'],
      [() => hintApi.settings(), 'GET /students/hint-settings'],
      [() => hintApi.setMode('OFF'), 'PUT /students/hint-settings'],
      [() => hintApi.report(HINT_REPORT), 'POST /ai/reports'],
      [() => mistakesApi.list(null, 20), 'GET /students/mistakes'],
      [() => mistakesApi.review(), 'GET /students/mistakes/review'],
      [() => mistakesApi.check({ questionId: 4, answer: null }), 'POST /students/mistakes/review/check'],
      [() => mistakesApi.complete([]), 'POST /students/mistakes/review/complete'],
      [() => mistakesApi.hint(4, null), 'POST /students/mistakes/review/hint'],
      [() => xpApi.today(), 'GET /students/today'],
      [() => xpApi.dailyGoal(), 'GET /students/daily-goal'],
      [() => xpApi.setDailyGoal(20), 'PUT /students/daily-goal'],
      [() => xpApi.progress(), 'GET /students/progress/overview'],
      [() => xpApi.progressSkills(), 'GET /students/progress/skills'],
      [() => xpApi.progressAnswers(), 'GET /students/progress/answers'],
      [() => levelCheckApi.next(null, []), 'POST /students/level-check/next'],
    ])
  })

  it('asks for an item’s one hint with the run, or nulls without one', async () => {
    const calls = installAxiosCapture()

    await learnApi.hint(5, 11, RUN)
    await mistakesApi.hint(4, null)
    await practiceApi.hint('TYPING', 3, 'w1', RUN)

    expect(calls.map((call) => call.data)).toEqual([
      { questionId: 11, ...RUN },
      { questionId: 4, runStartedAt: null, runSignature: null },
      { itemKey: 'w1', ...RUN },
    ])
  })

  it('reports a hint as an AI answer of the HINT feature, with what it was for and the hint itself', async () => {
    const calls = installAxiosCapture()

    await hintApi.report(HINT_REPORT)

    expect(calls[0]!.data).toEqual({ featureKey: 'HINT', ...HINT_REPORT })
  })

  it('names every Mistakes source and only a chosen Progress month in the query', async () => {
    const calls = installAxiosCapture()

    await mistakesApi.list(null, 20)
    await mistakesApi.list('PRACTICE', 5)
    await xpApi.progress()
    await xpApi.progress('2026-10')

    expect(calls.map((call) => call.params)).toEqual([
      { source: 'all', size: 20 },
      { source: 'PRACTICE', size: 5 },
      {},
      { month: '2026-10' },
    ])
  })

  it('maps the admin Learning path and Practice library, creating without an id and saving with one', async () => {
    await expectRequests([
      [() => adminLearningApi.path(), 'GET /admin/learning-path'],
      [() => adminLearningApi.unit(3), 'GET /admin/learning-path/units/3'],
      [() => adminLearningApi.setUnitPublished(3, true), 'POST /admin/learning-path/units/3/publish'],
      [() => adminLearningApi.setUnitPublished(3, false), 'POST /admin/learning-path/units/3/unpublish'],
      [() => adminLearningApi.questions(0, 20), 'GET /admin/learning-path/level-check-questions'],
      [() => adminLearningApi.question(7), 'GET /admin/learning-path/level-check-questions/7'],
      [() => adminLearningApi.saveQuestion(null, QUESTION), 'POST /admin/learning-path/level-check-questions'],
      [() => adminLearningApi.saveQuestion(7, QUESTION), 'PUT /admin/learning-path/level-check-questions/7'],
      [() => adminLearningApi.publishQuestion(7), 'POST /admin/learning-path/level-check-questions/7/publish'],
      [() => adminLearningApi.unpublishQuestion(7), 'POST /admin/learning-path/level-check-questions/7/unpublish'],
      [() => adminLearningApi.practiceSets({ page: 0, size: 10 }), 'GET /admin/practice-sets'],
      [() => adminLearningApi.practiceSet('DRAG_DROP', 4), 'GET /admin/practice-sets/drag-drop/4'],
      [() => adminLearningApi.savePracticeSet('DRAG_DROP', null, SET), 'POST /admin/practice-sets/drag-drop'],
      [() => adminLearningApi.savePracticeSet('DRAG_DROP', 4, SET), 'PUT /admin/practice-sets/drag-drop/4'],
      [() => adminLearningApi.changePracticeSet('TYPING', 4, 'archive'), 'POST /admin/practice-sets/typing/4/archive'],
    ])
  })

  it('filters the Practice library by the type slug and leaves out the empty filters', async () => {
    const calls = installAxiosCapture()

    await adminLearningApi.practiceSets({ q: '', type: 'DRAG_DROP', status: null, page: 1, size: 10 })
    await adminLearningApi.practiceSets({ q: 'food', type: null, status: 'PUBLISHED', page: 0, size: 10 })

    expect(calls.map((call) => call.params)).toEqual([
      { q: undefined, type: 'drag-drop', status: undefined, page: 1, size: 10 },
      { q: 'food', type: undefined, status: 'PUBLISHED', page: 0, size: 10 },
    ])
  })
})
