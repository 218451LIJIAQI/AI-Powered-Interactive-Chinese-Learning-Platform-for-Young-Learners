import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import { http } from './http'
import { adminActivityGateway, adminApi, teacherActivityGateway, teacherApi } from './services'
import type { AdminFlashcardPayload } from './services'

interface CapturedRequest {
  method: string
  url: string
  data?: unknown
  params?: unknown
}

const originalAdapter = http.defaults.adapter

function capture(data: unknown = { items: [], id: 1, lessonId: 2, status: 'DRAFT' }): CapturedRequest[] {
  const calls: CapturedRequest[] = []
  http.defaults.adapter = (async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    calls.push({
      method: (config.method ?? 'get').toUpperCase(),
      url: config.url ?? '',
      data: typeof config.data === 'string' ? JSON.parse(config.data) : config.data,
      params: config.params,
    })
    return { data: { success: true, data }, status: 200, statusText: 'OK', headers: {}, config }
  }) satisfies AxiosAdapter
  return calls
}

const flashcard: AdminFlashcardPayload = { frontText: '茶', backText: 'tea', learningStage: 'GUIDED', tags: [] }

afterEach(() => {
  http.defaults.adapter = originalAdapter
})

describe('W3 content governance wrappers', () => {
  it('maps teacher submit-for-review, activity list and AI draft endpoints', async () => {
    const calls = capture()

    await teacherApi.submitPackage(1)
    await teacherApi.submitModule(2)
    await teacherApi.submitLesson(3)
    await teacherApi.submitQuiz(4)
    await teacherApi.activities({ status: 'REJECTED', type: 'STORY_ACTIVITY', page: 0, size: 12 })
    await teacherApi.aiAvailability()
    await teacherApi.aiQuizDraft({ topic: 'Food', numberOfQuestions: 3, languagePreference: 'ms' })
    await teacherApi.aiStoryDraft({ topic: 'Campus', lengthPreference: 'short' })

    expect(calls.map(({ method, url }) => `${method} ${url}`)).toEqual([
      'POST /teachers/packages/1/submit',
      'POST /teachers/modules/2/submit',
      'POST /teachers/lessons/3/submit',
      'POST /teachers/quizzes/4/submit',
      'GET /teachers/activities',
      'GET /teachers/ai/availability',
      'POST /teachers/ai/quiz-draft',
      'POST /teachers/ai/story-draft',
    ])
    expect(calls[4].params).toEqual({ status: 'REJECTED', type: 'STORY_ACTIVITY', page: 0, size: 12 })
    expect(calls[6].data).toEqual({ topic: 'Food', numberOfQuestions: 3, languagePreference: 'ms' })
  })

  it('maps teacher-authored activity CRUD and submit onto /teachers/activities/{slug}', async () => {
    const calls = capture()

    await teacherActivityGateway.create('FLASHCARD', flashcard)
    await teacherActivityGateway.detail('LISTENING_EXERCISE', 5)
    await teacherActivityGateway.update('TRACING_EXERCISE', 6, { title: '人' })
    await teacherActivityGateway.remove('DRAG_DROP_ACTIVITY', 7)
    await teacherActivityGateway.submit('STORY_ACTIVITY', 8)

    expect(calls.map(({ method, url }) => `${method} ${url}`)).toEqual([
      'POST /teachers/activities/flashcards',
      'GET /teachers/activities/listening-exercises/5',
      'PUT /teachers/activities/tracing-exercises/6',
      'DELETE /teachers/activities/drag-drop-activities/7',
      'POST /teachers/activities/story-activities/8/submit',
    ])
    expect(calls[0].data).toEqual(flashcard)
  })

  it('keeps the admin library on /admin/activities with the same shared payloads', async () => {
    const calls = capture()

    await adminActivityGateway.create('FLASHCARD', flashcard)
    await adminActivityGateway.update('STORY_ACTIVITY', 3, { title: 'Story', storyText: '你好。', questions: [] })
    await adminActivityGateway.detail('MATCHING_GAME', 4)
    await adminActivityGateway.remove('FLASHCARD', 5)

    expect(calls.map(({ method, url }) => `${method} ${url}`)).toEqual([
      'POST /admin/activities/flashcards',
      'PUT /admin/activities/story-activities/3',
      'GET /admin/activities/matching-games/4',
      'DELETE /admin/activities/flashcards/5',
    ])
  })

  it('maps the admin content review queue and decisions', async () => {
    const calls = capture()

    await adminApi.contentReviewQueue({ status: 'SUBMITTED', type: 'QUIZ', page: 0, size: 10 })
    await adminApi.contentReviewDetail('LESSON', 15)
    await adminApi.approveContent('QUIZ', 7)
    await adminApi.approveContent('QUIZ', 8, 'Clear questions')
    await adminApi.rejectContent('MATCHING_GAME', 42, 'Add pinyin')
    await adminApi.archiveReviewedContent('PACKAGE', 1)
    await adminApi.restoreContent('MODULE', 2, 'Fixed')

    expect(calls.map(({ method, url }) => `${method} ${url}`)).toEqual([
      'GET /admin/content-review',
      'GET /admin/content-review/LESSON/15',
      'POST /admin/content-review/QUIZ/7/approve',
      'POST /admin/content-review/QUIZ/8/approve',
      'POST /admin/content-review/MATCHING_GAME/42/reject',
      'POST /admin/content-review/PACKAGE/1/archive',
      'POST /admin/content-review/MODULE/2/restore',
    ])
    expect(calls[0].params).toEqual({ status: 'SUBMITTED', type: 'QUIZ', page: 0, size: 10 })
    expect(calls[2].data).toEqual({})
    expect(calls[3].data).toEqual({ note: 'Clear questions' })
    expect(calls[4].data).toEqual({ reason: 'Add pinyin' })
    expect(calls[6].data).toEqual({ note: 'Fixed' })
  })

  it('sends the server-side audit-log filters and returns the full page', async () => {
    const page = { items: [{ id: 1 }], page: 1, size: 10, totalItems: 11, totalPages: 2 }
    const calls = capture(page)

    const result = await adminApi.auditLogPage({
      from: '2026-05-01',
      to: '2026-05-31',
      actorId: 103,
      page: 1,
      size: 10,
    })

    expect(result).toEqual(page)
    expect(calls[0]).toMatchObject({
      method: 'GET',
      url: '/admin/audit-logs',
      params: { from: '2026-05-01', to: '2026-05-31', actorId: 103, page: 1, size: 10 },
    })
  })
})
