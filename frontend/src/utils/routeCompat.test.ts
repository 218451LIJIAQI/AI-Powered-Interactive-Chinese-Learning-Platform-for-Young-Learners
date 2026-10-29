import { describe, expect, it } from 'vitest'

import { normalizeAppRoute } from './routeCompat'

describe('normalizeAppRoute', () => {
  it.each([
    'https://evil.example/student/home',
    '//evil.example/student/home',
    '/%2f%2fevil.example/student/home',
    '/student/home%00',
    '/unknown',
    '/student/classes/abc',
    null,
  ])('rejects external or unknown target %s', (input) => {
    expect(normalizeAppRoute(input)).toBeNull()
  })

  it.each(['/classes/../../admin/settings', '/classes/%2e%2e/%2e%2e/admin/settings', '/classes\\..\\admin\\settings'])(
    'rejects path traversal target %s',
    (input) => {
      expect(normalizeAppRoute(input)).toBeNull()
    },
  )

  it.each([
    '/classes/42',
    '/student/quiz/123',
    '/student/classes/7/lessons/9',
    '/student/classes/7/lessons/9/activities/flashcards/3',
    '/student/learn/lessons/12',
    '/student/practice/mistakes',
    '/student/ai/voice',
    '/student/support/1038',
    '/teacher/classes/4/lessons/5/quiz',
    '/teacher/live-sessions/past',
    '/admin/content/learning-path',
    '/admin/ai/settings',
  ])('keeps valid dynamic app route %s', (input) => {
    expect(normalizeAppRoute(input)).toBe(input)
  })

  it.each([
    '/student/achievements',
    '/courses/12',
    '/teacher/content',
    '/admin/moderation',
    '/admin/content-review?id=7',
  ])('keeps an old path from a stored notification, which the router redirects: %s', (input) => {
    expect(normalizeAppRoute(input)).toBe(input)
  })

  it.each(['/responsible-ai', '/privacy#ai-provider', '/terms', '/help', '/safety'])(
    'keeps public information route %s',
    (input) => {
      expect(normalizeAppRoute(input)).toBe(input)
    },
  )

  it.each([
    ['  /student/home  ', '/student/home'],
    ['\n/admin/ai\t', '/admin/ai'],
  ])('trims harmless surrounding whitespace in %s', (input, output) => {
    expect(normalizeAppRoute(input)).toBe(output)
  })
})
