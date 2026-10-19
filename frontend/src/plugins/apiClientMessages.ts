import { documentLanguage } from '@/utils/locale'

/**
 * Client-side fallback messages raised by the API layer (`api/services.ts`) when the server does not send a
 * readable message: malformed envelopes and AI chat stream failures. Server messages always take precedence.
 */
export const apiClientMessages = {
  en: {
    rejected: 'The request was not accepted by the server.',
    unexpectedFormat: 'The server returned an unexpected response format.',
    missingData: 'The server response did not include the expected data payload.',
    streamFallback: 'Unable to get an AI chat response.',
    streamIncomplete: 'The AI stream ended before the final response arrived.',
    streamMalformedComplete: 'The AI stream returned a malformed final response.',
    streamMissingBody: 'The server did not open an AI stream.',
    streamRateLimited: 'You are doing that too often. Please wait a moment and try again.',
    streamServerTrouble: 'The learning service is having trouble right now. Please try again shortly.',
    streamSignInAgain: 'Please sign in again to continue.',
    streamStopped: 'The AI stream stopped unexpectedly.',
    streamTimeout: 'The AI stream took too long to respond. Please try again.',
  },
  ms: {
    rejected: 'Permintaan itu tidak diterima oleh pelayan.',
    unexpectedFormat: 'Pelayan memulangkan format respons yang tidak dijangka.',
    missingData: 'Respons pelayan tidak mengandungi data yang dijangka.',
    streamFallback: 'Respons sembang AI tidak dapat diperoleh.',
    streamIncomplete: 'Strim AI tamat sebelum respons akhir diterima.',
    streamMalformedComplete: 'Strim AI memulangkan respons akhir yang rosak.',
    streamMissingBody: 'Pelayan tidak membuka strim AI.',
    streamRateLimited: 'Anda melakukannya terlalu kerap. Sila tunggu sebentar dan cuba lagi.',
    streamServerTrouble: 'Perkhidmatan pembelajaran sedang menghadapi masalah. Sila cuba sebentar lagi.',
    streamSignInAgain: 'Sila log masuk semula untuk meneruskan.',
    streamStopped: 'Strim AI berhenti tanpa diduga.',
    streamTimeout: 'Strim AI mengambil masa terlalu lama untuk bertindak balas. Sila cuba lagi.',
  },
  'zh-CN': {
    rejected: '服务器未接受该请求。',
    unexpectedFormat: '服务器返回了无法识别的响应格式。',
    missingData: '服务器响应中缺少预期的数据。',
    streamFallback: '无法获取 AI 对话回复。',
    streamIncomplete: 'AI 回复流在最终结果到达前已结束。',
    streamMalformedComplete: 'AI 回复流返回的最终结果格式有误。',
    streamMissingBody: '服务器没有打开 AI 回复流。',
    streamRateLimited: '操作过于频繁，请稍等片刻再试。',
    streamServerTrouble: '学习服务目前出现问题，请稍后重试。',
    streamSignInAgain: '请重新登录后继续。',
    streamStopped: 'AI 回复流意外中断。',
    streamTimeout: 'AI 回复流响应超时，请重试。',
  },
} as const

export type ApiClientMessageKey = keyof (typeof apiClientMessages)['en']

/** Picks the bundle for the current document language (kept in sync with the UI locale by the ui store). */
export function apiClientMessage(key: ApiClientMessageKey): string {
  return apiClientMessages[documentLanguage()][key]
}
