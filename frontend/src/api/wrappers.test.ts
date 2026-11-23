import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it } from 'vitest'

import { apiDownload } from './request'
import { authApi, notificationsApi, publicApi, shellApi, studentApi, systemApi } from './services'
import { http, setAccessToken } from './http'
import { adminSupportApi } from './ops'
import {
  axiosHttpError,
  axiosResponse,
  expectRequests,
  installAxiosCapture,
  restoreAxiosAdapterAfterEach,
} from '@/test/axiosCapture'
import { deferred } from '@/test/helpers'

const PASSWORD = 'Password123!'

restoreAxiosAdapterAfterEach()

function downloadResponse(config: InternalAxiosRequestConfig, blob: Blob) {
  return {
    ...axiosResponse(config, 200, blob),
    headers: {
      'content-disposition': "attachment; filename*=UTF-8''support%20attachment.pdf",
      'content-type': 'application/pdf',
    },
  }
}

describe('REST API envelope handling', () => {
  it('rejects a 2xx answer that is not an API envelope', async () => {
    http.defaults.adapter = (async (config) => axiosResponse(config, 200, { unexpected: true })) satisfies AxiosAdapter

    await expect(systemApi.health()).rejects.toMatchObject({
      name: 'ApiEnvelopeError',
      response: { data: { message: 'The server returned an unexpected response format.' } },
    })
  })

  it('passes HTTP error responses through with their status', async () => {
    http.defaults.adapter = (async (config) => {
      throw axiosHttpError(config, 400, { success: false, message: 'Nope' })
    }) satisfies AxiosAdapter

    await expect(systemApi.health()).rejects.toMatchObject({ response: { status: 400 } })
  })

  it('honours a failure envelope even when a gateway answers with HTTP 200', async () => {
    http.defaults.adapter = async (config) => axiosResponse(config, 200, { success: false, message: 'Unavailable' })
    await expect(systemApi.health()).rejects.toMatchObject({ response: { data: { message: 'Unavailable' } } })
  })
})

describe('shared REST API wrappers', () => {
  it('map auth, notification, system and student calls to their endpoints', async () => {
    await expectRequests([
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
      [() => authApi.session(), 'GET /users/me/session'],
      [() => authApi.logout(), 'POST /auth/logout'],
      [() => authApi.forgotPassword('student@example.com'), 'POST /auth/forgot-password'],
      [
        () => authApi.resetPassword({ token: 't', password: PASSWORD, confirmPassword: PASSWORD }),
        'POST /auth/reset-password',
      ],
      [() => publicApi.platformSettings(), 'GET /public/platform-settings'],
      [() => notificationsApi.page({ status: 'READ', page: 1, size: 20 }), 'GET /notifications'],
      [() => notificationsApi.unreadCount(), 'GET /notifications/unread-count'],
      [() => notificationsApi.markAsRead(7), 'PATCH /notifications/7/read'],
      [() => notificationsApi.markAllRead(), 'PATCH /notifications/read-all'],
      [() => notificationsApi.markUnread([3, 4]), 'PATCH /notifications/unread'],
      [() => shellApi.navCounts(), 'GET /users/me/nav-counts'],
      [() => systemApi.health(), 'GET /health'],
      [() => studentApi.rewards(), 'GET /students/rewards'],
    ])
  })

  it('downloads a protected file as a Blob with the file name from Content-Disposition', async () => {
    const blob = new Blob(['%PDF'], { type: 'application/pdf' })
    http.defaults.adapter = (async (config) => downloadResponse(config, blob)) satisfies AxiosAdapter

    await expect(adminSupportApi.attachment(4)).resolves.toMatchObject({ blob, fileName: 'support attachment.pdf' })
  })

  it('reads the JSON error body of a failed blob download so the server message can be shown', async () => {
    http.defaults.adapter = (async (config) => {
      const body = new Blob([JSON.stringify({ success: false, message: 'Attachment not found.' })], {
        type: 'application/json',
      })
      throw axiosHttpError(config, 404, body)
    }) satisfies AxiosAdapter

    await expect(adminSupportApi.attachment(4)).rejects.toMatchObject({
      response: { status: 404, data: { success: false, message: 'Attachment not found.' } },
    })
  })

  it('discards an attachment that arrives after its account signed out', async () => {
    const started = deferred<void>()
    const release = deferred<void>()
    http.defaults.adapter = async (config) => {
      started.resolve()
      await release.promise
      return downloadResponse(config, new Blob(['private attachment']))
    }
    setAccessToken('attachment-owner')
    const download = adminSupportApi.attachment(4)
    await started.promise
    setAccessToken(null)
    release.resolve()
    await expect(download).rejects.toMatchObject({ code: 'ERR_CANCELED' })
  })

  it('sends notification query parameters and mark-unread payload accurately', async () => {
    const calls = installAxiosCapture()

    await notificationsApi.page({ status: 'READ', page: 2, size: 15 })
    await notificationsApi.markUnread([3, 4])

    expect(calls).toEqual([
      { method: 'GET', url: '/notifications', params: { status: 'READ', page: 2, size: 15 }, data: undefined },
      { method: 'PATCH', url: '/notifications/unread', params: undefined, data: { ids: [3, 4] } },
    ])
  })

  it.each([
    ['attachment; filename="report-2026.csv"', 'report-2026.csv'],
    ['attachment; filename=plain.txt', 'plain.txt'],
    ["attachment; filename*=UTF-8''%E0%A4%A", '%E0%A4%A'],
    [undefined, null],
  ])('parses Content-Disposition header (%s) as %s', async (header, expected) => {
    const blob = new Blob(['data'])
    http.defaults.adapter = (async (config) => ({
      ...axiosResponse(config, 200, blob),
      headers: header ? { 'content-disposition': header } : {},
    })) satisfies AxiosAdapter

    await expect(apiDownload('/exports/sample')).resolves.toEqual({ blob, fileName: expected })
  })
})
