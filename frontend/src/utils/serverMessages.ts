/**
 * Localised versions of the backend error and notice messages a user can see (FRLANG02).
 *
 * The backend answers in English (FriendlyErrorMessageResolver + service messages). When the interface language is
 * Malay or Simplified Chinese, a known message is replaced by its translation via an O(1) lookup table built lazily on
 * the first non-English call; an unknown message is returned unchanged so the backend text remains the fallback.
 *
 * In-app notifications live separately in `notificationMessages.ts` / `serverMessageRows/notifications.ts` so Vite
 * code-splits notification templates and the regex template matcher out of the main bundle into `NotificationsPage.vue`.
 *
 * Matching ignores case, repeated spaces and a final full stop. `serverMessages.test.ts` scans the backend Java
 * sources to verify every visible backend error message is translated and no stale row remains.
 */
import type { AppLanguage } from '@/types/api'
import { documentLanguage, normalizeAppLanguage } from '@/utils/locale'
import { truncateText } from '@/utils/truncateText'

import { AI_MESSAGES } from './serverMessageRows/ai'
import { CLASSES_MESSAGES, JOIN_CLASS_TO_OPEN_LESSONS_MESSAGE } from './serverMessageRows/classes'
import { LEARN_MESSAGES, RESTART_LEVEL_CHECK_MESSAGE } from './serverMessageRows/learn'
import { INVITE_ALREADY_ACCEPTED_MESSAGE, NO_PENDING_EMAIL_CHANGE_MESSAGE, OPS_MESSAGES } from './serverMessageRows/ops'
import {
  BUILD_PLAN_FIRST_MESSAGE,
  EMAIL_ALREADY_CONFIRMED_MESSAGE,
  GOAL_DATE_PASSED_MESSAGE,
  PLAN_MESSAGES,
} from './serverMessageRows/plan'
import type { MessageRow } from './serverMessageRows/types'

type TranslatedLocale = Exclude<AppLanguage, 'en'>

/**
 * Canonical English backend refusal messages matched by UI control flow via `hasServerMessage(...)`.
 * Every value here is embedded in `MESSAGE_ROWS`, so `serverMessages.test.ts` guards them against backend drift.
 */
export const SERVER_MESSAGE = {
  ACCOUNT_UNAVAILABLE: 'This account is currently unavailable. Please contact the platform administrator.',
  EMAIL_ALREADY_REGISTERED: 'Email is already registered.',
  WRONG_CURRENT_PASSWORD: 'Current password is incorrect.',
  NO_PENDING_EMAIL_CHANGE: NO_PENDING_EMAIL_CHANGE_MESSAGE,
  INVITE_ACCEPTED: INVITE_ALREADY_ACCEPTED_MESSAGE,
  EMAIL_ALREADY_CONFIRMED: EMAIL_ALREADY_CONFIRMED_MESSAGE,
  BUILD_PLAN_FIRST: BUILD_PLAN_FIRST_MESSAGE,
  GOAL_DATE_PASSED: GOAL_DATE_PASSED_MESSAGE,
  JOIN_CLASS_TO_OPEN_LESSONS: JOIN_CLASS_TO_OPEN_LESSONS_MESSAGE,
  RESTART_LEVEL_CHECK: RESTART_LEVEL_CHECK_MESSAGE,
} as const

