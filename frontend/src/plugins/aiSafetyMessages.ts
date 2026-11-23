import type { AiResponseReportReason } from '@/types/api'

/** Shared wording for the AI disclaimer and the learner report form. */
export const aiSafetyMessages = {
  en: {
    disclaimer: 'AI can make mistakes.',
    responsibleAi: 'Responsible AI guidance ›',
    report: 'Report',
    reported: 'Reported',
    reportOpen: 'Open',
    reportResolved: 'Resolved',
    reportDismissed: 'Dismissed',
    reportLead: 'An admin replies in Ask SparkBot › My reports.',
    reportReason: 'Reason',
    reportNote: 'Note (optional)',
    reportNotePlaceholder: 'What looked wrong?',
    sendReport: 'Send report',
    reportError: 'The report could not be sent. Please try again.',
    reportSent: 'Report sent. An admin replies in Ask SparkBot › My reports.',
    reasonIncorrect: 'Incorrect content',
    reasonUnclear: 'Unclear explanation',
    reasonInappropriate: 'Unsafe or inappropriate',
    reasonScore: 'Score seems wrong',
    reasonMismatch: 'Doesn’t match what I wrote',
    reasonOther: 'Other',
  },
  ms: {
    disclaimer: 'AI boleh membuat kesilapan.',
    responsibleAi: 'Panduan AI Bertanggungjawab ›',
    report: 'Laporkan',
    reported: 'Telah dilaporkan',
    reportOpen: 'Terbuka',
    reportResolved: 'Diselesaikan',
    reportDismissed: 'Ditolak',
    reportLead: 'Pentadbir membalas dalam Tanya SparkBot › Laporan saya.',
    reportReason: 'Sebab',
    reportNote: 'Nota (pilihan)',
    reportNotePlaceholder: 'Apa yang kelihatan salah?',
    sendReport: 'Hantar laporan',
    reportError: 'Laporan tidak dapat dihantar. Sila cuba lagi.',
    reportSent: 'Laporan dihantar. Pentadbir membalas dalam Tanya SparkBot › Laporan saya.',
    reasonIncorrect: 'Kandungan tidak tepat',
    reasonUnclear: 'Penerangan tidak jelas',
    reasonInappropriate: 'Tidak selamat atau tidak sesuai',
    reasonScore: 'Markah kelihatan salah',
    reasonMismatch: 'Tidak sepadan dengan apa yang saya tulis',
    reasonOther: 'Lain-lain',
  },
  'zh-CN': {
    disclaimer: 'AI 可能会出错。',
    responsibleAi: '负责任 AI 指南 ›',
    report: '举报',
    reported: '已举报',
    reportOpen: '处理中',
    reportResolved: '已解决',
    reportDismissed: '已驳回',
    reportLead: '管理员会在“问 SparkBot › 我的举报”中回复。',
    reportReason: '原因',
    reportNote: '备注（选填）',
    reportNotePlaceholder: '哪里有问题？',
    sendReport: '发送举报',
    reportError: '无法发送举报，请再试一次。',
    reportSent: '举报已发送。管理员会在“问 SparkBot › 我的举报”中回复。',
    reasonIncorrect: '内容不正确',
    reasonUnclear: '解释不清楚',
    reasonInappropriate: '不安全或不恰当',
    reasonScore: '分数似乎不对',
    reasonMismatch: '与我写的内容不符',
    reasonOther: '其他',
  },
} as const

/**
 * The platform's earlier default notice. Settings an admin saved without changing the notice still hold it, so it is
 * the platform default too: it names a teacher, and the platform has none.
 */
const RETIRED_DEFAULT_NOTICE = 'AI responses may contain errors and do not replace your teacher.'

/**
 * The AI notice as a reader sees it (the rule of the Responsible AI page): an admin's own wording as written; the
 * platform default, which the server stores and sends in English (PlatformSettingsService.DEFAULT_AI_DISCLAIMER_TEXT,
 * the same words as `aiSafety.disclaimer` in `en`, or the retired default), or no text at all, as
 * `aiSafety.disclaimer` in the reader's language. `translate` is the page's `t`, with this bundle registered as
 * `aiSafety`.
 */
export function shownAiNotice(serverText: string | null | undefined, translate: (key: string) => string): string {
  const text = serverText?.trim()
  if (!text || text === aiSafetyMessages.en.disclaimer || text === RETIRED_DEFAULT_NOTICE) {
    return translate('aiSafety.disclaimer')
  }
  return text
}

/** Full i18n key (bundle registered as `aiSafety`) for every report reason the backend accepts. */
export const AI_REPORT_REASON_KEYS = {
  INCORRECT_CONTENT: 'aiSafety.reasonIncorrect',
  UNCLEAR_EXPLANATION: 'aiSafety.reasonUnclear',
  INAPPROPRIATE_CONTENT: 'aiSafety.reasonInappropriate',
  SCORE_SEEMS_WRONG: 'aiSafety.reasonScore',
  DOES_NOT_MATCH_INPUT: 'aiSafety.reasonMismatch',
  OTHER: 'aiSafety.reasonOther',
} as const satisfies Record<AiResponseReportReason, `aiSafety.${keyof (typeof aiSafetyMessages)['en']}`>
