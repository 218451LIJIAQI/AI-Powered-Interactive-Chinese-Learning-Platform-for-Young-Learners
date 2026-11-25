import { describe, expect, it } from 'vitest'

import { toCsv } from './csv'

describe('CSV text', () => {
  it('quotes cells with commas, quotes and line breaks', () => {
    expect(toCsv([['plain', 'a, b', 'say "hi"', 'two\nlines', null, 42]])).toBe(
      'plain,"a, b","say ""hi""","two\nlines",,42\r\n',
    )
  })

  it('neutralises text a spreadsheet would run as a formula, including leading whitespace or newlines, but not negative numbers', () => {
    expect(toCsv([['=SUM(A1:A2)', '@cmd', '+1', ' =1+1', '\u00a0@cmd', '\n=1+1', -3]])).toBe(
      "'=SUM(A1:A2),'@cmd,'+1,' =1+1,'\u00a0@cmd,\"'\n=1+1\",-3\r\n",
    )
  })

  it('joins rows with CRLF and returns empty string for an empty dataset', () => {
    expect(toCsv([])).toBe('')
    expect(toCsv([['Metric', 'Value'], ['Users', 15], []])).toBe('Metric,Value\r\nUsers,15\r\n\r\n')
  })
})
