import { describe, expect, it } from 'vitest'

import { normalizeAppRoute } from './routeCompat'

describe('normalizeAppRoute', () => {
  it.each([
    ['/catalog?level=beginner', '/courses?level=beginner'],
    ['/student/feedback?tab=history', '/student/profile?tab=history'],
    ['/admin/ai-response-reports/42#review', '/admin/moderation#review'],
  ])('rewrites %s to %s', (input, output) => {
    expect(normalizeAppRoute(input)).toBe(output)
  })

  it.each([
    'https://evil.example/student/home',
    '//evil.example/student/home',
    '/%2f%2fevil.example/student/home',
    '/student/home%00',
    '/unknown',
  ])('rejects external or unknown target %s', (input) => {
    expect(normalizeAppRoute(input)).toBeNull()
  })

  it.each(['/courses/../../admin/settings', '/courses/%2e%2e/%2e%2e/admin/settings', '/courses\\..\\admin\\settings'])(
    'rejects path traversal target %s',
    (input) => {
      expect(normalizeAppRoute(input)).toBeNull()
    },
  )

  it.each(['/courses/PACKAGE-42?tab=overview', '/student/quiz/123?lessonId=9'])(
    'keeps valid dynamic app route %s',
    (input) => {
      expect(normalizeAppRoute(input)).toBe(input)
    },
  )

  it.each(['/teacher/activities', '/admin/content-review', '/admin/content-review?type=QUIZ&id=7'])(
    'keeps content governance route %s (notification target paths)',
    (input) => {
      expect(normalizeAppRoute(input)).toBe(input)
    },
  )

  it.each([
    '/student/notifications',
    '/teacher/notifications',
    '/admin/notifications',
    '/student/practice-submissions',
    '/student/account',
    '/teacher/account',
    '/admin/account',
    '/student/progress',
  ])('keeps W4 notification target / account route %s', (input) => {
    expect(normalizeAppRoute(input)).toBe(input)
  })

  it.each(['/responsible-ai', '/privacy#ai-provider', '/terms'])('keeps public information route %s', (input) => {
    expect(normalizeAppRoute(input)).toBe(input)
  })

  it.each([
    ['  /student/home  ', '/student/home'],
    ['\n/admin/settings\t', '/admin/settings'],
  ])('trims harmless surrounding whitespace in %s', (input, output) => {
    expect(normalizeAppRoute(input)).toBe(output)
  })
})