/** The generic answers of FriendlyErrorMessageResolver and the sign-in, password and AI limit messages a user meets most. */
const GENERAL_MESSAGES: readonly MessageRow[] = [
  ['Invalid email or password.', 'E-mel atau kata laluan tidak sah.', '邮箱或密码不正确。'],
  [
    SERVER_MESSAGE.ACCOUNT_UNAVAILABLE,
    'Akaun ini tidak tersedia buat masa ini. Sila hubungi pentadbir platform.',
    '此账号目前无法使用，请联系平台管理员。',
  ],
  [SERVER_MESSAGE.EMAIL_ALREADY_REGISTERED, 'E-mel ini sudah didaftarkan.', '该邮箱已注册。'],
  ['Confirm password does not match.', 'Pengesahan kata laluan tidak sepadan.', '两次输入的密码不一致。'],
  [SERVER_MESSAGE.WRONG_CURRENT_PASSWORD, 'Kata laluan semasa tidak betul.', '当前密码不正确。'],
  [
    'New password must be different from the current password.',
    'Kata laluan baharu mestilah berbeza daripada kata laluan semasa.',
    '新密码必须与当前密码不同。',
  ],
  [
    'Reset token has expired. Please request a new one.',
    'Pautan tetapan semula telah tamat tempoh. Sila minta pautan baharu.',
    '重置链接已过期，请重新申请。',
  ],
  ['Reset token has already been used.', 'Pautan tetapan semula telah digunakan.', '重置链接已被使用。'],
  [
    'Reset token is invalid or has already been used.',
    'Pautan tetapan semula tidak sah atau telah digunakan.',
    '重置链接无效或已被使用。',
  ],
  [
    'Your session has expired. Please sign in again to continue.',
    'Sesi anda telah tamat. Sila log masuk semula untuk meneruskan.',
    '登录已过期，请重新登录后继续。',
  ],
  ['Please sign in to continue.', 'Sila log masuk untuk meneruskan.', '请先登录后继续。'],
  [
    'You do not have permission to perform this action. Contact an administrator if needed.',
    'Anda tiada kebenaran untuk melakukan tindakan ini. Hubungi pentadbir jika perlu.',
    '你无权执行此操作，如有需要请联系管理员。',
  ],
  [
    'We could not find the item you requested. Please refresh and try again.',
    'Item yang anda minta tidak ditemui. Sila muat semula dan cuba lagi.',
    '找不到你请求的内容，请刷新后重试。',
  ],
  [
    'The information you entered is incomplete or invalid. Please check it and try again.',
    'Maklumat yang dimasukkan tidak lengkap atau tidak sah. Sila semak dan cuba lagi.',
    '输入的信息不完整或无效，请检查后重试。',
  ],
  [
    'The request could not be processed. Please check the input and try again.',
    'Permintaan tidak dapat diproses. Sila semak input dan cuba lagi.',
    '无法处理该请求，请检查输入后重试。',
  ],
  [
    'The file upload did not work. Please check the file type and size, then try again.',
    'Muat naik fail tidak berjaya. Sila semak jenis dan saiz fail, kemudian cuba lagi.',
    '文件上传失败，请检查文件类型和大小后重试。',
  ],
  [
    'The file is too large. Please choose a smaller file and try again.',
    'Fail terlalu besar. Sila pilih fail yang lebih kecil dan cuba lagi.',
    '文件过大，请选择较小的文件后重试。',
  ],
  [
    'The service is temporarily unavailable. Please try again later.',
    'Perkhidmatan tidak tersedia buat sementara waktu. Sila cuba sebentar lagi.',
    '服务暂时不可用，请稍后重试。',
  ],
  [
    'You are doing that too often. Please wait a moment and try again.',
    'Anda melakukannya terlalu kerap. Sila tunggu sebentar dan cuba lagi.',
    '操作过于频繁，请稍候再试。',
  ],
  [
    'Daily AI request limit reached. Please try again tomorrow.',
    'Had permintaan AI harian telah dicapai. Sila cuba lagi esok.',
    '今天的 AI 使用次数已达上限，请明天再试。',
  ],
  [
    'AI tutor is currently unavailable. Please try again later.',
    'Pembantu AI tidak tersedia buat masa ini. Sila cuba sebentar lagi.',
    'AI 助手暂时不可用，请稍后再试。',
  ],
  // The `error` event of the SparkBot chat stream (AiChatStreamRunner), which is not an exception message.
  ['AI chat stream failed. Please try again.', 'Sembang AI terhenti. Sila cuba lagi.', 'AI 聊天中断了，请重试。'],
  [
    'This request was not sent to the AI helper because it may contain unsafe content. Please keep AI requests to Mandarin learning.',
    'Permintaan ini tidak dihantar kepada pembantu AI kerana mungkin mengandungi kandungan tidak selamat. Sila hadkan permintaan AI kepada pembelajaran bahasa Mandarin.',
    '此请求可能包含不安全内容，未发送给 AI 助手。请只就中文学习提问。',
  ],
  [
    'The AI reply was withheld by the safety check. Please rephrase your Mandarin-learning question.',
    'Jawapan AI telah ditahan oleh semakan keselamatan. Sila tulis semula soalan pembelajaran Mandarin anda.',
    'AI 回复未通过安全检查，已被隐藏。请换一种方式提出中文学习问题。',
  ],
  [
    'The request conflicts with the current state. Please refresh and try again.',
    'Permintaan bercanggah dengan keadaan semasa. Sila muat semula dan cuba lagi.',
    '该请求与当前状态冲突，请刷新后重试。',
  ],
  [
    'We could not complete that action. Please try again shortly.',
    'Tindakan itu tidak dapat diselesaikan. Sila cuba sebentar lagi.',
    '无法完成该操作，请稍后重试。',
  ],
]

