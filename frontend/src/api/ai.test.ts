import type { AxiosAdapter } from 'axios'
import { describe, expect, it } from 'vitest'

import { http } from './http'
import { adminAiApi, aiHelperApi } from './ai'
import { axiosResponse, installAxiosCapture, restoreAxiosAdapterAfterEach } from '@/test/axiosCapture'

restoreAxiosAdapterAfterEach()

const turns = [{ speaker: 'XIAOMEI' as const, text: '你好！我是小美。' }]

describe('AI Helper wrappers', () => {
  it('sends the conversation, quiz, story, report and check-list requests to the /ai endpoints', async () => {
    const calls = installAxiosCapture({ items: [] })

    await aiHelperApi.status()
    await aiHelperApi.xiaomeiReply({ scenario: 'CAFE', turns, message: '我要一杯奶茶', languagePreference: 'en' })
    await aiHelperApi.xiaomeiSuggestions({ scenario: 'CAFE', turns, languagePreference: 'ms' })
    await aiHelperApi.xiaomeiSummary({ scenario: 'CAFE', turns, languagePreference: 'zh-CN' })
    await aiHelperApi.quiz({ topic: 'Food', numberOfQuestions: 3, languagePreference: 'en' })
    await aiHelperApi.story({ topic: 'Family', languagePreference: 'en' })
    await aiHelperApi.practiceChecks()
    await aiHelperApi.practiceCheckLessons()
    await aiHelperApi.deletePracticeCheck(7)
    await aiHelperApi.reports()
    await aiHelperApi.report({ featureKey: 'STORY_GENERATOR', reasonCategory: 'UNCLEAR_EXPLANATION' })

    expect(calls.map(({ method, url }) => `${method} ${url}`)).toEqual([
      'GET /ai/status',
      'POST /ai/conversation/reply',
      'POST /ai/conversation/suggestions',
      'POST /ai/conversation/summary',
      'POST /ai/quiz-generator',
      'POST /ai/story-generator',
      'GET /ai/practice-checks',
      'GET /ai/practice-checks/lessons',
      'DELETE /ai/practice-checks/7',
      'GET /ai/reports',
      'POST /ai/reports',
    ])
    expect(calls[1]!.data).toEqual({ scenario: 'CAFE', turns, message: '我要一杯奶茶', languagePreference: 'en' })
    expect(calls[6]!.params).toEqual({ page: 0, size: 5 })
    expect(calls[10]!.data).toEqual({ featureKey: 'STORY_GENERATOR', reasonCategory: 'UNCLEAR_EXPLANATION' })
  })

  it('uploads a practice check as multipart form data with a whole-second recording length', async () => {
    const calls = installAxiosCapture({ status: 'SUCCESS', message: null, check: null })
    const recording = new Blob(['voice'], { type: 'audio/webm' })

    await aiHelperApi.checkPractice({
      type: 'RECORDING',
      file: recording,
      fileName: 'xie-xie.webm',
      targetText: '  谢谢  ',
      lessonId: 12,
      durationSeconds: 3.4,
      languagePreference: 'en',
    })
    await aiHelperApi.checkPractice({
      type: 'HANDWRITING',
      file: new Blob(['img'], { type: 'image/png' }),
      fileName: 'hao.png',
      targetText: '好',
      lessonId: null,
      durationSeconds: null,
      languagePreference: 'zh-CN',
    })

    expect(calls.map(({ method, url }) => `${method} ${url}`)).toEqual([
      'POST /ai/practice-checks',
      'POST /ai/practice-checks',
    ])
    const recordingForm = calls[0]!.data as FormData
    expect(recordingForm.get('type')).toBe('RECORDING')
    expect(recordingForm.get('targetText')).toBe('谢谢')
    expect(recordingForm.get('lessonId')).toBe('12')
    expect(recordingForm.get('durationSeconds')).toBe('3')
    expect((recordingForm.get('file') as File).name).toBe('xie-xie.webm')
    const photoForm = calls[1]!.data as FormData
    expect(photoForm.get('type')).toBe('HANDWRITING')
    expect(photoForm.has('lessonId')).toBe(false)
    expect(photoForm.has('durationSeconds')).toBe(false)
    expect(photoForm.get('languagePreference')).toBe('zh-CN')
  })
})

describe('admin AI wrappers', () => {
  it('maps reports, review decisions, usage and settings to /admin/ai', async () => {
    const calls = installAxiosCapture({ items: [] })

    await adminAiApi.reports({ status: 'OPEN', page: 1, size: 8 })
    await adminAiApi.reportCounts()
    await adminAiApi.reviewReport(3, 'DISMISSED', 'The answer was correct.')
    await adminAiApi.usageSummary()
    await adminAiApi.usageLogs({ featureType: 'CHAT_TUTOR', status: '', userRole: 'STUDENT' }, 2, 10)
    await adminAiApi.settings()
    await adminAiApi.updateSettings({
      aiNoticeText: 'AI can make mistakes.',
      sensitiveWordFilter: true,
      aiSafetyCheck: false,
    })

    expect(calls).toEqual([
      { method: 'GET', url: '/admin/ai/reports', params: { status: 'OPEN', page: 1, size: 8 }, data: undefined },
      { method: 'GET', url: '/admin/ai/reports/counts', params: undefined, data: undefined },
      {
        method: 'POST',
        url: '/admin/ai/reports/3/review',
        params: undefined,
        data: { status: 'DISMISSED', reviewerNote: 'The answer was correct.' },
      },
      { method: 'GET', url: '/admin/ai/usage/summary', params: undefined, data: undefined },
      {
        method: 'GET',
        url: '/admin/ai/usage-logs',
        params: { featureType: 'CHAT_TUTOR', userRole: 'STUDENT', page: 2, size: 10 },
        data: undefined,
      },
      { method: 'GET', url: '/admin/ai/settings', params: undefined, data: undefined },
      {
        method: 'PUT',
        url: '/admin/ai/settings',
        params: undefined,
        data: { aiNoticeText: 'AI can make mistakes.', sensitiveWordFilter: true, aiSafetyCheck: false },
      },
    ])
  })

  it('downloads the filtered usage log as a CSV file', async () => {
    const seen: Array<{ url?: string; params: unknown; responseType: unknown }> = []
    http.defaults.adapter = (async (config) => {
      seen.push({ url: config.url, params: config.params, responseType: config.responseType })
      return {
        ...axiosResponse(config, 200, new Blob(['Time,User'], { type: 'text/csv' })),
        headers: { 'content-disposition': 'attachment; filename="ai-usage-log-2026-10-28.csv"' },
      }
    }) satisfies AxiosAdapter

    const file = await adminAiApi.exportUsageLogs({ featureType: '', status: 'FAILED', userRole: '' })

    expect(file.fileName).toBe('ai-usage-log-2026-10-28.csv')
    expect(seen).toEqual([{ url: '/admin/ai/usage-logs/export', params: { status: 'FAILED' }, responseType: 'blob' }])
  })
})
