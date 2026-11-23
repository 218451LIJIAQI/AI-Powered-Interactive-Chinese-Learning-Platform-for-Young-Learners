import { describe, expect, it, vi } from 'vitest'

import { aiSafetyMessages, shownAiNotice } from '@/plugins/aiSafetyMessages'
import {
  BackendMessageExtractor,
  javaStringLiterals,
  messageText,
  type ExtractedMessage,
  type MessageParts,
  type NotificationText,
} from '@/test/backendMessageScanner'

import { resolveApiError } from './apiError'
import { localizeNotificationMessage, placeholderNames } from './notificationMessages'
import { NOTIFICATION_MESSAGES, RETIRED_NOTIFICATION_MESSAGES } from './serverMessageRows/notifications'
import { localizeServerMessage, MESSAGE_ROWS, messageKey, SERVER_MESSAGE, serverMessageOr } from './serverMessages'

function apiError(status: number, message: string) {
  return { response: { status, data: { message } } }
}

function translations(message: string) {
  return { ms: localizeServerMessage(message, 'ms'), 'zh-CN': localizeServerMessage(message, 'zh-CN') }
}

function notificationTranslations(message: string) {
  return { ms: localizeNotificationMessage(message, 'ms'), 'zh-CN': localizeNotificationMessage(message, 'zh-CN') }
}

