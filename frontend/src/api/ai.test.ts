import type { AxiosAdapter } from 'axios'
import { describe, expect, it } from 'vitest'

import { http } from './http'
import { adminAiApi, aiHelperApi } from './ai'
import { axiosResponse, installAxiosCapture, restoreAxiosAdapterAfterEach } from '@/test/axiosCapture'

restoreAxiosAdapterAfterEach()

const turns = [{ speaker: 'XIAOMEI' as const, text: '你好！我是小美。' }]

describe('AI Helper wrappers', () => {
  it('sends the conversation, quiz, story, report and check-list requests to the /ai endpoints', async () => {
    const calls = installAxiosCapture()

    await aiHelperApi.status()
    await aiHelperApi.xiaomeiReply({ scenario: 'CAFE', turns, message: '我要一杯奶茶', languagePreference: 'en' })
    await aiHelperApi.xiaomeiSummary({ scenario: 'CAFE', turns, languagePreference: 'zh-CN' })
    await aiHelperApi.quiz({ topic: 'Food', numberOfQuestions: 3, languagePreference: 'en' })
    await aiHelperApi.story({ topic: 'Family', languagePreference: 'en' })
    await aiHelperApi.practiceChecks(0, 5)
    await aiHelperApi.practiceCheckLessons()
    await aiHelperApi.deletePracticeCheck(7)
    await aiHelperApi.reports(1)
    await aiHelperApi.report({ featureKey: 'STORY_GENERATOR', reasonCategory: 'UNCLEAR_EXPLANATION' })
    await aiHelperApi.restorePracticeCheck(7)
    await aiHelperApi.xiaomeiMemory()
    await aiHelperApi.forgetXiaomeiNote(3)
    await aiHelperApi.restoreXiaomeiNote(3)

    expect(calls.map(({ method, url }) => `${method} ${url}`)).toEqual([
      'GET /ai/status',
      'POST /ai/conversation/reply',
      'POST /ai/conversation/summary',
      'POST /ai/quiz-generator',
      'POST /ai/story-generator',
      'GET /ai/practice-checks',
      'GET /ai/practice-checks/lessons',
      'DELETE /ai/practice-checks/7',
      'GET /ai/reports',
      'POST /ai/reports',
      'POST /ai/practice-checks/7/restore',
      'GET /ai/conversation/memory',
      'DELETE /ai/conversation/memory/3',
      'POST /ai/conversation/memory/3/restore',
    ])
    expect(calls[1]!.data).toEqual({ scenario: 'CAFE', turns, message: '我要一杯奶茶', languagePreference: 'en' })
    expect(calls[5]!.params).toEqual({ page: 0, size: 5 })
    expect(calls[8]!.params).toEqual({ page: 1, size: 20 })
    expect(calls[9]!.data).toEqual({ featureKey: 'STORY_GENERATOR', reasonCategory: 'UNCLEAR_EXPLANATION' })
  })

  it('fits both report snapshots into the server fields without splitting a Han character', async () => {
    const calls = installAxiosCapture()
    const prompt = '问'.repeat(3999)
    const reply = '𠀀'.repeat(2000)
    await aiHelperApi.report({
      featureKey: 'STORY_GENERATOR',
      reasonCategory: 'UNCLEAR_EXPLANATION',
      promptSnapshot: prompt + '𠀀',
      aiResponseSnapshot: reply + '答',
    })
    expect(calls[0]!.data).toMatchObject({ promptSnapshot: prompt, aiResponseSnapshot: reply })
  })

  it('uploads a practice check as multipart form data', async () => {
    const calls = installAxiosCapture()
    const recording = new File(['voice'], 'xie-xie.webm', { type: 'audio/webm' })

    await aiHelperApi.checkPractice({
      type: 'RECORDING',
      file: recording,
      targetText: '  谢谢  ',
      lessonId: 12,
      languagePreference: 'en',
    })
    await aiHelperApi.checkPractice({
      type: 'HANDWRITING',
      file: new File(['img'], 'hao.png', { type: 'image/png' }),
      targetText: '好',
      lessonId: null,
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
    expect((recordingForm.get('file') as File).name).toBe('xie-xie.webm')
    const photoForm = calls[1]!.data as FormData
    expect(photoForm.get('type')).toBe('HANDWRITING')
    expect(photoForm.has('lessonId')).toBe(false)
    expect(photoForm.get('languagePreference')).toBe('zh-CN')
  })

  it('applies endpoint-specific timeouts to AI generation and practice check uploads', async () => {
    const timeouts: Array<{ url?: string; timeout?: number }> = []
    http.defaults.adapter = (async (config) => {
      timeouts.push({ url: config.url, timeout: config.timeout })
      return axiosResponse(config, 200, { success: true, data: {} })
    }) satisfies AxiosAdapter

    await aiHelperApi.xiaomeiReply({ scenario: 'CAFE', turns, message: '你好', languagePreference: 'en' })
    await aiHelperApi.checkPractice({
      type: 'RECORDING',
      file: new File(['voice'], 'a.webm', { type: 'audio/webm' }),
      targetText: '你好',
      languagePreference: 'en',
    })
    await aiHelperApi.checkPractice({
      type: 'HANDWRITING',
      file: new File(['img'], 'a.png', { type: 'image/png' }),
      targetText: '好',
      languagePreference: 'en',
    })

    expect(timeouts).toEqual([
      { url: '/ai/conversation/reply', timeout: 45_000 },
      { url: '/ai/practice-checks', timeout: 370_000 },
      { url: '/ai/practice-checks', timeout: 90_000 },
    ])
  })
})

describe('admin AI wrappers', () => {
  it('maps reports, review decisions, usage, settings and hint policy to /admin/ai', async () => {
    const calls = installAxiosCapture()
    const levels = [{ tier: 'HSK1' as const, delaySeconds: 15, perSet: 2 }]

    await adminAiApi.reports({ page: 1, size: 8 })
    await adminAiApi.report(3)
    await adminAiApi.reviewReport(3, 'DISMISSED', 'The answer was correct.')
    await adminAiApi.usageSummary()
    await adminAiApi.usageLogs({ featureType: 'CHAT_TUTOR', status: '', userRole: 'STUDENT' }, 2, 10)
    await adminAiApi.settings()
    await adminAiApi.updateSettings({
      aiNoticeText: 'AI can make mistakes.',
      sensitiveWordFilter: true,
      aiSafetyCheck: false,
    })
    await adminAiApi.hintPolicy()
    await adminAiApi.saveHintPolicy(levels)

    expect(calls).toEqual([
      { method: 'GET', url: '/admin/ai/reports', params: { page: 1, size: 8 }, data: undefined },
      { method: 'GET', url: '/admin/ai/reports/3', params: undefined, data: undefined },
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
      { method: 'GET', url: '/admin/ai/hints', params: undefined, data: undefined },
      { method: 'PUT', url: '/admin/ai/hints', params: undefined, data: { levels } },
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
