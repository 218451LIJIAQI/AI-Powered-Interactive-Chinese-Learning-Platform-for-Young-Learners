import * as Sentry from '@sentry/vue'
import type { App } from 'vue'
import type { Router } from 'vue-router'

import { appEnv } from '@/config/env'

const SENSITIVE_HEADER_NAMES = ['authorization', 'cookie', 'set-cookie', 'x-api-key']
const SENSITIVE_VALUE_PATTERNS = [
  /bearer\s+[a-z0-9._-]+/gi,
  /mandarin_spark_refresh=[^;\s]+/gi,
  /password["':=\s]+[^"',\s}]+/gi,
  /token["':=\s]+[^"',\s}]+/gi,
]

export function initObservability(app: App, router: Router) {
  if (!appEnv.sentryDsn) {
    return
  }

  Sentry.init({
    app,
    dsn: appEnv.sentryDsn,
    environment: appEnv.environment,
    release: appEnv.release || undefined,
    integrations: [Sentry.browserTracingIntegration({ router })],
    tracesSampleRate: appEnv.environment === 'production' ? 0.05 : 0,
    beforeSend(event) {
      scrubHeaders(event.request?.headers)
      if (event.request?.cookies) {
        scrubHeaders(event.request.cookies)
      }
      if (typeof event.request?.data === 'string') {
        event.request.data = scrubValue(event.request.data)
      }
      if (event.extra) {
        for (const key of Object.keys(event.extra)) {
          event.extra[key] = scrubUnknown(event.extra[key])
        }
      }
      return event
    },
  })
}

function scrubHeaders(headers: Record<string, string> | undefined) {
  if (!headers) {
    return
  }
  for (const key of Object.keys(headers)) {
    if (SENSITIVE_HEADER_NAMES.includes(key.toLowerCase())) {
      headers[key] = '[Filtered]'
    } else {
      headers[key] = scrubValue(headers[key])
    }
  }
}

function scrubUnknown(value: unknown): unknown {
  if (typeof value === 'string') {
    return scrubValue(value)
  }
  if (Array.isArray(value)) {
    return value.map(scrubUnknown)
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([key, nested]) => [
        key,
        SENSITIVE_HEADER_NAMES.includes(key.toLowerCase()) ? '[Filtered]' : scrubUnknown(nested),
      ]),
    )
  }
  return value
}

function scrubValue(value: string) {
  return SENSITIVE_VALUE_PATTERNS.reduce((current, pattern) => current.replace(pattern, '[Filtered]'), value)
}
