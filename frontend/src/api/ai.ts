/**
 * The AI Helper (`/ai/**`, students) and the admin AI area (`/admin/ai/**`; the lesson editor's quiz draft is in
 * adminClasses.ts).
 */
import type { AiQuizGeneratorResponse, AppLanguage, PageResponse } from '@/types/api'
import type {
  AdminAiResponseReportResponse,
  AdminAiSettings,
  AdminAiSettingsUpdate,
  AdminAiUsageFilters,
  AdminAiUsageLogResponse,
  AdminAiUsageSummary,
  AiHelperStatus,
  AiReportPayload,
  AiStoryGeneratorResponse,
  PracticeCheck,
  PracticeCheckLesson,
  PracticeCheckResult,
  PracticeCheckType,
  StudentAiResponseReportResponse,
  XiaomeiMemoryNote,
  XiaomeiReply,
  XiaomeiScenario,
  XiaomeiSummary,
  XiaomeiTurn,
} from '@/types/ai'
import type { HintPolicyLevel } from '@/types/learn'
import { AI_REPORT_SNAPSHOT_LIMIT } from '@/types/ai'
import { truncateText } from '@/utils/truncateText'

import { apiChatStream } from './chatStream'
import { apiDelete, apiDownload, apiGet, apiPost, apiPut } from './request'

/**
 * For a request answered after one AI provider call (AI Helper generators, Xiaomei, lesson editor quiz drafts).
 * The backend gives the provider at most 40 s (`AI_TIMEOUT_SECONDS`, 30 by default; SRS FRAI09); the browser waits a
 * little longer so the server always answers first.
 */
export const AI_REQUEST_CONFIG = { timeout: 45_000 }

/** Allow for uploading the photo as well as the image assessment. */
const HANDWRITING_CHECK_CONFIG = { timeout: 90_000 }
// Uploaded recordings over a minute are relayed to the speech service at playback speed.
const RECORDING_CHECK_CONFIG = { timeout: 370_000 }

interface XiaomeiContext {
  scenario: XiaomeiScenario
  turns: readonly XiaomeiTurn[]
  languagePreference: AppLanguage
}

interface PracticeCheckUpload {
  type: PracticeCheckType
  file: File
  targetText: string
  lessonId?: number | null
  languagePreference: AppLanguage
}

function practiceCheckForm(upload: PracticeCheckUpload): FormData {
  const form = new FormData()
  form.append('type', upload.type)
  form.append('file', upload.file)
  form.append('targetText', upload.targetText.trim())
  form.append('languagePreference', upload.languagePreference)
  if (upload.lessonId) form.append('lessonId', String(upload.lessonId))
  return form
}