describe('localised backend error messages (FRLANG02)', () => {
  it('keeps English and unknown messages unchanged (backend text is the fallback)', () => {
    expect(localizeServerMessage('Invalid email or password.', 'en')).toBe('Invalid email or password.')
    expect(localizeServerMessage('Lesson 12 has no quiz yet.', 'ms')).toBe('Lesson 12 has no quiz yet.')
    // Notification templates do not run against API errors, preventing false-match collisions.
    expect(localizeServerMessage('About something unexpected.', 'ms')).toBe('About something unexpected.')
  })

  it('keeps the daily AI limit distinct from the generic rate-limit message', () => {
    const daily = 'Daily AI request limit reached. Please try again tomorrow.'
    expect(localizeServerMessage(daily, 'en')).toBe(daily)
    expect(localizeServerMessage(daily, 'zh-CN')).toBe('今天的 AI 使用次数已达上限，请明天再试。')
    expect(localizeServerMessage(daily, 'ms')).toBe('Had permintaan AI harian telah dicapai. Sila cuba lagi esok.')
  })

  it('translates the error event of the SparkBot chat stream, which no exception carries', () => {
    expect(translations('AI chat stream failed. Please try again.')).toEqual({
      ms: 'Sembang AI terhenti. Sila cuba lagi.',
      'zh-CN': 'AI 聊天中断了，请重试。',
    })
  })

  it('translates the AI Helper practice check messages', () => {
    const nothingHeard =
      "SparkBot couldn't hear any speech in this recording. Record it again a little closer to the microphone."
    expect(localizeServerMessage(nothingHeard, 'zh-CN')).toBe(
      'SparkBot 没有在这段录音里听到说话声。请靠近麦克风再录一次。',
    )
    expect(localizeServerMessage('Practice check was not found.', 'ms')).toBe('Semakan latihan tidak ditemui.')
  })

  it('shows a message the server sent in the page language, or the fallback when it sent none', () => {
    const daily = 'Daily AI request limit reached. Please try again tomorrow.'
    expect(serverMessageOr(daily, 'Try again')).toBe(daily)
    expect(serverMessageOr(null, 'Try again')).toBe('Try again')
    expect(serverMessageOr('', 'Try again')).toBe('Try again')

    document.documentElement.lang = 'zh-CN'
    expect(serverMessageOr(daily, '再试一次')).toBe('今天的 AI 使用次数已达上限，请明天再试。')
    expect(serverMessageOr(undefined, '再试一次')).toBe('再试一次')
  })

  it('has no catalogue row that speaks of the backend configuration', () => {
    const technical = MESSAGE_ROWS.map(([en]) => en).filter((en) => /configur/i.test(en))
    expect(technical).toEqual([])
  })

  it('translates the rules of the admin class editor', () => {
    expect(translations('A lesson quiz has exactly 5 questions. Delete one to add another.')).toEqual({
      ms: 'Kuiz pelajaran mempunyai tepat 5 soalan. Padamkan satu untuk menambah yang lain.',
      'zh-CN': '课时测验正好有 5 道题。请先删除一道再添加。',
    })
    // The backend writes some rules with and some without a final full stop; both read the same.
    expect(localizeServerMessage('To publish a class, give it 2 units with 4 to 6 published lessons', 'zh-CN')).toBe(
      '要发布课堂，需要有 2 个单元，共 4 到 6 节已发布的课时。',
    )
  })

  it('keeps two different reset-link answers apart in translation', () => {
    expect(translations('Reset token has already been used.')).toEqual({
      ms: 'Pautan tetapan semula telah digunakan.',
      'zh-CN': '重置链接已被使用。',
    })
  })

  it('matches messages regardless of case and spacing', () => {
    expect(localizeServerMessage('  invalid EMAIL or   password. ', 'zh-CN')).toBe('邮箱或密码不正确。')
  })

  it('resolveApiError shows the login, registration, reset and validation errors in the interface language', () => {
    document.documentElement.lang = 'ms'
    expect(resolveApiError(apiError(401, 'Invalid email or password.'))).toBe('E-mel atau kata laluan tidak sah.')
    expect(resolveApiError(apiError(400, SERVER_MESSAGE.EMAIL_ALREADY_REGISTERED))).toBe('E-mel ini sudah didaftarkan.')

    document.documentElement.lang = 'zh-CN'
    expect(resolveApiError(apiError(400, 'Reset token has expired. Please request a new one.'))).toBe(
      '重置链接已过期，请重新申请。',
    )
    expect(
      resolveApiError(
        apiError(400, 'The information you entered is incomplete or invalid. Please check it and try again.'),
      ),
    ).toBe('输入的信息不完整或无效，请检查后重试。')
    expect(resolveApiError(apiError(400, 'Something only the server knows.'))).toBe('Something only the server knows.')
  })

  it('keeps every SERVER_MESSAGE constant registered in MESSAGE_ROWS', () => {
    const keys = new Set(MESSAGE_ROWS.map(([en]) => messageKey(en)))
    for (const value of Object.values(SERVER_MESSAGE)) {
      expect(keys.has(messageKey(value)), value).toBe(true)
    }
  })

  it('localises default and retired AI notices while keeping custom admin notices verbatim', () => {
    const tMs = (key: string) => (key === 'aiSafety.disclaimer' ? aiSafetyMessages.ms.disclaimer : key)
    expect(shownAiNotice(null, tMs)).toBe(aiSafetyMessages.ms.disclaimer)
    expect(shownAiNotice(undefined, tMs)).toBe(aiSafetyMessages.ms.disclaimer)
    expect(shownAiNotice('   ', tMs)).toBe(aiSafetyMessages.ms.disclaimer)
    expect(shownAiNotice(aiSafetyMessages.en.disclaimer, tMs)).toBe(aiSafetyMessages.ms.disclaimer)
    expect(shownAiNotice('AI responses may contain errors and do not replace your teacher.', tMs)).toBe(
      aiSafetyMessages.ms.disclaimer,
    )
    expect(shownAiNotice('  Check important answers with your lesson.  ', tMs)).toBe(
      'Check important answers with your lesson.',
    )
  })
})