/** Cross-cutting content-safety and upload/file request errors shared across modules. */
const SHARED_REQUEST_MESSAGES: readonly MessageRow[] = [
  // Content safety
  [
    'Content contains language that is not suitable for this learning platform.',
    'Kandungan ini mengandungi bahasa yang tidak sesuai untuk platform pembelajaran ini.',
    '内容包含不适合本学习平台的用语。',
  ],

  // Uploads and files
  ['Uploaded file is required.', 'Sila muat naik fail.', '请上传文件。'],
  ['Uploaded file name is invalid.', 'Nama fail yang dimuat naik tidak sah.', '上传的文件名无效。'],
  ['Invalid upload target path.', 'Lokasi simpanan fail tidak sah.', '文件保存位置无效。'],
  [
    'Uploaded file must include a valid extension.',
    'Fail yang dimuat naik mesti mempunyai sambungan nama fail yang sah.',
    '上传的文件必须带有有效的扩展名。',
  ],
  [
    'Uploaded video must include a valid extension. Allowed types are MP4, WEBM, and MOV.',
    'Video yang dimuat naik mesti mempunyai sambungan nama fail yang sah. Jenis yang dibenarkan ialah MP4, WEBM dan MOV.',
    '上传的视频必须带有有效的扩展名。允许的类型为 MP4、WEBM 和 MOV。',
  ],
  [
    'Unsupported audio file type. Allowed types are WEBM, WAV, MPEG, and MP3.',
    'Jenis fail audio tidak disokong. Jenis yang dibenarkan ialah WEBM, WAV, MPEG dan MP3.',
    '不支持该音频文件类型。允许的类型为 WEBM、WAV、MPEG 和 MP3。',
  ],
  [
    'Unsupported image file type. Allowed types are PNG, JPG, and JPEG.',
    'Jenis fail imej tidak disokong. Jenis yang dibenarkan ialah PNG, JPG dan JPEG.',
    '不支持该图片文件类型。允许的类型为 PNG、JPG 和 JPEG。',
  ],
  [
    'Unsupported video type. Allowed types are MP4, WEBM, and MOV.',
    'Jenis video tidak disokong. Jenis yang dibenarkan ialah MP4, WEBM dan MOV.',
    '不支持该视频类型。允许的类型为 MP4、WEBM 和 MOV。',
  ],
  [
    'Unsupported video content type. Allowed types are video/mp4, video/webm, and video/quicktime.',
    'Jenis kandungan video tidak disokong. Jenis yang dibenarkan ialah video/mp4, video/webm dan video/quicktime.',
    '不支持该视频内容类型。允许的类型为 video/mp4、video/webm 和 video/quicktime。',
  ],
  [
    'Uploaded audio content does not match the declared WEBM/WAV/MP3 type.',
    'Kandungan audio yang dimuat naik tidak sepadan dengan jenis WEBM/WAV/MP3 yang dinyatakan.',
    '上传的音频内容与声明的 WEBM/WAV/MP3 类型不符。',
  ],
  [
    'Uploaded image content does not match the declared PNG/JPG/JPEG type.',
    'Kandungan imej yang dimuat naik tidak sepadan dengan jenis PNG/JPG/JPEG yang dinyatakan.',
    '上传的图片内容与声明的 PNG/JPG/JPEG 类型不符。',
  ],
  [
    'Uploaded video content does not match the declared MP4/WEBM/MOV type.',
    'Kandungan video yang dimuat naik tidak sepadan dengan jenis MP4/WEBM/MOV yang dinyatakan.',
    '上传的视频内容与声明的 MP4/WEBM/MOV 类型不符。',
  ],
  ['Requested file could not be found.', 'Fail yang diminta tidak ditemui.', '找不到请求的文件。'],
  ['Requested video could not be found.', 'Video yang diminta tidak ditemui.', '找不到请求的视频。'],
]

