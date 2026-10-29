import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it } from 'vitest'

import { accountApi, authApi, notificationsApi, publicApi, studentApi, systemApi, teacherApi } from './services'
import { http } from './http'
import { teacherVerificationApi } from './ops'
import { axiosHttpError, axiosResponse, installAxiosCapture, restoreAxiosAdapterAfterEach } from '@/test/axiosCapture'

const BACKEND_ERROR = { success: false, message: 'Nope', errors: ['Bad input'] }
const PASSWORD = 'Password123!'

restoreAxiosAdapterAfterEach()

/** A wrapper call and the request it must send, written `METHOD /path`. */
type WrapperCase = [run: () => Promise<unknown>, request: string]

async function expectWrapperMappings(cases: WrapperCase[]) {
  const calls = installAxiosCapture({ items: [] })
  for (const [run] of cases) await run()
  expect(calls.map(({ method, url }) => `${method} ${url}`)).toEqual(cases.map(([, request]) => request))
}

/** Every request is answered with `data` (status 200). */
function answerOk(data: unknown) {
  http.defaults.adapter = (async (config) => axiosResponse(config, 200, data)) satisfies AxiosAdapter
}

function downloadResponse(config: InternalAxiosRequestConfig, blob: Blob) {
  return {
    ...axiosResponse(config, 200, blob),
    headers: {
      'content-disposition': "attachment; filename*=UTF-8''teacher%20document.pdf",
      'content-type': 'application/pdf',
    },
  }
}

describe('REST API envelope handling', () => {
  it('rejects malformed envelopes', async () => {
    answerOk({ unexpected: true })

    await expect(systemApi.health()).rejects.toMatchObject({
      response: { data: { message: 'The server returned an unexpected response format.' } },
    })
  })

  it('turns a success:false envelope into an error carrying the server message', async () => {
    answerOk(BACKEND_ERROR)

    await expect(systemApi.health()).rejects.toMatchObject({
      name: 'ApiEnvelopeError',
      message: 'Nope',
      response: { data: { message: 'Nope', errors: ['Bad input'] } },
    })
  })

  it('rejects an envelope without data', async () => {
    answerOk({ success: true })

    await expect(systemApi.health()).rejects.toMatchObject({ name: 'ApiEnvelopeError' })
  })

  it('passes HTTP error responses through with their status', async () => {
    http.defaults.adapter = (async (config) => {
      throw axiosHttpError(config, 400, BACKEND_ERROR)
    }) satisfies AxiosAdapter

    await expect(systemApi.health()).rejects.toMatchObject({ response: { status: 400 } })
  })
})

describe('shared REST API wrappers', () => {
  it('map auth, account, notification, system, student and teacher calls to their endpoints', async () => {
    await expectWrapperMappings([
      [() => authApi.login({ email: 'student@example.com', password: PASSWORD }), 'POST /auth/login'],
      [
        () =>
          authApi.registerStudent({
            fullName: 'Test User',
            email: 'student@example.com',
            password: PASSWORD,
            confirmPassword: PASSWORD,
          }),
        'POST /auth/register/student',
      ],
      [() => authApi.me(), 'GET /auth/me'],
      [() => authApi.logout(), 'POST /auth/logout'],
      [() => authApi.forgotPassword('student@example.com'), 'POST /auth/forgot-password'],
      [
        () =>
          accountApi.changePassword({
            currentPassword: PASSWORD,
            newPassword: 'N3w-pass!',
            confirmPassword: 'N3w-pass!',
          }),
        'POST /users/me/password',
      ],
      [() => publicApi.platformSettings(), 'GET /public/platform-settings'],
      [() => notificationsApi.page({ status: 'READ', page: 1 }), 'GET /notifications'],
      [() => notificationsApi.unreadCount(), 'GET /notifications/unread-count'],
      [() => notificationsApi.markAsRead(7), 'PATCH /notifications/7/read'],
      [() => notificationsApi.markAllRead(), 'PATCH /notifications/read-all'],
      [() => systemApi.health(), 'GET /health'],
      [() => studentApi.rewards(), 'GET /students/rewards'],
      [() => teacherApi.aiAvailability(), 'GET /teachers/ai/availability'],
      [() => teacherApi.aiQuizDraft({ topic: 'campus' }), 'POST /teachers/ai/quiz-draft'],
    ])
  })

  it('sends the teacher AI quiz draft request as given', async () => {
    const calls = installAxiosCapture({ items: [] })
    await teacherApi.aiQuizDraft({ topic: 'Food', numberOfQuestions: 3, languagePreference: 'ms' })
    expect(calls[0].data).toEqual({ topic: 'Food', numberOfQuestions: 3, languagePreference: 'ms' })
  })

  it('downloads a protected file as a Blob with the file name from Content-Disposition', async () => {
    const blob = new Blob(['%PDF'], { type: 'application/pdf' })
    http.defaults.adapter = (async (config) => downloadResponse(config, blob)) satisfies AxiosAdapter

    await expect(teacherVerificationApi.document(4)).resolves.toMatchObject({ blob, fileName: 'teacher document.pdf' })
  })

  it('reads the JSON error body of a failed blob download so the server message can be shown', async () => {
    http.defaults.adapter = (async (config) => {
      const body = new Blob([JSON.stringify({ success: false, message: 'Document not found.' })], {
        type: 'application/json',
      })
      throw axiosHttpError(config, 404, body)
    }) satisfies AxiosAdapter

    await expect(teacherVerificationApi.document(4)).rejects.toMatchObject({
      response: { status: 404, data: { success: false, message: 'Document not found.' } },
    })
  })
})