describe('request messages and catalogue consistency (FRLANG02)', () => {
  const LINK_RULE = 'link must be a full http:// or https:// web address without a user name or password.'

  it('translates the reasons of failed requests', () => {
    expect(translations('Quiz was not found.')).toEqual({ ms: 'Kuiz tidak ditemui.', 'zh-CN': '找不到该测验。' })
    expect(localizeServerMessage('  activity   WAS not found ', 'ms')).toBe('Aktiviti tidak ditemui.')
    expect(localizeServerMessage('Unsupported image file type. Allowed types are PNG, JPG, and JPEG.', 'zh-CN')).toBe(
      '不支持该图片文件类型。允许的类型为 PNG、JPG 和 JPEG。',
    )
    expect(localizeServerMessage('Title is required.', 'zh-CN')).toBe('请填写标题。')

    document.documentElement.lang = 'ms'
    expect(resolveApiError(apiError(400, 'Selected learning category does not exist.'))).toBe(
      'Kategori pembelajaran yang dipilih tidak wujud.',
    )
    document.documentElement.lang = 'zh-CN'
    expect(resolveApiError(apiError(404, 'Practice check was not found.'))).toBe('找不到该练习检查。')
  })

  it('translates the link rule that the backend builds with the word Audio or Image', () => {
    expect(translations(`Audio ${LINK_RULE}`)).toEqual({
      ms: 'Pautan audio mestilah alamat web penuh yang bermula dengan http:// atau https://, tanpa nama pengguna atau kata laluan.',
      'zh-CN': '音频链接必须是以 http:// 或 https:// 开头的完整网址，且不能包含用户名或密码。',
    })
    expect(localizeServerMessage(`  IMAGE   ${LINK_RULE} `, 'zh-CN')).toBe(
      '图片链接必须是以 http:// 或 https:// 开头的完整网址，且不能包含用户名或密码。',
    )
    // A word outside the fixed list is not guessed: the whole message stays in English.
    expect(localizeServerMessage(`Video ${LINK_RULE}`, 'ms')).toBe(`Video ${LINK_RULE}`)
  })

  it('has a complete, duplicate-free, placeholder-free request catalogue', () => {
    for (const [en, ms, zh] of MESSAGE_ROWS) {
      expect(ms.trim(), en).not.toBe('')
      expect(zh.trim(), en).not.toBe('')
      expect(ms, en).not.toBe(en)
      expect(zh, en).not.toBe(en)
      expect(placeholderNames(en), `${en} belongs in notificationMessages`).toEqual([])
    }
    const keys = MESSAGE_ROWS.map(([en]) => messageKey(en))
    expect(keys.filter((key, index) => keys.indexOf(key) !== index)).toEqual([])
  })
})

const RESOLVER = 'FriendlyErrorMessageResolver'
const PACKAGE_ROOT = 'com/example/aichinese/'

/**
 * FriendlyErrorMessageResolver.resolve, read for one message: true when the user sees the message as sent. Any 5xx
 * answer is always replaced by the generic unavailable message; a 401 (apart from bad credentials), 403 or 429 answer
 * is replaced by a generic message unless the message carries one of the listed pass-through hints.
 */
function shownAsSent(status: number, message: string, passthroughHints: string[], badCredentialsHint: string): boolean {
  if (status >= 500) return false
  const normalized = message.toLowerCase()
  if (passthroughHints.some((hint) => normalized.includes(hint))) return true
  if (status === 401) return normalized.includes(badCredentialsHint)
  return status !== 403 && status !== 429
}

const isTemplate = (message: ExtractedMessage) => message.parts.some((p) => 'hole' in p)

function isUntranslated(message: string): boolean {
  const translated = translations(message)
  return translated.ms === message || translated['zh-CN'] === message
}

// Only the backend contract checks below read the sibling backend repo. Keep Node globals out of the browser TS config.
const fs = await vi.importActual<{
  existsSync(directory: URL): boolean
  realpathSync(directory: URL): string
  readdirSync(directory: string, options: { recursive: true; encoding: 'utf8' }): string[]
  readFileSync(file: string, encoding: 'utf8'): string
}>('node:fs')
// A direct new URL(..., import.meta.url) is rewritten by Vite as a browser asset.
const testFileUrl = import.meta.url
const backendJavaUrl = new URL('../../../backend/src/main/java/', testFileUrl)
const hasBackendSources = fs.existsSync(backendJavaUrl)

function readBackendFiles(): Array<{ path: string; source: string }> {
  const javaRoot = fs.realpathSync(backendJavaUrl)
  return fs
    .readdirSync(javaRoot, { recursive: true, encoding: 'utf8' })
    .filter((file) => file.endsWith('.java'))
    .map((file) => ({ path: file.replaceAll('\\', '/'), source: fs.readFileSync(javaRoot + '/' + file, 'utf8') }))
}

let scanned: {
  extractor: BackendMessageExtractor
  passthroughHints: string[]
  badCredentialsHint: string
  extracted: ExtractedMessage[]
  shown: ExtractedMessage[]
} | null = null

/** The backend messages a user can see, read once from the Java sources. */
function scanBackend() {
  if (scanned) return scanned
  const extractor = new BackendMessageExtractor(readBackendFiles())
  const passthroughHints = extractor.stringList(RESOLVER, 'EXPLICIT_PASSTHROUGH_HINTS')
  const badCredentialsHint = extractor.stringConstant(RESOLVER, 'BAD_CREDENTIALS_HINT') ?? ''

  const extracted = extractor.extract()
  const shown = extracted.filter((message) => {
    // An unrecognized expression has no text to translate. The source coverage assertion below rejects it.
    if (message.parts.length === 1 && 'hole' in message.parts[0]) return false
    return shownAsSent(message.status, messageText(message.parts), passthroughHints, badCredentialsHint)
  })
  scanned = { extractor, passthroughHints, badCredentialsHint, extracted, shown }
  return scanned
}

