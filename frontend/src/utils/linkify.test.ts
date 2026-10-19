import { describe, expect, it } from 'vitest'

import { safeImageUrl, splitLinkRuns } from './linkify'

describe('splitLinkRuns (FR-CONT-05 lesson links)', () => {
  it('turns http(s) and www URLs into link runs and keeps the surrounding text', () => {
    expect(splitLinkRuns('Listen at https://example.com/audio?id=1 then review.')).toEqual([
      { text: 'Listen at ' },
      { text: 'https://example.com/audio?id=1', href: 'https://example.com/audio?id=1' },
      { text: ' then review.' },
    ])
    expect(splitLinkRuns('See www.example.org/pinyin')).toEqual([
      { text: 'See ' },
      { text: 'www.example.org/pinyin', href: 'https://www.example.org/pinyin' },
    ])
  })

  it('drops sentence punctuation (ASCII and Chinese) after the URL but keeps balanced parentheses', () => {
    expect(splitLinkRuns('复习：https://example.com/tones。')).toEqual([
      { text: '复习：' },
      { text: 'https://example.com/tones', href: 'https://example.com/tones' },
      { text: '。' },
    ])
    const runs = splitLinkRuns('(see https://en.wikipedia.org/wiki/Pinyin_(romanization)).')
    expect(runs[1]).toEqual({
      text: 'https://en.wikipedia.org/wiki/Pinyin_(romanization)',
      href: 'https://en.wikipedia.org/wiki/Pinyin_(romanization)',
    })
  })

  it('never links unsafe schemes or credentials and leaves markup as plain text', () => {
    const text = 'javascript:alert(1) <a href="https://evil.example">x</a> https://user:pw@example.com/a'
    const runs = splitLinkRuns(text)
    expect(runs.filter((run) => run.href).map((run) => run.href)).toEqual(['https://evil.example/'])
    expect(runs.map((run) => run.text).join('')).toBe(text)
    expect(runs.some((run) => run.href?.startsWith('javascript:'))).toBe(false)
  })

  it('returns no runs for empty text and a single text run without URLs', () => {
    expect(splitLinkRuns(null)).toEqual([])
    expect(splitLinkRuns('你好 nǐ hǎo')).toEqual([{ text: '你好 nǐ hǎo' }])
  })

  it('accepts only absolute http(s) cover image URLs', () => {
    expect(safeImageUrl('https://cdn.example.com/cover.png')).toBe('https://cdn.example.com/cover.png')
    expect(safeImageUrl('javascript:alert(1)')).toBeNull()
    expect(safeImageUrl('data:image/png;base64,AAAA')).toBeNull()
    expect(safeImageUrl('/relative/cover.png')).toBeNull()
    expect(safeImageUrl(null)).toBeNull()
  })
})
