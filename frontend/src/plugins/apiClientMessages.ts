import { documentLanguage } from '@/utils/locale'

/**
 * Client-side fallback messages of the API layer, used when the server does not send a readable message: status and
 * transport errors (`utils/apiError.ts`, SDD Table 6.3), malformed envelopes and AI chat stream failures
 * (`api/services.ts`). Server messages always take precedence.
 */
export const apiClientMessages = {
  en: {
    fallback: 'We could not complete that request. Please try again.',
    unauthorized: 'Please sign in again to continue.',
    forbidden: 'You do not have permission to do that.',
    notFound: 'This item is not available any more. Please go back and choose another one.',
    rateLimited: 'You are doing that too often. Please wait a moment and try again.',
    server: 'The learning service is having trouble right now. Please try again shortly.',
    timeout: 'The learning service took too long to respond. Please try again.',
    network: 'We cannot reach the learning service right now. Check your connection and try again.',
    rejected: 'The request was not accepted by the server.',
    unexpectedFormat: 'The server returned an unexpected response format.',
    missingData: 'Something went wrong. Please try again.',
    streamFallback: 'Unable to get an AI chat response.',
    streamIncomplete: 'The AI helper could not finish this answer. Please try again.',
    streamMissingBody: 'The AI helper could not start an answer. Please try again.',
    streamStopped: 'The AI answer stopped unexpectedly. Please try again.',
    streamTimeout: 'The AI helper took too long to answer. Please try again.',
  },
  ms: {
    fallback: 'Permintaan itu tidak dapat diselesaikan. Sila cuba lagi.',
    unauthorized: 'Sila log masuk semula untuk meneruskan.',
    forbidden: 'Anda tiada kebenaran untuk melakukan tindakan itu.',
    notFound: 'Item ini tidak lagi tersedia. Sila kembali dan pilih yang lain.',
    rateLimited: 'Anda melakukannya terlalu kerap. Sila tunggu sebentar dan cuba lagi.',
    server: 'Perkhidmatan pembelajaran sedang menghadapi masalah. Sila cuba sebentar lagi.',
    timeout: 'Perkhidmatan pembelajaran mengambil masa terlalu lama untuk bertindak balas. Sila cuba lagi.',
    network: 'Perkhidmatan pembelajaran tidak dapat dicapai. Semak sambungan anda dan cuba lagi.',
    rejected: 'Permintaan itu tidak diterima oleh pelayan.',
    unexpectedFormat: 'Pelayan memulangkan format respons yang tidak dijangka.',
    missingData: 'Berlaku masalah. Sila cuba lagi.',
    streamFallback: 'Respons sembang AI tidak dapat diperoleh.',
    streamIncomplete: 'Pembantu AI tidak dapat menyiapkan jawapan ini. Sila cuba lagi.',
    streamMissingBody: 'Pembantu AI tidak dapat mula menjawab. Sila cuba lagi.',
    streamStopped: 'Jawapan AI berhenti tanpa diduga. Sila cuba lagi.',
    streamTimeout: 'Pembantu AI mengambil masa terlalu lama untuk menjawab. Sila cuba lagi.',
  },
  'zh-CN': {
    fallback: '无法完成该请求，请重试。',
    unauthorized: '请重新登录后继续。',
    forbidden: '你无权执行此操作。',
    notFound: '该内容已不可用，请返回并选择其他内容。',
    rateLimited: '操作过于频繁，请稍等片刻再试。',
    server: '学习服务目前出现问题，请稍后重试。',
    timeout: '学习服务响应超时，请重试。',
    network: '目前无法连接学习服务，请检查网络连接后重试。',
    rejected: '服务器未接受该请求。',
    unexpectedFormat: '服务器返回了无法识别的响应格式。',
    missingData: '出现问题，请重试。',
    streamFallback: '无法获取 AI 对话回复。',
    streamIncomplete: 'AI 助手未能完成这次回答，请重试。',
    streamMissingBody: 'AI 助手未能开始回答，请重试。',
    streamStopped: 'AI 回答意外中断，请重试。',
    streamTimeout: 'AI 助手回答超时，请重试。',
  },
} as const

type ApiClientMessageKey = keyof (typeof apiClientMessages)['en']

/** Picks the bundle for the current document language (kept in sync with the UI locale by the ui store). */
export function apiClientMessage(key: ApiClientMessageKey): string {
  return apiClientMessages[documentLanguage()][key]
}
