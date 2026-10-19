import * as Sentry from '@sentry/vue'
import type { Event, User } from '@sentry/vue'
import type { App } from 'vue'
import type { Router } from 'vue-router'

import { appEnv } from '@/config/env'
import {
  FILTERED,
  isSensitiveKey,
  scrubSensitiveRecord,
  scrubSensitiveUnknown,
  scrubSensitiveUrl,
  scrubSensitiveValue,
} from '@/utils/sensitiveData'

type SentryQueryParams = NonNullable<NonNullable<Event['request']>['query_string']>

export function initObservability(app: App, router: Router): void {
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
    beforeSend: sanitizeSentryEvent,
    // Tracing events bypass beforeSend, so URLs and span data are scrubbed here too.
    beforeSendTransaction: sanitizeSentryEvent,
  })
}

export function sanitizeSentryEvent<T extends Event>(event: T): T {
  if (event.request) {
    event.request.url = scrubSensitiveUrl(event.request.url)
    event.request.headers = scrubSensitiveRecord(event.request.headers)
    event.request.cookies = scrubSensitiveRecord(event.request.cookies)
    event.request.env = scrubSensitiveRecord(event.request.env)
    event.request.query_string = scrubQueryParams(event.request.query_string)
    event.request.data = scrubSensitiveUnknown(event.request.data)
  }

  if (event.transaction) {
    event.transaction = scrubSensitiveValue(event.transaction)
  }
  if (event.message) {
    event.message = scrubSensitiveValue(event.message)
  }
  if (event.logentry?.message) {
    event.logentry.message = scrubSensitiveValue(event.logentry.message)
  }
  if (event.exception?.values) {
    event.exception.values = event.exception.values.map((exception) => ({
      ...exception,
      value: exception.value ? scrubSensitiveValue(exception.value) : exception.value,
    }))
  }

  event.extra = scrubSensitiveRecord(event.extra)
  event.tags = scrubSensitiveRecord(event.tags)
  event.contexts = scrubSensitiveUnknown(event.contexts) as Event['contexts']
  event.user = scrubSentryUser(event.user)
  event.spans = event.spans?.map((span) => ({
    ...span,
    description: span.description ? scrubSensitiveValue(span.description) : span.description,
    data: scrubSensitiveRecord(span.data) as typeof span.data,
  }))

  if (event.breadcrumbs) {
    event.breadcrumbs = event.breadcrumbs.map((breadcrumb) => ({
      ...breadcrumb,
      message: breadcrumb.message ? scrubSensitiveValue(breadcrumb.message) : breadcrumb.message,
      data: scrubSensitiveUnknown(breadcrumb.data) as typeof breadcrumb.data,
    }))
  }

  return event
}

function scrubQueryParams(query: SentryQueryParams | undefined): SentryQueryParams | undefined {
  if (typeof query === 'string') {
    return scrubSensitiveValue(query)
  }

  if (Array.isArray(query)) {
    return query.map(([key, value]): [string, string] => [key, scrubQueryValue(key, value)])
  }

  if (query) {
    return Object.fromEntries(Object.entries(query).map(([key, value]) => [key, scrubQueryValue(key, value)]))
  }

  return query
}

function scrubQueryValue(key: string, value: string): string {
  return isSensitiveKey(key) ? FILTERED : scrubSensitiveValue(value)
}

function scrubSentryUser(user: User | undefined): User | undefined {
  if (!user) return user

  const scrubbed = scrubSensitiveRecord(user as Record<string, unknown>) as User
  delete scrubbed.id
  delete scrubbed.email
  delete scrubbed.username
  delete scrubbed.ip_address
  delete scrubbed.geo
  return scrubbed
}
