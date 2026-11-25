import { describe, expect, it } from 'vitest'

import { firstQueryText, pageFromQuery, pageQuery } from './routeQuery'

describe('routeQuery', () => {
  it('extracts the first non-empty query text from scalar or array query values', () => {
    expect(firstQueryText(' Learner@Example.com ')).toBe('Learner@Example.com')
    expect(firstQueryText(['', null, ' reset-token '])).toBe('reset-token')
    expect(firstQueryText(null)).toBe('')
  })

  it('converts between 1-based address page params and 0-based page indexes', () => {
    expect(pageFromQuery(undefined)).toBe(0)
    expect(pageFromQuery('3')).toBe(2)
    expect(pageQuery(0)).toBeUndefined()
    expect(pageQuery(2)).toBe('3')
  })
})