describe.runIf(hasBackendSources)('every backend message a user can see is translated (FRLANG02)', () => {
  it('reads the resolver pass-through and bad-credentials hints from backend sources', () => {
    const result = scanBackend()
    expect(result.passthroughHints.length, `${RESOLVER} pass-through hints`).toBeGreaterThan(0)
    expect(result.badCredentialsHint).toBe('invalid email or password')
  })

  it('keeps aiSafetyMessages.en.disclaimer aligned with PlatformSettingsService.DEFAULT_AI_DISCLAIMER_TEXT', () => {
    expect(scanBackend().extractor.stringConstant('PlatformSettingsService', 'DEFAULT_AI_DISCLAIMER_TEXT')).toBe(
      aiSafetyMessages.en.disclaimer,
    )
  })

  it('translates every fixed reason of a failed request', () => {
    const messages = scanBackend().shown.filter((m) => !isTemplate(m))
    // A guard against a scan that silently finds nothing after a change in the backend's code style.
    expect(messages.length).toBeGreaterThan(150)
    const untranslated = [...new Set(messages.map((m) => messageText(m.parts)))].filter(isUntranslated)
    expect(untranslated).toEqual([])
  })

  it('translates every message the backend builds from parts', () => {
    const templates = scanBackend().shown.filter((m) => isTemplate(m) && !m.example)
    const built = [...new Set(templates.map((m) => messageText(m.parts)))]
    expect(built.sort()).toEqual([
      '{} link must be a full http:// or https:// web address without a user name or password.',
    ])
  })

  it('translates every finished message the backend builds from a value it passes in', () => {
    const { shown } = scanBackend()
    const examples = [...new Set(shown.filter((m) => m.example).map((m) => messageText(m.parts)))]
    expect(examples.length).toBeGreaterThan(1)
    expect(examples.filter(isUntranslated)).toEqual([])
  })

  it('can read how every message is written, so a new form cannot slip past the checks above', () => {
    const opaque = new Set<string>()
    for (const { parts } of scanBackend().extracted) {
      const [only] = parts
      if (parts.length === 1 && 'hole' in only) opaque.add(only.hole)
    }
    expect([...opaque].sort()).toEqual([])
  })

  it('has no row for a message the backend no longer sends', () => {
    const { extracted, shown } = scanBackend()
    const fixed = new Set(extracted.filter((m) => !isTemplate(m)).map((m) => messageKey(messageText(m.parts))))
    const built = shown
      .filter(isTemplate)
      .map((m) => new RegExp(`^${messageKey(messageText(m.parts)).split('{}').map(escapeRegExp).join('.+')}$`))
    const literals = javaStringLiterals(scanBackend().extractor.files)
    const stale = MESSAGE_ROWS.map(([en]) => en).filter((en) => {
      const key = messageKey(en)
      return !fixed.has(key) && !literals.has(key) && !built.some((pattern) => pattern.test(key))
    })
    expect(stale).toEqual([])
  })
})

/** A stand-in for a value the backend puts in a notification: spaces, punctuation and Chinese. */
const SAMPLE_VALUE = 'Mei Ling 美玲 (Year 1)'
/** The reason in the admins' "AI report needs review" message: the report's category, words in lower case. */
const REASON_HOLE = /\.getReasonCategory\(\)\.name\(\)\.replace\(_, \)\.toLowerCase\(Locale\.ROOT\)$/

let notificationScan: { texts: NotificationText[]; reasons: string[] } | null = null

/** The notification texts of the backend and the reasons of an AI report as such a text spells them, read once. */
function scanNotifications() {
  if (notificationScan) return notificationScan
  const { extractor } = scanBackend()
  const texts = extractor.notificationTexts()
  const { tokens } = extractor.files.find(({ path }) => path.endsWith('entity/enums/AiResponseReportReason.java'))!
  const body = tokens.findIndex((token) => token.value === '{')
  const end = tokens.findIndex((token, index) => index > body && (token.value === ';' || token.value === '}'))
  const reasons = tokens
    .slice(body + 1, end)
    .flatMap((token) =>
      token.kind === 'word' && /^[A-Z0-9_]+$/.test(token.value) ? [token.value.replace(/_/g, ' ').toLowerCase()] : [],
    )
  notificationScan = { texts, reasons }
  return notificationScan
}

