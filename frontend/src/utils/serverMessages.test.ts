import { describe, expect, it } from 'vitest'

import { apiFieldError, resolveApiError } from './apiError'
import { localizedServerMessage, localizeServerMessage, serverMessageCatalogue } from './serverMessages'

function apiError(status: number, message: string) {
  return { response: { status, data: { message, errors: [message] } } }
}

/** Every fixed message of the catalogue: [English, Malay, Chinese]. */
const catalogueRows = [
  ...serverMessageCatalogue.GENERAL_MESSAGES,
  ...serverMessageCatalogue.REQUEST_MESSAGES,
  ...serverMessageCatalogue.VALIDATION_MESSAGES,
  ...serverMessageCatalogue.MODULE_MESSAGES,
]

describe('localised backend error messages (FRLANG02)', () => {
  it('keeps English and unknown messages unchanged (backend text is the fallback)', () => {
    expect(localizeServerMessage('Invalid email or password.', 'en')).toBe('Invalid email or password.')
    expect(localizeServerMessage('Lesson 12 has no quiz yet.', 'ms')).toBe('Lesson 12 has no quiz yet.')
  })

  it('keeps the daily AI limit distinct from the generic rate-limit message', () => {
    const daily = 'Daily AI request limit reached. Please try again tomorrow.'
    expect(localizeServerMessage(daily, 'en')).toBe(daily)
    expect(localizeServerMessage(daily, 'zh-CN')).toBe('今天的 AI 使用次数已达上限，请明天再试。')
    expect(localizeServerMessage(daily, 'ms')).toBe('Had permintaan AI harian telah dicapai. Sila cuba lagi esok.')
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
    expect(localizedServerMessage(daily, 'Try again')).toBe(daily)
    expect(localizedServerMessage(null, 'Try again')).toBe('Try again')
    expect(localizedServerMessage('', 'Try again')).toBe('Try again')

    document.documentElement.lang = 'zh-CN'
    expect(localizedServerMessage(daily, '再试一次')).toBe('今天的 AI 使用次数已达上限，请明天再试。')
    expect(localizedServerMessage(undefined, '再试一次')).toBe('再试一次')
  })

  it('has no catalogue row that speaks of the backend configuration', () => {
    const technical = catalogueRows.map(([en]) => en).filter((en) => /configur/i.test(en))
    expect(technical).toEqual([])
  })

  it('translates the teacher bio rule and the live class messages', () => {
    expect(localizeServerMessage('Teacher bio is required', 'ms')).toBe('Biografi guru diperlukan.')
    expect(localizeServerMessage('Teacher bio must be between 20 and 1000 characters', 'zh-CN')).toBe(
      '教师简介必须为 20 到 1000 个字符。',
    )
    expect(localizeServerMessage('Start time must be in the future.', 'ms')).toBe(
      'Masa mula mestilah pada masa hadapan.',
    )
    expect(localizeServerMessage('This live class is not available.', 'zh-CN')).toBe('该直播课目前不可用。')
    expect(localizeServerMessage('This live class is not available.', 'en')).toBe('This live class is not available.')
  })

  it('matches messages regardless of case and spacing', () => {
    expect(localizeServerMessage('  invalid EMAIL or   password. ', 'zh-CN')).toBe('邮箱或密码不正确。')
  })

  it('resolveApiError shows the login, registration, reset and validation errors in the interface language', () => {
    document.documentElement.lang = 'ms'
    expect(resolveApiError(apiError(401, 'Invalid email or password.'))).toBe('E-mel atau kata laluan tidak sah.')
    expect(resolveApiError(apiError(400, 'Email is already registered.'))).toBe('E-mel ini sudah didaftarkan.')

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
})

/** The catalogue's lookup key: case, repeated spaces and one final full stop do not matter. */
function lookupKey(message: string): string {
  return message.toLowerCase().split(' ').filter(Boolean).join(' ').replace(/[.]$/, '')
}

function translations(message: string) {
  return { ms: localizeServerMessage(message, 'ms'), 'zh-CN': localizeServerMessage(message, 'zh-CN') }
}

function validationError(field: string, message: string) {
  const summary = 'The information you entered is incomplete or invalid. Please check it and try again.'
  return {
    response: { status: 400, data: { message: summary, errors: [summary], fieldErrors: { [field]: [message] } } },
  }
}

describe('request, validation and template messages (FRLANG02)', () => {
  it('translates the reasons of failed requests', () => {
    expect(translations('Quiz was not found.')).toEqual({ ms: 'Kuiz tidak ditemui.', 'zh-CN': '找不到该测验。' })
    expect(localizeServerMessage('Unsupported image file type. Allowed types are PNG, JPG, and JPEG.', 'zh-CN')).toBe(
      '不支持该图片文件类型。允许的类型为 PNG、JPG 和 JPEG。',
    )

    document.documentElement.lang = 'ms'
    expect(resolveApiError(apiError(400, 'Selected learning level does not exist.'))).toBe(
      'Tahap pembelajaran yang dipilih tidak wujud.',
    )
    document.documentElement.lang = 'zh-CN'
    expect(resolveApiError(apiError(404, 'Practice check was not found.'))).toBe('找不到该练习检查。')
  })

  it('translates the validation rule shown next to a form field', () => {
    document.documentElement.lang = 'zh-CN'
    expect(apiFieldError(validationError('title', 'Title is required.'), 'title')).toBe('请填写标题。')
    document.documentElement.lang = 'ms'
    expect(
      apiFieldError(validationError('meetingLink', 'Meeting link must be 500 characters or fewer.'), 'meetingLink'),
    ).toBe('Pautan mesyuarat tidak boleh melebihi 500 aksara.')
    // The backend writes some rules with and some without a final full stop; both read the same.
    expect(localizeServerMessage('Email is required', 'ms')).toBe('E-mel diperlukan.')
    expect(localizeServerMessage('Email is required.', 'ms')).toBe('E-mel diperlukan.')
    expect(localizeServerMessage('Reset token is required', 'zh-CN')).toBe('缺少重置链接。')
  })

  it('translates template messages together with the content label inside them', () => {
    expect(translations('Lesson was not found.')).toEqual({ ms: 'Pelajaran tidak ditemui.', 'zh-CN': '找不到该课时。' })
    expect(translations('Teacher profile was not found.').ms).toBe('Profil guru tidak ditemui.')
    expect(localizeServerMessage('  activity   WAS not found ', 'ms')).toBe('Aktiviti tidak ditemui.')
  })

  it('keeps user content inside a template exactly as sent', () => {
    const typed = 'Draft (old) 草稿 $1 .*+?^${}()|[]'
    expect(translations(`Unknown status: ${typed}`)).toEqual({
      ms: `Status tidak dikenali: ${typed}`,
      'zh-CN': `无法识别的状态：${typed}`,
    })
    expect(localizeServerMessage('Unsupported system setting key: ai.MODE', 'zh-CN')).toBe('不支持的系统设置：ai.MODE')
    // A label outside the fixed list is not guessed: the whole message stays in English.
    expect(localizeServerMessage('Homework was not found.', 'ms')).toBe('Homework was not found.')
  })

  it('keeps English as sent and falls back to English for unknown text', () => {
    for (const message of ['Lesson was not found.', 'Title is required.', 'Quiz was not found.', 'Unknown status: x']) {
      expect(localizeServerMessage(message, 'en')).toBe(message)
    }
    expect(localizeServerMessage('Lesson 12 has no quiz yet.', 'zh-CN')).toBe('Lesson 12 has no quiz yet.')
  })

  it('has a complete and consistent catalogue', () => {
    for (const [en, ms, zh] of catalogueRows) {
      expect(ms.trim(), en).not.toBe('')
      expect(zh.trim(), en).not.toBe('')
      expect(ms, en).not.toBe(en)
      expect(zh, en).not.toBe(en)
    }

    // Each English message is listed once, so it has one translation.
    const keys = catalogueRows.map(([en]) => lookupKey(en))
    expect(keys.filter((key, index) => keys.indexOf(key) !== index)).toEqual([])

    const placeholders = (text: string) => [...text.matchAll(/[{]([A-Za-z]+)[}]/g)].map((match) => match[1]).sort()
    for (const pattern of serverMessageCatalogue.MESSAGE_PATTERNS) {
      const names = placeholders(pattern.en)
      expect(names.length, pattern.en).toBeGreaterThan(0)
      expect(placeholders(pattern.ms), pattern.en).toEqual(names)
      expect(placeholders(pattern['zh-CN']), pattern.en).toEqual(names)
      expect(Object.keys(pattern.slots).sort(), pattern.en).toEqual([...new Set(names)])
      for (const slot of Object.values(pattern.slots)) {
        if (slot === 'text') continue
        for (const [word, translated] of Object.entries(slot)) {
          expect(word, pattern.en).toBe(word.toLowerCase())
          expect(translated.ms.trim(), word).not.toBe('')
          expect(translated['zh-CN'].trim(), word).not.toBe('')
        }
      }
    }
  })
})

/*
 * Completeness: the backend's Java sources are read, and every message that can reach a user must be translated.
 * The scan below is a small tokenizer for this code base's patterns, not a Java parser.
 */

type JavaToken = { kind: 'word' | 'text' | 'number' | 'mark' | 'char'; value: string; line: number }
/** A message part: fixed text, a parameter or record component of the code around it, or any other value. */
type MessagePart =
  | { lit: string }
  | { param: number; text: string }
  | { field: string; index: number; text: string }
  | { hole: string }
type MessageParts = MessagePart[]
type JavaMethod = { name: string; params: string[]; start: number; end: number }
type JavaFile = {
  path: string
  cls: string
  tokens: JavaToken[]
  methods: JavaMethod[]
  constants: Map<string, number>
  staticImports: Set<string>
}
type StatusExpression = { code: number | null } | { param: number }

type ExtractedMessage = {
  source: 'ResponseStatusException' | 'IllegalArgumentException' | 'validation'
  status: number | null
  parts: MessageParts
  file: string
  line: number
  /** A template message finished with a fixed value that the code passes in. */
  example: boolean
}

/** A parameter or record component that ends up in (or as) an exception message. */
type MessageSink = {
  kind: 'call' | 'new'
  cls: string
  name: string
  arity: number
  index: number
  status: StatusExpression
  template: MessageParts
  exception: ExtractedMessage['source']
  /** The value is part of a longer text (a template), not the whole message. */
  embedded: boolean
}

const BACKSLASH = String.fromCharCode(92)
const NEWLINE = String.fromCharCode(10)
const JAVA_ESCAPES: Readonly<Record<string, string>> = {
  n: NEWLINE,
  t: String.fromCharCode(9),
  r: String.fromCharCode(13),
  b: String.fromCharCode(8),
  f: String.fromCharCode(12),
  s: ' ',
}

const HTTP_STATUS: Readonly<Record<string, number>> = {
  BAD_REQUEST: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  CONFLICT: 409,
  PAYLOAD_TOO_LARGE: 413,
  TOO_MANY_REQUESTS: 429,
  INTERNAL_SERVER_ERROR: 500,
}

const NOT_A_METHOD = new Set(['if', 'for', 'while', 'switch', 'catch', 'synchronized', 'new', 'return', 'throw', 'try'])
const MODIFIERS = new Set(['static', 'final', 'private', 'public', 'protected'])
const OPENERS = new Set(['(', '[', '{'])
const CLOSERS = new Set([')', ']', '}'])

function unescapeJava(body: string): string {
  let out = ''
  for (let i = 0; i < body.length; i++) {
    if (body[i] !== BACKSLASH) {
      out += body[i]
      continue
    }
    const next = body[i + 1] ?? ''
    if (next === 'u') {
      let j = i + 1
      while (body[j] === 'u') j++
      out += String.fromCharCode(parseInt(body.slice(j, j + 4), 16))
      i = j + 3
    } else if (next >= '0' && next <= '7') {
      let j = i + 1
      while (j < i + 4 && body[j] >= '0' && body[j] <= '7') j++
      out += String.fromCharCode(parseInt(body.slice(i + 1, j), 8))
      i = j - 1
    } else {
      out += JAVA_ESCAPES[next] ?? next
      i++
    }
  }
  return out
}

function tokenize(src: string): JavaToken[] {
  const tokens: JavaToken[] = []
  let line = 1
  const countLines = (from: number, to: number) => {
    for (let k = from; k < to; k++) if (src[k] === NEWLINE) line++
  }
  let i = 0
  while (i < src.length) {
    const c = src[i]
    if (c.trim() === '') {
      if (c === NEWLINE) line++
      i++
    } else if (src.startsWith('//', i)) {
      while (i < src.length && src[i] !== NEWLINE) i++
    } else if (src.startsWith('/*', i)) {
      const end = src.indexOf('*/', i + 2)
      const stop = end < 0 ? src.length : end + 2
      countLines(i, stop)
      i = stop
    } else if (src.startsWith('"""', i)) {
      const end = src.indexOf('"""', i + 3)
      const stop = end < 0 ? src.length : end
      const lines = src.slice(src.indexOf(NEWLINE, i) + 1, stop).split(NEWLINE)
      const indent = Math.min(...lines.filter((l) => l.trim()).map((l) => l.length - l.trimStart().length))
      const text = lines.map((l) => l.slice(indent).trimEnd()).join(NEWLINE)
      tokens.push({ kind: 'text', value: unescapeJava(text.split(BACKSLASH + NEWLINE).join('')), line })
      countLines(i, stop + 3)
      i = stop + 3
    } else if (c === '"' || c === "'") {
      let j = i + 1
      while (j < src.length && src[j] !== c) j += src[j] === BACKSLASH ? 2 : 1
      tokens.push({ kind: c === '"' ? 'text' : 'char', value: unescapeJava(src.slice(i + 1, j)), line })
      i = j + 1
    } else if (/[A-Za-z_$]/.test(c)) {
      let j = i + 1
      while (j < src.length && /[A-Za-z0-9_$]/.test(src[j])) j++
      tokens.push({ kind: 'word', value: src.slice(i, j), line })
      i = j
    } else if (/[0-9]/.test(c)) {
      let j = i + 1
      while (j < src.length && /[A-Za-z0-9_.]/.test(src[j])) j++
      tokens.push({ kind: 'number', value: src.slice(i, j), line })
      i = j
    } else if (src.startsWith('->', i) || src.startsWith('::', i)) {
      tokens.push({ kind: 'mark', value: src.slice(i, i + 2), line })
      i += 2
    } else {
      tokens.push({ kind: 'mark', value: c, line })
      i++
    }
  }
  return tokens
}

function depthStep(token: JavaToken): number {
  if (token.kind !== 'mark') return 0
  return OPENERS.has(token.value) ? 1 : CLOSERS.has(token.value) ? -1 : 0
}

function matchClose(tokens: JavaToken[], open: number): number {
  let depth = 0
  for (let k = open; k < tokens.length; k++) {
    depth += depthStep(tokens[k])
    if (depth === 0) return k
  }
  return tokens.length - 1
}

/** Splits tokens[from..to) on top-level commas. */
function splitArgs(tokens: JavaToken[], from: number, to: number): Array<[number, number]> {
  const args: Array<[number, number]> = []
  let depth = 0
  let start = from
  for (let k = from; k < to; k++) {
    depth += depthStep(tokens[k])
    if (depth === 0 && tokens[k].kind === 'mark' && tokens[k].value === ',') {
      args.push([start, k])
      start = k + 1
    }
  }
  if (to > from) args.push([start, to])
  return args
}

/** End of the expression starting at `from`: the first top-level `;` or `,`, or an unmatched closing bracket. */
function expressionEnd(tokens: JavaToken[], from: number): number {
  let depth = 0
  for (let k = from; k < tokens.length; k++) {
    const token = tokens[k]
    if (token.kind === 'mark' && CLOSERS.has(token.value) && depth === 0) return k
    depth += depthStep(token)
    if (depth === 0 && token.kind === 'mark' && (token.value === ';' || token.value === ',')) return k
  }
  return tokens.length
}

function lastWord(tokens: JavaToken[], from: number, to: number): string {
  let last = ''
  for (let k = from; k < to; k++) if (tokens[k].kind === 'word') last = tokens[k].value
  return last
}

function findMethods(tokens: JavaToken[]): JavaMethod[] {
  const methods: JavaMethod[] = []
  for (let k = 1; k < tokens.length - 1; k++) {
    const token = tokens[k]
    if (token.kind !== 'word' || NOT_A_METHOD.has(token.value) || tokens[k + 1].value !== '(') continue
    const prev = tokens[k - 1]
    const declared =
      prev.kind === 'word' ? !NOT_A_METHOD.has(prev.value) && prev.value !== 'else' : ['>', ']'].includes(prev.value)
    if (!declared) continue
    const close = matchClose(tokens, k + 1)
    let body = close + 1
    if (tokens[body]?.value === 'throws') {
      while (body < tokens.length && !['{', ';'].includes(tokens[body].value)) body++
    }
    if (tokens[body]?.value !== '{') continue
    const params = splitArgs(tokens, k + 2, close).map(([s, e]) => lastWord(tokens, s, e))
    methods.push({ name: token.value, params, start: body, end: matchClose(tokens, body) })
  }
  return methods
}

/** `final` String, int and long fields or locals with an initialiser: name -> index of the initialiser. */
function findConstants(tokens: JavaToken[]): Map<string, number> {
  const constants = new Map<string, number>()
  for (let k = 1; k < tokens.length - 3; k++) {
    const typed = ['String', 'int', 'long'].includes(tokens[k].value)
    if (!typed || tokens[k + 1].kind !== 'word' || tokens[k + 2].value !== '=') continue
    let q = k - 1
    let isFinal = false
    while (q >= 0 && MODIFIERS.has(tokens[q].value)) if (tokens[q--].value === 'final') isFinal = true
    if (isFinal) constants.set(tokens[k + 1].value, k + 3)
  }
  return constants
}

/** `import static a.b.Cls.name;` gives `Cls.name`, and `import static a.b.Cls.*;` gives `Cls.*`. */
function findStaticImports(tokens: JavaToken[]): Set<string> {
  const imports = new Set<string>()
  for (let k = 0; k < tokens.length - 1; k++) {
    if (tokens[k].value !== 'import' || tokens[k + 1].value !== 'static') continue
    let end = k + 2
    while (end < tokens.length && tokens[end].value !== ';') end++
    imports.add(`${tokens[end - 3].value}.${tokens[end - 1].value}`)
  }
  return imports
}

/** `"... %s ...".formatted(x)` and `String.format("... %s ...", x)`: each format specifier is a value. */
function formatParts(format: string): MessageParts {
  const parts: MessageParts = []
  let last = 0
  for (const match of format.matchAll(/%([0-9]+[$])?[-#+ 0,(]*[0-9]*([.][0-9]+)?[sdf]/g)) {
    if (match.index > last) parts.push({ lit: format.slice(last, match.index) })
    parts.push({ hole: match[0] })
    last = match.index + match[0].length
  }
  if (last < format.length) parts.push({ lit: format.slice(last) })
  return parts
}

/**
 * Finds the messages the backend sends with a failed request: the message of every ResponseStatusException and
 * IllegalArgumentException (GlobalExceptionHandler answers the latter with HTTP 400), followed through constants,
 * local variables, suppliers, helper methods and record components (an upload policy's messages), plus every Bean
 * Validation message (`message = "..."` of a constraint annotation).
 */
class BackendMessageExtractor {
  private readonly files: JavaFile[] = []
  private readonly classes = new Map<string, JavaFile>()
  /** Record component name -> the records that declare it, with the component's position. */
  private readonly recordComponents = new Map<string, Array<{ record: string; index: number; arity: number }>>()

  constructor(sources: ReadonlyArray<{ path: string; source: string }>) {
    for (const { path, source } of sources) {
      const tokens = tokenize(source)
      const file: JavaFile = {
        path,
        cls: path.slice(path.lastIndexOf('/') + 1, -'.java'.length),
        tokens,
        methods: findMethods(tokens),
        constants: findConstants(tokens),
        staticImports: findStaticImports(tokens),
      }
      this.files.push(file)
      this.classes.set(file.cls, file)
      this.indexRecords(file)
    }
  }

  /** The `List.of("...", ...)` strings of a constant, e.g. the hint lists of FriendlyErrorMessageResolver. */
  stringList(cls: string, constant: string): string[] {
    const tokens = this.classes.get(cls)?.tokens ?? []
    for (let k = 0; k < tokens.length - 5; k++) {
      if (tokens[k].value !== constant || tokens[k + 1].value !== '=' || tokens[k + 4].value !== 'of') continue
      const close = matchClose(tokens, k + 5)
      return tokens.slice(k + 6, close).flatMap((token) => (token.kind === 'text' ? [token.value] : []))
    }
    return []
  }

  /** The constant names of an enum, and for `NAME("Label")` constants their labels. */
  enumConstants(cls: string): Array<{ name: string; label: string | null }> {
    const tokens = this.classes.get(cls)?.tokens ?? []
    const open = tokens.findIndex((token, k) => token.value === '{' && tokens[k - 2]?.value === 'enum')
    const constants: Array<{ name: string; label: string | null }> = []
    for (let k = open + 1; k < tokens.length && tokens[k].value !== ';'; k++) {
      const token = tokens[k]
      if (token.kind !== 'word' || !['{', ','].includes(tokens[k - 1].value)) continue
      const label = tokens[k + 1].value === '(' && tokens[k + 2].kind === 'text' ? tokens[k + 2].value : null
      constants.push({ name: token.value, label })
      if (tokens[k + 1].value === '(') k = matchClose(tokens, k + 1)
    }
    return constants
  }

  private indexRecords(file: JavaFile) {
    const t = file.tokens
    for (let k = 0; k < t.length - 2; k++) {
      if (t[k].value !== 'record' || t[k + 1].kind !== 'word' || t[k + 2].value !== '(') continue
      const components = splitArgs(t, k + 3, matchClose(t, k + 2))
      components.forEach(([s, e], index) => {
        const name = lastWord(t, s, e)
        const records = this.recordComponents.get(name) ?? []
        records.push({ record: t[k + 1].value, index, arity: components.length })
        this.recordComponents.set(name, records)
      })
    }
  }

  private enclosingMethod(file: JavaFile, index: number): JavaMethod | null {
    let best: JavaMethod | null = null
    for (const m of file.methods) if (index > m.start && index < m.end && (!best || m.start > best.start)) best = m
    return best
  }

  private evaluate(
    file: JavaFile,
    from: number,
    to: number,
    method: JavaMethod | null,
    seen: Set<string>,
  ): MessageParts[] {
    const t = file.tokens
    while (from < to && t[from].value === '(' && matchClose(t, from) === to - 1) {
      from++
      to--
    }
    if (from >= to) return [[{ hole: '' }]]
    // A supplier `() -> expression` stands for its expression.
    if (t[from].value === '(' && t[from + 1]?.value === ')' && t[from + 2]?.value === '->') {
      return this.evaluate(file, from + 3, to, method, seen)
    }
    // `condition ? a : b` gives both messages.
    let depth = 0
    for (let k = from; k < to; k++) {
      depth += depthStep(t[k])
      if (depth !== 0 || t[k].kind !== 'mark' || t[k].value !== '?') continue
      let inner = 0
      let nested = 0
      for (let q = k + 1; q < to; q++) {
        inner += depthStep(t[q])
        if (inner !== 0 || t[q].kind !== 'mark') continue
        if (t[q].value === '?') nested++
        else if (t[q].value === ':' && nested-- === 0) {
          return [...this.evaluate(file, k + 1, q, method, seen), ...this.evaluate(file, q + 1, to, method, seen)]
        }
      }
    }
    // `a + b + c` concatenates.
    const operands: Array<[number, number]> = []
    depth = 0
    let start = from
    for (let k = from; k < to; k++) {
      depth += depthStep(t[k])
      if (depth === 0 && t[k].kind === 'mark' && t[k].value === '+') {
        operands.push([start, k])
        start = k + 1
      }
    }
    operands.push([start, to])
    if (operands.length === 1) return this.evaluateOperand(file, from, to, method, seen)
    let result: MessageParts[] = [[]]
    for (const [s, e] of operands) {
      const alternatives = this.evaluate(file, s, e, method, seen)
      result = result.flatMap((prefix) => alternatives.map((alt) => [...prefix, ...alt]))
    }
    return result
  }

  private constant(file: JavaFile, name: string, seen: Set<string>): MessageParts[] | null {
    const at = file.constants.get(name)
    const key = `${file.cls}.${name}`
    if (at === undefined || seen.has(key)) return null
    return this.evaluate(file, at, expressionEnd(file.tokens, at), null, new Set([...seen, key]))
  }

  private localVariable(file: JavaFile, method: JavaMethod, name: string, seen: Set<string>): MessageParts[] | null {
    const key = `${file.cls}#${method.start}.${name}`
    if (seen.has(key)) return null
    const t = file.tokens
    const alternatives: MessageParts[] = []
    for (let k = method.start; k < method.end - 2; k++) {
      if (t[k].value === name && t[k + 1].value === '=' && t[k + 2].value !== '=' && t[k - 1]?.value !== '.') {
        alternatives.push(...this.evaluate(file, k + 2, expressionEnd(t, k + 2), method, new Set([...seen, key])))
      }
    }
    return alternatives.length ? alternatives : null
  }

  private evaluateOperand(
    file: JavaFile,
    from: number,
    to: number,
    method: JavaMethod | null,
    seen: Set<string>,
  ): MessageParts[] {
    const t = file.tokens
    const size = to - from
    const text = t
      .slice(from, to)
      .map((token) => (token.kind === 'text' ? JSON.stringify(token.value) : token.value))
      .join('')
    if (size === 1 && t[from].kind === 'text') return [[{ lit: t[from].value }]]
    if (size === 1 && t[from].kind === 'number' && /^[0-9]+[lL]?$/.test(t[from].value)) {
      return [[{ lit: t[from].value.replace(/[lL]$/, '') }]]
    }
    if (t[from].kind === 'text' && t[from + 1]?.value === '.' && t[from + 2]?.value === 'formatted') {
      return [formatParts(t[from].value)]
    }
    if (t[from].value === 'String' && t[from + 2]?.value === 'format' && t[from + 4]?.kind === 'text') {
      return [formatParts(t[from + 4].value)]
    }
    if (size === 1 && t[from].kind === 'word') {
      const name = t[from].value
      if (method?.params.includes(name)) return [[{ param: method.params.indexOf(name), text: name }]]
      const local = method ? this.localVariable(file, method, name, seen) : null
      if (local) return local
      const constant = this.constant(file, name, seen)
      if (constant) return constant
    }
    if (size === 3 && t[from + 1].value === '.' && this.classes.has(t[from].value)) {
      const constant = this.constant(this.classes.get(t[from].value)!, t[from + 2].value, seen)
      if (constant) return constant
    }
    // `supplier.get()` of a parameter.
    if (
      size === 5 &&
      t[from + 2].value === 'get' &&
      t[from + 3].value === '(' &&
      method?.params.includes(t[from].value)
    ) {
      return [[{ param: method.params.indexOf(t[from].value), text }]]
    }
    // `value.component()` of a record that carries the message.
    if (size === 5 && t[from + 1].value === '.' && t[from + 3].value === '(' && t[from + 4].value === ')') {
      const records = this.recordComponents.get(t[from + 2].value)
      if (records && !this.classes.has(t[from].value)) {
        return records.map((r) => [{ field: `${r.record}/${r.arity}`, index: r.index, text }])
      }
    }
    // `....orElse("fallback")`: the fallback is one of the messages.
    const alternatives: MessageParts[] = [[{ hole: text }]]
    for (let k = from; k < to - 3; k++) {
      if (t[k].value === 'orElse' && t[k + 1].value === '(' && t[k + 2].kind === 'text' && t[k + 3].value === ')') {
        alternatives.push([{ lit: t[k + 2].value }])
      }
    }
    return alternatives
  }

  private evaluateStatus(file: JavaFile, from: number, to: number, method: JavaMethod | null): StatusExpression {
    const t = file.tokens
    const last = t[to - 1]
    if (last && HTTP_STATUS[last.value] !== undefined) return { code: HTTP_STATUS[last.value] }
    if (to - from === 1 && method?.params.includes(t[from].value))
      return { param: method.params.indexOf(t[from].value) }
    return { code: null }
  }

  /** True when tokens[k] (the sink's name followed by `(`) calls the sink's method or constructs its record. */
  private isCallOf(sink: MessageSink, file: JavaFile, k: number): boolean {
    const t = file.tokens
    const prev = t[k - 1]
    if (sink.kind === 'new') {
      let q = k - 1
      while (t[q]?.value === '.' && t[q - 1]?.kind === 'word') q -= 2
      return t[q]?.value === 'new'
    }
    if (['>', ']', '::'].includes(prev.value)) return false
    const qualifier = prev.value === '.' ? t[k - 2] : null
    if (qualifier) return !this.classes.has(qualifier.value) || qualifier.value === sink.cls
    if (prev.kind === 'word' && !['return', 'throw', 'else'].includes(prev.value)) return false
    const imported = file.staticImports.has(`${sink.cls}.${sink.name}`) || file.staticImports.has(`${sink.cls}.*`)
    return file.cls === sink.cls || imported
  }

  /** True when the `message` token at `index` is an argument of an annotation or an annotation element's default. */
  private insideAnnotation(t: JavaToken[], index: number): boolean {
    if (t[index + 1].value === '(') return true
    let depth = 0
    for (let k = index - 1; k >= 0; k--) {
      depth -= depthStep(t[k])
      if (depth < 0) {
        let q = k - 1
        while (t[q]?.kind === 'word' && t[q - 1]?.value === '.') q -= 2
        return t[k].value === '(' && t[q]?.kind === 'word' && t[q - 1]?.value === '@'
      }
      if (t[k].value === ';' || t[k].value === '{') return false
    }
    return false
  }

  extract(): ExtractedMessage[] {
    const found: ExtractedMessage[] = []
    const sinks: MessageSink[] = []
    const seen = new Set<string>()
    const once = (key: string) => !seen.has(key) && Boolean(seen.add(key))
    const addSink = (sink: MessageSink) => {
      if (once(`sink:${JSON.stringify(sink)}`)) sinks.push(sink)
    }
    const emit = (message: ExtractedMessage) => {
      if (once(`message:${JSON.stringify(message)}`)) found.push(message)
    }
    const asHoles = (alt: MessageParts): MessageParts =>
      alt.map((p) => ('field' in p || 'param' in p ? { hole: p.text } : p))

    /**
     * Records the messages of one throw, or of one place that supplies a helper's message. A parameter or record
     * component that is the whole message is followed to its callers. Inside a longer text it is a value (a status, a
     * content label, a title): the text is a template, and each caller that passes a fixed value adds an example.
     */
    const record = (
      origin: { file: JavaFile; line: number; method: JavaMethod | null },
      exception: ExtractedMessage['source'],
      status: StatusExpression,
      alternatives: MessageParts[],
    ) => {
      const { file, line, method } = origin
      for (const alt of alternatives) {
        const forwarded = alt.find((p) => 'param' in p || 'field' in p)
        if (forwarded && alt.length === 1) {
          if ('field' in forwarded) {
            const [name, arity] = forwarded.field.split('/')
            addSink({
              kind: 'new',
              cls: '',
              name,
              arity: Number(arity),
              index: forwarded.index,
              status,
              template: alt,
              exception,
              embedded: false,
            })
          } else if ('param' in forwarded && method) {
            addSink({
              kind: 'call',
              cls: file.cls,
              name: method.name,
              arity: method.params.length,
              index: forwarded.param,
              status,
              template: alt,
              exception,
              embedded: false,
            })
          }
          continue
        }
        if ('code' in status) {
          emit({ source: exception, status: status.code, parts: asHoles(alt), file: file.path, line, example: false })
        }
        const param = alt.find((p): p is { param: number; text: string } => 'param' in p)
        if (param && method) {
          addSink({
            kind: 'call',
            cls: file.cls,
            name: method.name,
            arity: method.params.length,
            index: param.param,
            status,
            template: alt,
            exception,
            embedded: true,
          })
        }
      }
    }

    for (const file of this.files) {
      // EnumLookups.fromValue throws the messages its enums pass in; they are not checked (see serverMessages.ts).
      if (file.cls === 'EnumLookups') continue
      const t = file.tokens
      for (let k = 0; k < t.length - 2; k++) {
        if (t[k].value !== 'new' || t[k + 2].value !== '(') continue
        const exception = t[k + 1].value
        if (exception !== 'ResponseStatusException' && exception !== 'IllegalArgumentException') continue
        const args = splitArgs(t, k + 3, matchClose(t, k + 2))
        const method = this.enclosingMethod(file, k)
        const statusArg = exception === 'ResponseStatusException' ? args[0] : null
        const messageArg = exception === 'ResponseStatusException' ? args[1] : args[0]
        if (!messageArg) continue
        const status = statusArg ? this.evaluateStatus(file, statusArg[0], statusArg[1], method) : { code: 400 }
        const alternatives = this.evaluate(file, messageArg[0], messageArg[1], method, new Set())
        record({ file, line: t[k].line, method }, exception, status, alternatives)
      }
    }

    // Follow each forwarded parameter or record component to the places that supply it, until nothing new is found.
    for (let s = 0; s < sinks.length; s++) {
      const sink = sinks[s]
      const isSlot = (p: MessagePart) =>
        sink.kind === 'new' ? 'field' in p && p.index === sink.index : 'param' in p && p.param === sink.index
      for (const file of this.files) {
        const t = file.tokens
        for (let k = 1; k < t.length - 1; k++) {
          if (t[k].value !== sink.name || t[k + 1].value !== '(' || !this.isCallOf(sink, file, k)) continue
          const args = splitArgs(t, k + 2, matchClose(t, k + 1))
          if (args.length !== sink.arity) continue
          const method = this.enclosingMethod(file, k)
          const supplied = this.evaluate(file, args[sink.index][0], args[sink.index][1], method, new Set())
          const status: StatusExpression =
            'param' in sink.status
              ? this.evaluateStatus(file, args[sink.status.param][0], args[sink.status.param][1], method)
              : sink.status
          if (!sink.embedded) {
            const alternatives = supplied.map((value) =>
              sink.template.flatMap((p): MessagePart[] => (isSlot(p) ? value : [p])),
            )
            record({ file, line: t[k].line, method }, sink.exception, status, alternatives)
            continue
          }
          for (const value of supplied) {
            const passed = value.length === 1 ? value[0] : null
            if (passed && 'param' in passed && method) {
              // The value comes from a parameter of the caller: follow that parameter further.
              const template = sink.template.map((p) =>
                isSlot(p) ? passed : 'param' in p || 'field' in p ? { hole: p.text } : p,
              )
              addSink({
                ...sink,
                cls: file.cls,
                name: method.name,
                arity: method.params.length,
                index: passed.param,
                status,
                template,
              })
            } else if (value.every((p) => 'lit' in p) && 'code' in status) {
              const parts = sink.template.flatMap((p): MessagePart[] => (isSlot(p) ? value : [p]))
              if (parts.every((p) => 'lit' in p)) {
                emit({
                  source: sink.exception,
                  status: status.code,
                  parts,
                  file: file.path,
                  line: t[k].line,
                  example: true,
                })
              }
            }
          }
        }
      }
    }

    // Bean Validation: `message = ...` of a constraint annotation and `String message() default ...` of a custom one.
    for (const file of this.files) {
      const t = file.tokens
      for (let k = 0; k < t.length - 3; k++) {
        let at = -1
        if (t[k].value === 'message' && t[k + 1].value === '=' && t[k + 2].value !== '=') at = k + 2
        if (
          t[k].value === 'message' &&
          t[k + 1].value === '(' &&
          t[k + 2].value === ')' &&
          t[k + 3].value === 'default'
        ) {
          at = k + 4
        }
        if (at < 0 || !this.insideAnnotation(t, k)) continue
        for (const parts of this.evaluate(file, at, expressionEnd(t, at), null, new Set())) {
          emit({ source: 'validation', status: 400, parts, file: file.path, line: t[k].line, example: false })
        }
      }
    }
    return found
  }
}

const JAVA_ROOT = 'src/main/java/'
const RESOLVER = 'FriendlyErrorMessageResolver'
const PACKAGE_ROOT = 'com/example/aichinese/'

/**
 * IllegalArgumentException messages from code that never handles a user's input: startup settings and servlet
 * filters (config/, and the limiter and trusted-proxy settings in security/), checks on objects the server builds
 * itself (dto/ records, the AI provider plumbing in service/ai/) and helper argument
 * checks (util/). One of them can only appear after a programming error; an IllegalArgumentException thrown while
 * Jackson reads a request record (dto/) reaches the user as the generic "could not be processed" message instead.
 * ResponseStatusException messages are never excluded by location.
 */
const INTERNAL_ILLEGAL_ARGUMENT_SOURCES = ['config/', 'dto/', 'util/', 'security/', 'service/ai/'].map(
  (folder) => PACKAGE_ROOT + folder,
)

type ResolverHints = Record<'explicit' | 'rateLimit' | 'badCredentials' | 'notFound', string[]>

/**
 * FriendlyErrorMessageResolver.resolve, read for one message: true when the user sees the message as sent. A 403 and a
 * 5xx answer are replaced by a generic message (already translated), and so are 401 (apart from bad credentials),
 * 413, 429 and any message about rate limits, unless the message carries one of the listed pass-through hints.
 */
function shownAsSent(status: number | null, message: string, hints: ResolverHints): boolean {
  const normalized = message.toLowerCase()
  const has = (list: string[]) => list.some((hint) => normalized.includes(hint))
  const code = status ?? 400
  if (!normalized.trim()) return false
  if (has(hints.explicit)) return true
  if (has(hints.rateLimit)) return false
  if (code === 401) return has(hints.badCredentials)
  if (code === 403) return false
  if (code === 404 || has(hints.notFound)) return true
  if (code >= 500) return false
  return code !== 413 && code !== 429
}

function messageText(parts: MessageParts): string {
  return parts
    .map((p) => ('lit' in p ? p.lit : '{}'))
    .join('')
    .split(' ')
    .filter(Boolean)
    .join(' ')
}

const isTemplate = (message: ExtractedMessage) => message.parts.some((p) => 'hole' in p)

function isUntranslated(message: string): boolean {
  const translated = translations(message)
  return translated.ms === message || translated['zh-CN'] === message
}

/**
 * The Node modules the scan needs. They are loaded by name, so Node's global types stay out of the app type-check
 * (tsconfig.app.json includes this file) and only these few functions are typed here.
 */
type NodeModules = {
  fs: {
    existsSync(path: string): boolean
    readdirSync(path: string, options: { recursive: true; encoding: 'utf8' }): string[]
    readFileSync(path: string, encoding: 'utf8'): string
  }
  path: {
    dirname(path: string): string
    join(...parts: string[]): string
    resolve(...parts: string[]): string
    sep: string
  }
  url: { fileURLToPath(url: string): string }
}

async function loadNodeModules(): Promise<NodeModules> {
  const load = (name: string) => import(/* @vite-ignore */ name)
  const [fs, path, url] = await Promise.all([load('node:fs'), load('node:path'), load('node:url')])
  return { fs, path, url }
}

const node = await loadNodeModules()
/** `../backend/src/main/java` next to the frontend folder (this file is frontend/src/utils/). */
const javaRoot = node.path.resolve(
  node.path.dirname(node.url.fileURLToPath(import.meta.url)),
  '../../../backend',
  JAVA_ROOT,
)
const noBackend = !node.fs.existsSync(javaRoot)
const skipNote = noBackend ? ' (skipped: ../backend/src/main/java is absent, as in the Frontend Demo copy)' : ''

function readBackendFiles(): Array<{ path: string; source: string }> {
  return node.fs
    .readdirSync(javaRoot, { recursive: true, encoding: 'utf8' })
    .filter((path) => path.endsWith('.java'))
    .map((path) => ({
      path: path.split(node.path.sep).join('/'),
      source: node.fs.readFileSync(node.path.join(javaRoot, path), 'utf8'),
    }))
}

let scanned: { extractor: BackendMessageExtractor; shown: ExtractedMessage[] } | null = null

/** The backend messages a user can see, read once from the Java sources. */
function scanBackend() {
  if (scanned) return scanned
  const extractor = new BackendMessageExtractor(readBackendFiles())
  const hints: ResolverHints = {
    explicit: extractor.stringList(RESOLVER, 'EXPLICIT_PASSTHROUGH_HINTS'),
    rateLimit: extractor.stringList(RESOLVER, 'RATE_LIMIT_HINTS'),
    badCredentials: extractor.stringList(RESOLVER, 'BAD_CREDENTIALS_HINTS'),
    notFound: extractor.stringList(RESOLVER, 'NOT_FOUND_HINTS'),
  }
  for (const [name, list] of Object.entries(hints)) expect(list.length, `${RESOLVER} ${name} hints`).toBeGreaterThan(0)

  const shown = extractor.extract().filter((message) => {
    // A message that is only a value passed on (exception.getMessage(), a Bean Validation message rethrown by
    // TeacherActivityService) is checked where that message is written.
    if (message.parts.length === 1 && 'hole' in message.parts[0]) return false
    if (message.source === 'validation') return true
    if (message.source === 'IllegalArgumentException') {
      if (INTERNAL_ILLEGAL_ARGUMENT_SOURCES.some((folder) => message.file.startsWith(folder))) return false
    }
    return shownAsSent(message.status, messageText(message.parts), hints)
  })
  scanned = { extractor, shown }
  return scanned
}

describe(`every backend message a user can see is translated (FRLANG02)${skipNote}`, () => {
  it.skipIf(noBackend)('translates every fixed reason of a failed request', () => {
    const messages = scanBackend().shown.filter((m) => m.source !== 'validation' && !isTemplate(m))
    // A guard against a scan that silently finds nothing after a change in the backend's code style.
    expect(messages.length).toBeGreaterThan(150)
    const untranslated = [...new Set(messages.map((m) => messageText(m.parts)))].filter(isUntranslated)
    expect(untranslated).toEqual([])
  })

  it.skipIf(noBackend)('translates every Bean Validation message', () => {
    const messages = scanBackend().shown.filter((m) => m.source === 'validation' && !isTemplate(m))
    expect(messages.length).toBeGreaterThan(200)
    const untranslated = [...new Set(messages.map((m) => messageText(m.parts)))].filter(isUntranslated)
    expect(untranslated).toEqual([])
  })

  it.skipIf(noBackend)('has a pattern for every message the backend builds from parts', () => {
    const skeleton = (text: string) => lookupKey(text.replace(/[{][A-Za-z]*[}]/g, '{}'))
    const patterns = new Set(serverMessageCatalogue.MESSAGE_PATTERNS.map((pattern) => skeleton(pattern.en)))
    const templates = scanBackend().shown.filter((m) => isTemplate(m) && !m.example)
    // A sanity check that the scanner still finds templates; the assertion that matters is the next one.
    expect(templates.length).toBeGreaterThan(3)
    const missing = [...new Set(templates.map((m) => messageText(m.parts)))].filter(
      (text) => !patterns.has(skeleton(text)),
    )
    expect(missing).toEqual([])
  })

  it.skipIf(noBackend)('translates every finished message the backend builds from a value it passes in', () => {
    const { shown } = scanBackend()
    // The finished messages of templates whose values the code passes in as text.
    const examples = [...new Set(shown.filter((m) => m.example).map((m) => messageText(m.parts)))]
    // A sanity check that the scanner still finds such templates; the assertion that matters is the next one.
    expect(examples.length).toBeGreaterThan(5)
    expect(examples.filter(isUntranslated)).toEqual([])
  })

  it.skipIf(noBackend)('has no row for a message the backend no longer sends', () => {
    const sent = scanBackend().extractor.extract()
    const fixed = new Set(sent.filter((m) => !isTemplate(m)).map((m) => lookupKey(messageText(m.parts))))
    // A message built from parts ("Class" + " has already been archived.") matches with any text in each hole.
    const built = sent
      .filter(isTemplate)
      .map((m) => new RegExp(`^${lookupKey(messageText(m.parts)).split('{}').map(escapeRegExp).join('.+')}$`))
    // The extractor does not read EnumLookups.fromValue messages; any string literal of the backend counts.
    const literals = javaStringLiterals()
    const stale = catalogueRows
      .map(([en]) => en)
      .filter((en) => {
        const key = lookupKey(en)
        return !fixed.has(key) && !literals.has(key) && !built.some((pattern) => pattern.test(key))
      })
    expect(stale).toEqual([])
  })
})

/** Every string literal of the backend sources, with `"a" + "b"` joined, as lookup keys. */
function javaStringLiterals(): Set<string> {
  const literal = '"(?:[^"\\\\\\n]|\\\\.)*"'
  const run = new RegExp(`${literal}(?:\\s*\\+\\s*${literal})*`, 'g')
  const keys = new Set<string>()
  for (const { source } of readBackendFiles()) {
    for (const [match] of source.matchAll(run)) {
      const text = [...match.matchAll(new RegExp(literal, 'g'))]
        .map(([part]) => unescapeJava(part.slice(1, -1)))
        .join('')
      keys.add(lookupKey(text))
    }
  }
  return keys
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