/** Every fixed API/request message row; module-specific rows live in `serverMessageRows/`. Exported for catalogue tests. */
export const MESSAGE_ROWS: readonly MessageRow[] = [
  ...GENERAL_MESSAGES,
  ...SHARED_REQUEST_MESSAGES,
  ...LEARN_MESSAGES,
  ...CLASSES_MESSAGES,
  ...AI_MESSAGES,
  ...OPS_MESSAGES,
  ...PLAN_MESSAGES,
]

export function collapseWhitespace(message: string): string {
  return message.replace(/\s+/g, ' ').trim()
}

/** Lookup key: case, repeated spaces and one final full stop do not matter ("Title is required" = "Title is required."). */
export function messageKey(message: string): string {
  return collapseWhitespace(message).toLowerCase().replace(/[.]$/, '')
}

let fixedMessages: Map<string, Readonly<Record<TranslatedLocale, string>>> | null = null

/** Shows a known backend request/error message in the interface language; unknown text is returned unchanged. */
export function localizeServerMessage(message: string, locale: string = documentLanguage()): string {
  const language = normalizeAppLanguage(locale) ?? 'en'
  if (language === 'en') return message
  if (!fixedMessages) {
    fixedMessages = new Map()
    for (const row of MESSAGE_ROWS) {
      fixedMessages.set(messageKey(row[0]), { ms: row[1], 'zh-CN': row[2] })
    }
  }
  return fixedMessages.get(messageKey(message))?.[language] ?? message
}

const MAX_USER_MESSAGE_LENGTH = 280
// eslint-disable-next-line no-control-regex
const UNSAFE_MESSAGE_CHARACTERS = /[\u0000-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/g
const TRANSPORT_INTERNAL_MESSAGES = new Set(['Request failed', 'Network Error'])

/**
 * A server message fit to show: control and bidi characters removed, spaces collapsed, at most 280 characters; null
 * for a non-string, an empty text or a bare transport error ("Network Error").
 */
export function cleanServerMessage(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null
  }

  const trimmed = value.replace(UNSAFE_MESSAGE_CHARACTERS, ' ').replace(/\s+/g, ' ').trim()
  if (!trimmed || TRANSPORT_INTERNAL_MESSAGES.has(trimmed)) {
    return null
  }

  return trimmed.length > MAX_USER_MESSAGE_LENGTH ? `${truncateText(trimmed, MAX_USER_MESSAGE_LENGTH - 3)}...` : trimmed
}

/**
 * A message the server may send (e.g. why an AI request was not answered) in the interface language, or `fallback`
 * when it sent none or sent an unsafe/transport-only text.
 */
export function serverMessageOr(message: string | null | undefined, fallback: string): string {
  const cleaned = cleanServerMessage(message)
  return cleaned ? localizeServerMessage(cleaned, documentLanguage()) : fallback
}
