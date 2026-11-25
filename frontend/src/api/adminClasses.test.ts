import { describe, expect, it } from 'vitest'

import { expectRequests, installAxiosCapture, restoreAxiosAdapterAfterEach } from '@/test/axiosCapture'

import { adminClassesApi, adminQuizDraftApi, lessonEditorApi } from './adminClasses'

restoreAxiosAdapterAfterEach()

const DETAILS = { title: 'Hawker Food Mandarin', description: null, categoryId: 3, bestAfterUnit: 3 }
const QUESTION = { questionType: 'MULTIPLE_CHOICE' as const, questionText: 'What does 茶 mean?', points: 2 }

describe('admin class authoring API', () => {
  it('maps the classes list, the class page, its units and lessons', async () => {
    await expectRequests([
      [() => adminClassesApi.list({ keyword: 'tea', type: 'LESSON', status: 'DRAFT', page: 1 }), 'GET /admin/classes'],
      [() => adminClassesApi.create(DETAILS), 'POST /admin/classes'],
      [() => adminClassesApi.classPage(7), 'GET /admin/classes/7'],
      [() => adminClassesApi.saveDetails(7, DETAILS), 'PUT /admin/classes/7'],
      [() => adminClassesApi.publish(7), 'POST /admin/classes/7/publish'],
      [() => adminClassesApi.archive(7), 'POST /admin/classes/7/archive'],
      [() => adminClassesApi.restore(7), 'POST /admin/classes/7/restore'],
      [() => adminClassesApi.remove(7), 'DELETE /admin/classes/7'],
      [() => adminClassesApi.addUnit(7, 'Unit 1: At the Hawker Stall'), 'POST /admin/classes/7/units'],
      [() => adminClassesApi.renameUnit(70, 'Unit 1: Ordering'), 'PUT /admin/units/70'],
      [() => adminClassesApi.addLesson(70, 'At the Hawker Stall'), 'POST /admin/units/70/lessons'],
    ])
  })

  it('maps the lesson editor, its publishing and the AI quiz draft', async () => {
    await expectRequests([
      [() => lessonEditorApi.get(11), 'GET /admin/lessons/11'],
      [
        () => lessonEditorApi.saveContent(11, { title: 'T', unitId: 70, keyPhrases: [] }),
        'PUT /admin/lessons/11/content',
      ],
      [
        () =>
          lessonEditorApi.saveVideo(11, {
            title: 'Campus tour',
            file: new File(['v'], 'tour.mp4', { type: 'video/mp4' }),
            durationSeconds: 95,
          }),
        'POST /admin/lessons/11/video',
      ],
      [() => lessonEditorApi.removeVideo(11), 'DELETE /admin/lessons/11/video'],
      [() => lessonEditorApi.addActivity(11, { type: 'TRACING', title: 'Trace' }), 'POST /admin/lessons/11/activities'],
      [
        () => lessonEditorApi.updateActivity(11, 'MATCHING', 5, { type: 'MATCHING', title: 'Match' }),
        'PUT /admin/lessons/11/activities/matching/5',
      ],
      [() => lessonEditorApi.addQuiz(11, { title: 'T', hintsAllowed: false }), 'POST /admin/lessons/11/quizzes'],
      [() => lessonEditorApi.updateQuiz(11, 3, { hintsAllowed: true }), 'PUT /admin/lessons/11/quizzes/3'],
      [() => lessonEditorApi.addQuestion(11, 3, QUESTION), 'POST /admin/lessons/11/quizzes/3/questions'],
      [() => lessonEditorApi.updateQuestion(11, 30, QUESTION), 'PUT /admin/lessons/11/questions/30'],
      [() => lessonEditorApi.deleteQuestion(11, 30), 'DELETE /admin/lessons/11/questions/30'],
      [() => lessonEditorApi.keepQuestion(11, 30), 'POST /admin/lessons/11/questions/30/keep'],
      [() => lessonEditorApi.publish(11), 'POST /admin/lessons/11/publish'],
      [() => lessonEditorApi.archive(11), 'POST /admin/lessons/11/archive'],
      [() => lessonEditorApi.restore(11), 'POST /admin/lessons/11/restore'],
      [() => lessonEditorApi.discardChanges(11), 'DELETE /admin/lessons/11/revision'],
      [() => adminQuizDraftApi.availability(), 'GET /admin/ai/quiz-draft/availability'],
      [
        () =>
          adminQuizDraftApi.draft({
            lessonId: 11,
            topic: 'places on campus',
            level: 'HSK 1',
            numberOfQuestions: 1,
            languagePreference: 'en',
          }),
        'POST /admin/ai/quiz-draft',
      ],
    ])
  })

  it('sends the list filters as query parameters, and a question’s Undo place and AI draft mark in its body', async () => {
    const calls = installAxiosCapture()
    const videoFile = new File(['v'], 'tour.mp4', { type: 'video/mp4' })

    await adminClassesApi.list({ keyword: 'tea', type: 'CLASS', status: 'PUBLISHED', page: 2, size: 10 })
    await lessonEditorApi.addQuestion(11, 3, { ...QUESTION, position: 5, aiDraft: true })
    await lessonEditorApi.saveVideo(11, { title: 'Campus tour', file: videoFile, durationSeconds: 95 })

    expect(calls[0]?.params).toEqual({ keyword: 'tea', type: 'CLASS', status: 'PUBLISHED', page: 2, size: 10 })
    expect(calls[1]?.data).toEqual({ ...QUESTION, position: 5, aiDraft: true })
    const form = calls[2]?.data as FormData
    expect(form).toBeInstanceOf(FormData)
    expect(form.get('title')).toBe('Campus tour')
    expect(form.get('file')).toBe(videoFile)
    expect(form.get('durationSeconds')).toBe('95')
  })

  it('forwards the 30-minute timeout, AbortSignal and omitted null duration on saveVideo, and 45s timeout on quiz drafts', async () => {
    const controller = new AbortController()
    const calls = installAxiosCapture()

    await lessonEditorApi.saveVideo(
      11,
      { title: 'Short clip', file: new File(['v'], 'clip.mp4', { type: 'video/mp4' }), durationSeconds: null },
      controller.signal,
    )
    await adminQuizDraftApi.draft({
      lessonId: 11,
      topic: 'tea',
      level: 'HSK 1',
      numberOfQuestions: 2,
      languagePreference: 'en',
    })

    expect(calls[0]?.timeout).toBe(1_800_000)
    expect(calls[0]?.signal).toBe(controller.signal)
    expect((calls[0]?.data as FormData).has('durationSeconds')).toBe(false)
    expect(calls[1]?.url).toBe('/admin/ai/quiz-draft')
    expect(calls[1]?.timeout).toBe(45_000)
  })
})
