import { describe, expect, it } from 'vitest'

import { normalizeAppRoute } from './routeCompat'

describe('normalizeAppRoute', () => {
  it('rewrites legacy deep links while preserving suffixes', () => {
    expect(normalizeAppRoute('/student/payments?tab=history')).toBe('/student/profile?tab=history')
    expect(normalizeAppRoute('/admin/ai-response-reports/42#review')).toBe('/admin/moderation#review')
  })

  it('rejects external and unknown redirect targets', () => {
    expect(normalizeAppRoute('https://evil.example/student/home')).toBeNull()
    expect(normalizeAppRoute('//evil.example/student/home')).toBeNull()
    expect(normalizeAppRoute('/%2f%2fevil.example/student/home')).toBeNull()
    expect(normalizeAppRoute('/student/home%00')).toBeNull()
    expect(normalizeAppRoute('/unknown')).toBeNull()
  })

  it('rejects browser-normalised path traversal variants', () => {
    expect(normalizeAppRoute('/courses/../../admin/settings')).toBeNull()
    expect(normalizeAppRoute('/courses/%2e%2e/%2e%2e/admin/settings')).toBeNull()
    expect(normalizeAppRoute('/courses\\..\\admin\\settings')).toBeNull()
  })

  it('keeps valid dynamic app routes with query strings', () => {
    expect(normalizeAppRoute('/courses/PACKAGE-42?tab=overview')).toBe('/courses/PACKAGE-42?tab=overview')
    expect(normalizeAppRoute('/student/quiz/123?lessonId=9')).toBe('/student/quiz/123?lessonId=9')
  })

  it('preserves only known routes after trimming harmless surrounding whitespace', () => {
    expect(normalizeAppRoute('  /student/home  ')).toBe('/student/home')
    expect(normalizeAppRoute('\n/admin/settings\t')).toBe('/admin/settings')
  })
})
