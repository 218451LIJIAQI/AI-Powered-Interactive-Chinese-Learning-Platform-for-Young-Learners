import { describe, expect, it } from 'vitest'

import { firstQueryText } from './queryText'

describe('firstQueryText', () => {
  it('extracts the first non-empty query text from scalar or array query values', () => {
    expect(firstQueryText(' Learner@Example.com ')).toBe('Learner@Example.com')
    expect(firstQueryText(['', null, ' reset-token '])).toBe('reset-token')
    expect(firstQueryText(null)).toBe('')
  })
})
