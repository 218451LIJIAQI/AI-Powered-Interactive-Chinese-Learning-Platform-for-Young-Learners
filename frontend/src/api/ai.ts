/**
 * The AI Helper (`/ai/**`, students) and the admin AI area (`/admin/ai/**`). Ask SparkBot's streamed answer comes
 * from `aiApi.chatStream` in services.ts, which owns the SSE plumbing.
 */
import type {
  AdminAiResponseReportResponse,
  AdminAiUsageLogResponse,
  AiQuizGeneratorResponse,
  AiResponseReportStatus,
  AiStoryGeneratorResponse,
  AppLanguage,
  PageResponse,
  StudentAiResponseReportResponse,
} from '@/types/api'
import type {
  AdminAiReportCounts,
  AdminAiSettings,
  AdminAiSettingsUpdate,
  AdminAiUsageFilters,
  AdminAiUsageSummary,
  AiHelperStatus,
  AiReportPayload,
  PracticeCheck,
  PracticeCheckLesson,
  PracticeCheckResult,
  PracticeCheckType,
  XiaomeiReply,
  XiaomeiScenario,
  XiaomeiSuggestions,
  XiaomeiSummary,
  XiaomeiTurn,
} from '@/types/ai'

import { aiApi, apiDelete, apiDownload, apiGet, apiPost, apiPut } from './services'

/** The backend gives the AI provider up to 30 s; the browser waits a little longer than that. */
const AI_REQUEST_CONFIG = { timeout: 45_000 }
/** A recording is written down and then scored: two provider calls. */
const PRACTICE_CHECK_CONFIG = { timeout: 90_000 }

interface XiaomeiContext {
  scenario: XiaomeiScenario
  turns: readonly XiaomeiTurn[]
  languagePreference: AppLanguage
}

interface PracticeCheckUpload {
  type: PracticeCheckType
  file: Blob
  fileName: string
  targetText: string
  lessonId?: number | null
  /** Length of a recording in whole seconds, when the browser knows it. */
  durationSeconds?: number | null
  languagePreference: AppLanguage
}

function practiceCheckForm(upload: PracticeCheckUpload): FormData {
  const form = new FormData()
  form.append('type', upload.type)
  form.append('file', upload.file, upload.fileName)
  form.append('targetText', upload.targetText.trim())
  form.append('languagePreference', upload.languagePreference)
  if (upload.lessonId) form.append('lessonId', String(upload.lessonId))
  if (upload.durationSeconds && upload.durationSeconds > 0) {
    form.append('durationSeconds', String(Math.min(600, Math.max(1, Math.round(upload.durationSeconds)))))
  }
  return form
}

export const aiHelperApi = {
  status() {
    return apiGet<AiHelperStatus>('/ai/status')
  },
  chatStream: aiApi.chatStream,
  xiaomeiReply(context: XiaomeiContext & { message: string }) {
    return apiPost<XiaomeiReply>('/ai/conversation/reply', context, AI_REQUEST_CONFIG)
  },
  xiaomeiSuggestions(context: XiaomeiContext) {
    return apiPost<XiaomeiSuggestions>('/ai/conversation/suggestions', context, AI_REQUEST_CONFIG)
  },
  xiaomeiSummary(context: XiaomeiContext) {
    return apiPost<XiaomeiSummary>('/ai/conversation/summary', context, AI_REQUEST_CONFIG)
  },
  quiz(payload: { topic: string; numberOfQuestions: number; languagePreference: AppLanguage }) {
    return apiPost<AiQuizGeneratorResponse>('/ai/quiz-generator', payload, AI_REQUEST_CONFIG)
  },
  story(payload: { topic: string; languagePreference: AppLanguage }) {
    return apiPost<AiStoryGeneratorResponse>('/ai/story-generator', payload, AI_REQUEST_CONFIG)
  },
  checkPractice(upload: PracticeCheckUpload) {
    return apiPost<PracticeCheckResult>('/ai/practice-checks', practiceCheckForm(upload), PRACTICE_CHECK_CONFIG)
  },
  practiceChecks(page = 0, size = 5) {
    return apiGet<PageResponse<PracticeCheck>>('/ai/practice-checks', { params: { page, size } })
  },
  practiceCheckLessons() {
    return apiGet<PracticeCheckLesson[]>('/ai/practice-checks/lessons')
  },
  deletePracticeCheck(id: number) {
    return apiDelete<null>(`/ai/practice-checks/${id}`)
  },
  reports(page = 0, size = 20) {
    return apiGet<PageResponse<StudentAiResponseReportResponse>>('/ai/reports', { params: { page, size } })
  },
  report(payload: AiReportPayload) {
    return apiPost<StudentAiResponseReportResponse>('/ai/reports', payload)
  },
}

/** Drops the "all" (empty) filters so the request only names real ones. */
function usageParams(filters: AdminAiUsageFilters): Record<string, string> {
  return Object.fromEntries(Object.entries(filters).filter(([, value]) => Boolean(value))) as Record<string, string>
}

export const adminAiApi = {
  /** `status`: OPEN, CLOSED, RESOLVED, DISMISSED, or nothing for every report with open ones first. */
  reports(query: { status?: 'OPEN' | 'CLOSED' | AiResponseReportStatus; page?: number; size?: number } = {}) {
    return apiGet<PageResponse<AdminAiResponseReportResponse>>('/admin/ai/reports', { params: query })
  },
  reportCounts() {
    return apiGet<AdminAiReportCounts>('/admin/ai/reports/counts')
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
}
