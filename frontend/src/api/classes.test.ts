import type { AxiosAdapter } from 'axios'
import { describe, expect, it } from 'vitest'

import { http } from '@/api/http'
import { axiosResponse, expectRequests, installAxiosCapture, restoreAxiosAdapterAfterEach } from '@/test/axiosCapture'

import { publicClassesApi, studentClassesApi, videoBlob } from './classes'

restoreAxiosAdapterAfterEach()

describe('class API wrappers', () => {
  it('maps the catalogue and the learner class endpoints', async () => {
    await expectRequests([
      [() => publicClassesApi.search({ page: 0, size: 12 }), 'GET /public/classes'],
      [() => publicClassesApi.detail(7), 'GET /public/classes/7'],
      [() => publicClassesApi.topics(), 'GET /public/categories'],
      [() => studentClassesApi.myClasses(), 'GET /students/classes'],
      [() => studentClassesApi.catalogue({ page: 0, size: 12 }), 'GET /students/classes/catalogue'],
      [() => studentClassesApi.catalogueMembership(), 'GET /students/classes/catalogue/membership'],
      [() => studentClassesApi.classPage(7), 'GET /students/classes/7'],
      [() => studentClassesApi.join(7), 'POST /students/classes/7/join'],
      [() => studentClassesApi.leave(7), 'POST /students/classes/7/leave'],
      [() => studentClassesApi.lesson(11), 'GET /students/classes/lessons/11'],
      [() => studentClassesApi.markVideoWatched(11), 'POST /students/classes/lessons/11/video/watched'],
      [() => studentClassesApi.activity('FLASHCARDS', 3), 'GET /students/classes/activities/flashcards/3'],
      [() => studentClassesApi.completeActivity('TRACING', 4), 'POST /students/classes/activities/tracing/4/complete'],
      [() => studentClassesApi.checkMatching(5, []), 'POST /students/classes/activities/matching/5/check'],
      [() => studentClassesApi.matchingHint(5, 2), 'POST /students/classes/activities/matching/5/hint'],
      [() => studentClassesApi.startQuiz(9), 'POST /students/classes/quizzes/9/attempts'],
      [
        () => studentClassesApi.saveAnswer(40, { questionId: 1, optionId: 2 }),
        'PUT /students/classes/quiz-attempts/40/answers',
      ],
      [() => studentClassesApi.clearAnswer(40, 1), 'DELETE /students/classes/quiz-attempts/40/answers/1'],
      [() => studentClassesApi.hint(40, 1), 'POST /students/classes/quiz-attempts/40/hint'],
      [() => studentClassesApi.discardAttempt(40), 'DELETE /students/classes/quiz-attempts/40'],
      [() => studentClassesApi.submitQuiz(40), 'POST /students/classes/quiz-attempts/40/submit'],
      [() => studentClassesApi.quizResult(40), 'GET /students/classes/quiz-attempts/40'],
    ])

    const calls = installAxiosCapture()
    await publicClassesApi.search({ page: 1, size: 12 })
    await studentClassesApi.checkMatching(5, [{ wordId: 1, meaning: 'tea' }])
    await studentClassesApi.matchingHint(5, 2)
    await studentClassesApi.saveAnswer(40, { questionId: 1, optionId: 2 })
    await studentClassesApi.hint(40, 1)

    expect(calls[0]?.params).toEqual({ page: 1, size: 12 })
    expect(calls[1]?.data).toEqual({ pairs: [{ wordId: 1, meaning: 'tea' }] })
    expect(calls[2]?.data).toEqual({ wordId: 2 })
    expect(calls[3]?.data).toEqual({ questionId: 1, optionId: 2 })
    expect(calls[4]?.data).toEqual({ questionId: 1 })
  })

  it('downloads an uploaded lesson video blob, stripping /api/v1 and forwarding timeout and abort signal', async () => {
    const controller = new AbortController()
    const payload = new Blob(['mp4-bytes'], { type: 'video/mp4' })
    const seen: Array<{ url?: string; responseType?: string; timeout?: number; signal?: unknown }> = []
    http.defaults.adapter = (async (config) => {
      seen.push({
        url: config.url,
        responseType: config.responseType,
        timeout: config.timeout,
        signal: config.signal,
      })
      return axiosResponse(config, 200, payload)
    }) satisfies AxiosAdapter

    const blob = await videoBlob('/api/v1/students/classes/lessons/11/video', controller.signal)

    expect(blob).toBe(payload)
    expect(seen).toEqual([
      {
        url: '/students/classes/lessons/11/video',
        responseType: 'blob',
        timeout: 600_000,
        signal: controller.signal,
      },
    ])
  })
})
