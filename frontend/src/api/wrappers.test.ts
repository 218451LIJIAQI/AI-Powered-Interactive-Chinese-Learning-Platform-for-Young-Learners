import { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import {
  aiApi,
  adminApi,
  authApi,
  notificationsApi,
  paymentApi,
  publicApi,
  studentApi,
  systemApi,
  teacherApi,
} from './services'
import { http } from './http'

const originalAdapter = http.defaults.adapter

interface CapturedRequest {
  method: string
  url: string
  data?: unknown
  params?: unknown
  responseType?: unknown
}

function parseData(value: unknown): unknown {
  if (typeof value !== 'string') {
    return value
  }

  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

function response<T>(config: InternalAxiosRequestConfig, data: T): AxiosResponse {
  return {
    data: {
      success: true,
      data,
    },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }
}

function installCaptureAdapter(data: unknown = { ok: true }) {
  const calls: CapturedRequest[] = []
  http.defaults.adapter = (async (config) => {
    calls.push({
      method: (config.method ?? 'get').toUpperCase(),
      url: config.url ?? '',
      data: parseData(config.data),
      params: config.params,
      responseType: config.responseType,
    })

    return response(config, data)
  }) satisfies AxiosAdapter
  return calls
}

describe('REST API wrappers', () => {
  afterEach(() => {
    http.defaults.adapter = originalAdapter
  })

  it('unwraps successful envelopes and rejects malformed envelopes', async () => {
    http.defaults.adapter = (async (config) => ({
      data: { unexpected: true },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    })) satisfies AxiosAdapter

    await expect(systemApi.health()).rejects.toMatchObject({
      response: {
        data: {
          message: 'The server returned an unexpected response format.',
        },
      },
    })
  })

  it('turns backend rejection envelopes into friendly errors', async () => {
    http.defaults.adapter = (async (config) => {
      throw new AxiosError('Request failed with status code 400', AxiosError.ERR_BAD_REQUEST, config, undefined, {
        data: { success: false, message: 'Nope', errors: ['Bad input'] },
        status: 400,
        statusText: 'Bad Request',
        headers: {},
        config,
      })
    }) satisfies AxiosAdapter

    await expect(systemApi.health()).rejects.toMatchObject({ response: { status: 400 } })
  })

  it('maps auth, public, notification, system, student, payment, teacher, and admin endpoints', async () => {
    const calls = installCaptureAdapter({ items: [{ id: 1 }] })

    await authApi.login({ email: 'learner@example.com', password: 'Password123!' })
    await publicApi.listCourses({ keyword: 'pinyin', page: 1 })
    await notificationsApi.markAllRead()
    await systemApi.health()
    await studentApi.submitQuiz(9, [{ questionId: 3, answerText: 'ni hao' }])
    await paymentApi.status(12)
    await teacherApi.archiveRecordedVideo(2, 5)
    await adminApi.approveTeacher(7, 'Looks good')

    expect(calls).toEqual([
      { method: 'POST', url: '/auth/login', data: { email: 'learner@example.com', password: 'Password123!' } },
      { method: 'GET', url: '/public/courses', params: { keyword: 'pinyin', page: 1 } },
      { method: 'PATCH', url: '/notifications/read-all' },
      { method: 'GET', url: '/health' },
      {
        method: 'POST',
        url: '/students/quizzes/9/attempts',
        data: { answers: [{ questionId: 3, answerText: 'ni hao' }] },
      },
      { method: 'GET', url: '/payments/status/12' },
      { method: 'PATCH', url: '/teachers/lessons/2/recorded-videos/5/archive' },
      { method: 'POST', url: '/admin/teachers/7/approve', data: { note: 'Looks good' } },
    ])
  })

  it('extracts safe download metadata from teacher and admin document responses', async () => {
    const blob = new Blob(['pdf'], { type: 'application/pdf' })
    http.defaults.adapter = (async (config) => ({
      data: blob,
      status: 200,
      statusText: 'OK',
      headers: {
        'content-disposition': "attachment; filename*=UTF-8''teacher%20document.pdf",
        'content-type': 'application/pdf',
      },
      config,
    })) satisfies AxiosAdapter

    await expect(teacherApi.downloadVerificationDocument(4)).resolves.toEqual({
      blob,
      fileName: 'teacher document.pdf',
      contentType: 'application/pdf',
    })
  })

  it('unwraps paged list endpoints to plain item arrays where wrappers promise arrays', async () => {
    const calls = installCaptureAdapter({ items: [{ id: 11 }, { id: 12 }] })

    await expect(notificationsApi.list({ status: 'UNREAD' })).resolves.toEqual([{ id: 11 }, { id: 12 }])
    await expect(studentApi.badges()).resolves.toEqual([{ id: 11 }, { id: 12 }])
    await expect(teacherApi.liveClasses()).resolves.toEqual([{ id: 11 }, { id: 12 }])
    await expect(adminApi.moderationRecords({ status: 'OPEN' })).resolves.toEqual([{ id: 11 }, { id: 12 }])

    expect(calls).toEqual([
      { method: 'GET', url: '/notifications', params: { status: 'UNREAD' } },
      { method: 'GET', url: '/students/rewards/badges' },
      { method: 'GET', url: '/teachers/live-classes' },
      { method: 'GET', url: '/admin/moderation-records', params: { status: 'OPEN' } },
    ])
  })

  it('maps AI, student commerce, teacher content, and admin moderation commands', async () => {
    const calls = installCaptureAdapter({ items: [] })

    await aiApi.quizGenerator({ topic: 'tones', numberOfQuestions: 3 })
    await studentApi.buyNow({ itemType: 'LESSON', itemId: 42, featureKey: 'recording' })
    await teacherApi.addPackageItem(6, { itemType: 'LESSON', itemId: 9, displayOrder: 2 })
    await adminApi.moderationAction('request-revision', {
      targetType: 'LESSON',
      targetId: 9,
      reason: 'Needs source attribution',
    })
    await adminApi.reviewAiReport(3, { status: 'RESOLVED', reviewerNote: 'Teacher reviewed' })

    expect(calls).toEqual([
      { method: 'POST', url: '/ai/quiz-generator', data: { topic: 'tones', numberOfQuestions: 3 } },
      {
        method: 'POST',
        url: '/students/checkout/buy-now',
        data: { itemType: 'LESSON', itemId: 42, featureKey: 'recording' },
      },
      {
        method: 'POST',
        url: '/teachers/packages/6/items',
        data: { itemType: 'LESSON', itemId: 9, displayOrder: 2 },
      },
      {
        method: 'POST',
        url: '/admin/moderation/request-revision',
        data: { targetType: 'LESSON', targetId: 9, reason: 'Needs source attribution' },
      },
      {
        method: 'POST',
        url: '/admin/ai-response-reports/3/review',
        data: { status: 'RESOLVED', reviewerNote: 'Teacher reviewed' },
      },
    ])
  })
})
