import { describe, expect, it } from 'vitest'

import {
  adminClassesApi,
  lessonEditorApi,
  publicClassesApi,
  studentClassesApi,
  teacherClassesApi,
  teacherSessionsApi,
  teacherStudentsApi,
} from './classes'
import { installAxiosCapture, restoreAxiosAdapterAfterEach } from '@/test/axiosCapture'

restoreAxiosAdapterAfterEach()

type Case = [run: () => Promise<unknown>, request: string]

async function expectRequests(cases: Case[]) {
  const calls = installAxiosCapture({ items: [] })
  for (const [run] of cases) await run()
  expect(calls.map(({ method, url }) => `${method} ${url}`)).toEqual(cases.map(([, request]) => request))
}

describe('class API wrappers', () => {
  it('maps the catalogue and the student class endpoints', async () => {
    await expectRequests([
      [() => publicClassesApi.search({ keyword: 'tones' }), 'GET /public/classes'],
      [() => publicClassesApi.featured(), 'GET /public/classes/featured'],
      [() => publicClassesApi.detail(7), 'GET /public/classes/7'],
      [() => studentClassesApi.myClasses(), 'GET /students/classes'],
      [() => studentClassesApi.catalogue(), 'GET /students/classes/catalogue'],
      [() => studentClassesApi.join(7), 'POST /students/classes/7/join'],
      [() => studentClassesApi.lesson(11), 'GET /students/classes/lessons/11'],
      [() => studentClassesApi.activity('FLASHCARDS', 3), 'GET /students/classes/activities/flashcards/3'],
      [() => studentClassesApi.completeActivity('TRACING', 4), 'POST /students/classes/activities/tracing/4/complete'],
      [() => studentClassesApi.checkMatching(5, []), 'POST /students/classes/activities/matching/5/check'],
      [() => studentClassesApi.startQuiz(9), 'POST /students/classes/quizzes/9/attempts'],
      [
        () => studentClassesApi.saveAnswer(40, { questionId: 1, optionId: 2 }),
        'PUT /students/classes/quiz-attempts/40/answers',
      ],
      [() => studentClassesApi.submitQuiz(40), 'POST /students/classes/quiz-attempts/40/submit'],
      [() => studentClassesApi.book(2), 'POST /students/classes/live-sessions/2/book'],
      [() => studentClassesApi.joinSession(2), 'POST /students/classes/live-sessions/2/join'],
    ])
  })

  it('maps the teacher and admin endpoints', async () => {
    await expectRequests([
      [() => teacherClassesApi.dashboard(), 'GET /teachers/teaching-dashboard'],
      [() => teacherClassesApi.create({ title: 'New class' }), 'POST /teachers/classes'],
      [() => teacherClassesApi.reorder(7, []), 'PUT /teachers/classes/7/structure'],
      [() => teacherClassesApi.discardDetails(7), 'DELETE /teachers/classes/7/pending-details'],
      [() => teacherClassesApi.addLesson(1, 'Greetings'), 'POST /teachers/units/1/lessons'],
      [
        () => lessonEditorApi.updateActivity(11, 'MATCHING', 5, { type: 'MATCHING', title: 'Match' }),
        'PUT /teachers/lessons/11/activities/matching/5',
      ],
      [() => lessonEditorApi.reorderQuestions(11, 9, [3, 2]), 'PUT /teachers/lessons/11/quizzes/9/question-order'],
      [() => lessonEditorApi.discardRevision(11), 'DELETE /teachers/lessons/11/revision'],
      [() => teacherSessionsApi.saveRecording(2, 'https://example.com/r'), 'PUT /teachers/live-sessions/2/recording'],
      [
        () => teacherStudentsApi.updateFeedback(8, { type: 'LESSON', message: 'Well done.' }),
        'PUT /teachers/feedback/8',
      ],
      [() => adminClassesApi.queue('LESSON'), 'GET /admin/class-reviews'],
      [
        () => adminClassesApi.requestChanges('LESSON', 21, 'Add pinyin.'),
        'POST /admin/class-reviews/lessons/21/request-changes',
      ],
      [() => adminClassesApi.archive('CLASS', 7), 'POST /admin/teacher-classes/classes/7/archive'],
    ])
  })
})
