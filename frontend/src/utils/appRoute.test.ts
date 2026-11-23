import { describe, expect, it } from 'vitest'

import { normalizeAppRoute, signedInDestination } from './appRoute'
import { parseRecordId } from './recordId'

it('reads a single decimal record id without rounding it to another record', () => {
  expect(parseRecordId(' 007 ')).toBe(7)
  expect(parseRecordId(7)).toBe(7)
  for (const invalid of [['7'], '9007199254740993', '0x10', '1e3', true, 0]) expect(parseRecordId(invalid)).toBeNull()
  expect(normalizeAppRoute('/classes/9007199254740993')).toBeNull()
})

describe('signedInDestination', () => {
  it('goes back to a safe page the signed-in role may open, query included', () => {
    expect(signedInDestination('/student/classes/7?tab=lessons', ['STUDENT'])).toBe('/student/classes/7?tab=lessons')
    expect(signedInDestination('/student/ai', ['STUDENT'])).toBe('/student/ai')
    expect(signedInDestination('/admin/content/classes/4', ['ADMIN'])).toBe('/admin/content/classes/4')
    // Public pages are open to every role.
    expect(signedInDestination('/classes/42', ['ADMIN'])).toBe('/classes/42')
  })

  it('restores a learner link for an account whose default home is the admin dashboard', () => {
    const roles = ['ADMIN', 'STUDENT'] as const
    expect(signedInDestination('/student/classes/7', roles)).toBe('/student/classes/7')
    expect(signedInDestination(null, roles)).toBe('/admin/dashboard')
  })

  it.each([
    // A public class page sends its visitor to sign in for the learner class page; an admin signs in there.
    ['/student/classes/7', 'ADMIN', '/admin/dashboard'],
    // The page of a session that ended for an admin, then a learner signs in on that computer.
    ['/admin/users', 'STUDENT', '/student/home'],
  ] as const)('leads home instead of to a page of another role: %s for %s', (redirect, role, home) => {
    expect(signedInDestination(redirect, [role])).toBe(home)
  })

  it.each([null, undefined, 42, ['/student/home'], '', 'https://evil.example/', '/unknown', '/auth', '/unauthorized'])(
    'ignores an unusable or pointless redirect %j',
    (redirect) => {
      expect(signedInDestination(redirect, ['STUDENT'])).toBe('/student/home')
    },
  )
})

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
    '/student/quiz/123/attempts/4',
    '/student/classes/7/lessons/9',
    '/student/classes/7/lessons/9/activities/flashcards/3',
    '/student/learn/lessons/12',
    '/student/practice/mistakes',
    '/student/ai/voice',
    '/admin/content/classes/4',
    '/admin/content/classes/4/details',
    '/admin/content/classes/4/lessons/5/quiz',
    '/admin/content/learning-path',
    '/admin/ai/settings',
    '/admin/support?request=9',
  ])('keeps valid dynamic app route %s', (input) => {
    expect(normalizeAppRoute(input)).toBe(input)
  })

  it.each([
    '/student/achievements',
    '/student/practice/story',
    '/student/practice/quizzes',
    '/student/classes/7/feedback',
    '/student/classes/live',
    '/student/support/1038',
    '/courses/12',
    '/teacher/classes/4/lessons/5/quiz',
    '/teacher/live-sessions/past',
    '/admin/users/applications',
    '/admin/content-review?id=7',
  ])('rejects a path the router no longer has: %s', (input) => {
    expect(normalizeAppRoute(input)).toBeNull()
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