export const aiHelperApi = {
  status() {
    return apiGet<AiHelperStatus>('/ai/status')
  },
  chatStream: apiChatStream,
  xiaomeiReply(context: XiaomeiContext & { message: string }) {
    return apiPost<XiaomeiReply>('/ai/conversation/reply', context, AI_REQUEST_CONFIG)
  },
  xiaomeiSummary(context: XiaomeiContext) {
    return apiPost<XiaomeiSummary>('/ai/conversation/summary', context, AI_REQUEST_CONFIG)
  },
  /** What Xiaomei remembers about the learner (the summary lists it too); no AI request. */
  xiaomeiMemory() {
    return apiGet<XiaomeiMemoryNote[]>('/ai/conversation/memory')
  },
  /** "Forget": answers with the notes Xiaomei still remembers. */
  forgetXiaomeiNote(id: number) {
    return apiDelete<XiaomeiMemoryNote[]>(`/ai/conversation/memory/${id}`)
  },
  /** The Undo of "Forget": answers with the notes. */
  restoreXiaomeiNote(id: number) {
    return apiPost<XiaomeiMemoryNote[]>(`/ai/conversation/memory/${id}/restore`)
  },
  quiz(payload: { topic: string; numberOfQuestions: number; languagePreference: AppLanguage }) {
    return apiPost<AiQuizGeneratorResponse>('/ai/quiz-generator', payload, AI_REQUEST_CONFIG)
  },
  story(payload: { topic: string; languagePreference: AppLanguage }) {
    return apiPost<AiStoryGeneratorResponse>('/ai/story-generator', payload, AI_REQUEST_CONFIG)
  },
  checkPractice(upload: PracticeCheckUpload) {
    const config = upload.type === 'RECORDING' ? RECORDING_CHECK_CONFIG : HANDWRITING_CHECK_CONFIG
    return apiPost<PracticeCheckResult>('/ai/practice-checks', practiceCheckForm(upload), config)
  },
  practiceChecks(page: number, size: number) {
    return apiGet<PageResponse<PracticeCheck>>('/ai/practice-checks', { params: { page, size } })
  },
  practiceCheckLessons() {
    return apiGet<PracticeCheckLesson[]>('/ai/practice-checks/lessons')
  },
  /** Deletes the check at once; `restorePracticeCheck` is its Undo for a while. */
  deletePracticeCheck(id: number) {
    return apiDelete<null>(`/ai/practice-checks/${id}`)
  },
  restorePracticeCheck(id: number) {
    return apiPost<PracticeCheck>(`/ai/practice-checks/${id}/restore`)
  },
  reports(page = 0) {
    return apiGet<PageResponse<StudentAiResponseReportResponse>>('/ai/reports', { params: { page, size: 20 } })
  },
  report(payload: AiReportPayload) {
    const { promptSnapshot, aiResponseSnapshot } = payload
    return apiPost<StudentAiResponseReportResponse>('/ai/reports', {
      ...payload,
      promptSnapshot: promptSnapshot && truncateText(promptSnapshot, AI_REPORT_SNAPSHOT_LIMIT),
      aiResponseSnapshot: aiResponseSnapshot && truncateText(aiResponseSnapshot, AI_REPORT_SNAPSHOT_LIMIT),
    })
  },
}

/** Drops the "all" (empty) filters so the request only names real ones. */
function usageParams(filters: AdminAiUsageFilters): Record<string, string> {
  return Object.fromEntries(Object.entries(filters).filter(([, value]) => Boolean(value)))
}

export const adminAiApi = {
  /** Every report, open ones first. */
  reports(query: { page: number; size: number }) {
    return apiGet<PageResponse<AdminAiResponseReportResponse>>('/admin/ai/reports', { params: query })
  },
  report(id: number) {
    return apiGet<AdminAiResponseReportResponse>(`/admin/ai/reports/${id}`)
  },
  reviewReport(id: number, decision: 'RESOLVED' | 'DISMISSED', reviewerNote: string) {
    return apiPost<AdminAiResponseReportResponse>(`/admin/ai/reports/${id}/review`, {
      status: decision,
      reviewerNote,
    })
  },
  usageSummary() {
    return apiGet<AdminAiUsageSummary>('/admin/ai/usage/summary')
  },
  usageLogs(filters: AdminAiUsageFilters, page: number, size: number) {
    return apiGet<PageResponse<AdminAiUsageLogResponse>>('/admin/ai/usage-logs', {
      params: { ...usageParams(filters), page, size },
    })
  },
  /** The filtered log as a CSV file; the export is recorded in the audit log. */
  exportUsageLogs(filters: AdminAiUsageFilters) {
    return apiDownload('/admin/ai/usage-logs/export', { params: usageParams(filters) })
  },
  settings() {
    return apiGet<AdminAiSettings>('/admin/ai/settings')
  },
  updateSettings(payload: AdminAiSettingsUpdate) {
    return apiPut<AdminAiSettings>('/admin/ai/settings', payload)
  },
  /** Hint delay and per-set budget rules per HSK tier (AdminAiController). */
  hintPolicy() {
    return apiGet<{ levels: HintPolicyLevel[] }>('/admin/ai/hints').then((data) => data.levels)
  },
  saveHintPolicy(levels: readonly HintPolicyLevel[]) {
    return apiPut<{ levels: HintPolicyLevel[] }>('/admin/ai/hints', { levels }).then((data) => data.levels)
  },
}