function holeValues(hole: string, reasons: string[]): string[] {
  if (hole.includes('getReasonCategory()')) {
    return reasons
  }
  return [SAMPLE_VALUE]
}

function textSamples(parts: MessageParts, reasons: string[]): string[] {
  return parts.reduce<string[]>(
    (partial, part) => {
      const options = 'lit' in part ? [part.lit] : holeValues('hole' in part ? part.hole : '', reasons)
      return partial.flatMap((prefix) => options.map((option) => prefix + option))
    },
    [''],
  )
}

function notificationSamples(): Set<string> {
  const { texts, reasons } = scanNotifications()
  return new Set(texts.flatMap(({ parts }) => textSamples(parts, reasons)).map(messageKey))
}

function rowSample(en: string): string {
  return en.replace(/\{([A-Za-z][A-Za-z0-9]*)\}/g, SAMPLE_VALUE)
}

const hasWords = (parts: MessageParts) => parts.some((p) => 'lit' in p && /[A-Za-z]/.test(p.lit))

const RETIRED_NOTIFICATIONS: ReadonlySet<string> = new Set(RETIRED_NOTIFICATION_MESSAGES.map(([en]) => en))

describe.runIf(hasBackendSources)('every notification title and message is translated (FRLANG02)', () => {
  it('finds the notifications of every place that writes them', () => {
    const { texts } = scanNotifications()
    expect(texts.filter((text) => text.field === 'title').length).toBeGreaterThan(5)
    expect(texts.filter((text) => text.field === 'message').length).toBeGreaterThan(8)
    expect([...new Set(texts.map((text) => text.file))].sort()).toEqual(
      [
        'config/seed/DemoOpsSeeder.java',
        'service/AiResponseReportService.java',
        'service/admin/AdminUserService.java',
        'service/learn/LearnerNotifications.java',
        'service/support/SupportService.java',
      ].map((path) => PACKAGE_ROOT + path),
    )
  })

  it('translates every title and message, keeping the values as the backend wrote them', () => {
    const { texts, reasons } = scanNotifications()
    const untranslated: string[] = []
    const values = (text: string) => text.split(SAMPLE_VALUE).length - 1
    for (const { parts } of texts.filter((text) => hasWords(text.parts))) {
      for (const message of textSamples(parts, reasons)) {
        for (const [locale, shown] of Object.entries(notificationTranslations(message))) {
          if (shown === message || values(shown) !== values(message)) untranslated.push(`${locale}: ${message}`)
        }
      }
    }
    expect(untranslated).toEqual([])
  })

  it('can read how every notification text is written', () => {
    const { texts } = scanNotifications()
    const wordless = texts.filter((text) => !hasWords(text.parts)).map((text) => messageText(text.parts))
    expect(wordless).toEqual([])
  })

  it('reads the reasons of an AI report the way the backend writes them', () => {
    const { texts, reasons } = scanNotifications()
    const reasonHoles = texts.flatMap(({ parts }) =>
      parts.flatMap((part) => ('hole' in part && part.hole.includes('getReasonCategory()') ? [part.hole] : [])),
    )
    expect(reasonHoles.length).toBeGreaterThan(0)
    for (const hole of reasonHoles) expect(hole).toMatch(REASON_HOLE)
    expect(reasons).toEqual([
      'incorrect content',
      'unclear explanation',
      'inappropriate content',
      'score seems wrong',
      'does not match input',
      'other',
    ])
  })

  it('has no notification row the backend does not write, except the retired wordings', () => {
    const samples = notificationSamples()
    const stale = NOTIFICATION_MESSAGES.map(([en]) => en).filter(
      (en) => !samples.has(messageKey(rowSample(en))) && !RETIRED_NOTIFICATIONS.has(en),
    )
    expect(stale).toEqual([])
  })

  it('keeps a retired wording only while the backend no longer writes it', () => {
    const samples = notificationSamples()
    expect(RETIRED_NOTIFICATIONS.size).toBeGreaterThan(0)
    expect([...RETIRED_NOTIFICATIONS].filter((en) => samples.has(messageKey(rowSample(en))))).toEqual([])
  })
})

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
