export const FILTERED = '[Filtered]'

const MAX_SCRUB_DEPTH = 8

const SENSITIVE_KEY_FRAGMENTS = [
  'authorization',
  'cookie',
  'credential',
  'csrf',
  'email',
  'jwt',
  'password',
  'passcode',
  'private-key',
  'private_key',
  'refresh',
  'session',
  'token',
  'secret',
  'api-key',
  'api_key',
  'apikey',
  'client-secret',
  'client_secret',
  'set-cookie',
] as const

const SENSITIVE_VALUE_PATTERNS = [
  {
    pattern: /\bbearer\s+[^\s,;]+/gi,
    replacement: FILTERED,
  },
  {
    pattern: /\bmandarin_spark_refresh=[^;\s]+/gi,
    replacement: `mandarin_spark_refresh=${FILTERED}`,
  },
  {
    pattern:
      /\b((?:(?:access|refresh|id|reset|csrf|session)[-_ ]?)?(?:token|jwt)|password|passcode|credential|secret|api[-_ ]?key|client[-_ ]?secret)\b\s*[:=]\s*[^\s"',&;}]+/gi,
    replacement: (_match: string, key: string) => `${key}=${FILTERED}`,
  },
  {
    pattern:
      /(["'](?:password|passcode|credential|token|jwt|secret|api[-_ ]?key|client[-_ ]?secret|email)["']\s*:\s*["'])[^"']+(["'])/gi,
    replacement: (_match: string, prefix: string, suffix: string) => `${prefix}${FILTERED}${suffix}`,
  },
  {
    pattern: /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi,
    replacement: FILTERED,
  },
]

export function isSensitiveKey(key: string | null | undefined): boolean {
  const normalized = key?.toLowerCase() ?? ''
  return SENSITIVE_KEY_FRAGMENTS.some((fragment) => normalized.includes(fragment))
}

export function scrubSensitiveValue(value: string): string {
  return SENSITIVE_VALUE_PATTERNS.reduce((current, { pattern, replacement }) => {
    if (typeof replacement === 'string') {
      return current.replace(pattern, replacement)
    }
    return current.replace(pattern, replacement)
  }, value)
}

export function scrubSensitiveUrl(value: string | null | undefined): string | undefined {
  if (!value) return value ?? undefined

  try {
    const rawValue = value.trim()
    const isAbsoluteUrl = /^[a-z][a-z\d+.-]*:/i.test(rawValue) || rawValue.startsWith('//')
    const url = new URL(rawValue, typeof window === 'undefined' ? 'https://app.local' : window.location.href)
    for (const key of Array.from(url.searchParams.keys())) {
      if (isSensitiveKey(key)) {
        url.searchParams.set(key, FILTERED)
      }
    }

    if (isAbsoluteUrl) {
      return scrubSensitiveValue(url.toString())
    }
    if (rawValue.startsWith('?')) {
      return scrubSensitiveValue(`${url.search}${url.hash}`)
    }
    if (rawValue.startsWith('#')) {
      return scrubSensitiveValue(url.hash)
    }

    const relativeUrl = `${url.pathname}${url.search}${url.hash}`
    return scrubSensitiveValue(rawValue.startsWith('/') ? relativeUrl : relativeUrl.replace(/^\//, ''))
  } catch {
    return scrubSensitiveValue(value)
  }
}

export function scrubSensitiveUnknown(value: unknown): unknown {
  return scrubSensitiveUnknownInternal(value, new WeakSet<object>(), 0)
}

function scrubSensitiveUnknownInternal(value: unknown, seen: WeakSet<object>, depth: number): unknown {
  if (typeof value === 'string') {
    return scrubSensitiveValue(value)
  }
  if (!value || typeof value !== 'object') {
    return value
  }
  if (depth >= MAX_SCRUB_DEPTH) {
    return FILTERED
  }
  if (seen.has(value)) {
    return FILTERED
  }

  seen.add(value)

  if (Array.isArray(value)) {
    return value.map((entry) => scrubSensitiveUnknownInternal(entry, seen, depth + 1))
  }
  if (value instanceof Date) {
    return value.toISOString()
  }
  if (value instanceof URLSearchParams) {
    return scrubSensitiveValue(value.toString())
  }
  if (typeof File !== 'undefined' && value instanceof File) {
    return FILTERED
  }
  if (typeof Blob !== 'undefined' && value instanceof Blob) {
    return FILTERED
  }
  if (value instanceof Error) {
    return {
      name: value.name,
      message: scrubSensitiveValue(value.message),
    }
  }

  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([key, nested]) => [
      key,
      isSensitiveKey(key) ? FILTERED : scrubSensitiveUnknownInternal(nested, seen, depth + 1),
    ]),
  )
}

export function scrubSensitiveRecord<T extends Record<string, unknown> | undefined>(record: T): T {
  if (!record) return record
  return Object.fromEntries(
    Object.entries(record).map(([key, value]) => [key, isSensitiveKey(key) ? FILTERED : scrubSensitiveUnknown(value)]),
  ) as T
}
