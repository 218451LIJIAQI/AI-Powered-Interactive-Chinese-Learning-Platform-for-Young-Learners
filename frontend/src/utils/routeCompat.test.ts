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
    expect(normalizeAppRoute('/unknown')).toBeNull()
  })
})
