import { describe, expect, it } from 'vitest'

import { splitCjkRuns } from './cjkText'

describe('splitCjkRuns', () => {
  it('returns an empty list for empty input', () => {
    expect(splitCjkRuns('')).toEqual([])
    expect(splitCjkRuns(null)).toEqual([])
    expect(splitCjkRuns(undefined)).toEqual([])
  })

  it('keeps plain English and Pinyin as one non-CJK run', () => {
    expect(splitCjkRuns('nǐ hǎo - Hello')).toEqual([{ text: 'nǐ hǎo - Hello', cjk: false }])
  })

  it('separates Chinese characters from Pinyin and English', () => {
    expect(splitCjkRuns('你好 (nǐ hǎo) - Hello')).toEqual([
      { text: '你好', cjk: true },
      { text: ' (nǐ hǎo) - Hello', cjk: false },
    ])
  })

  it('keeps full-width punctuation inside the Chinese run', () => {
    expect(splitCjkRuns('A: 你好！我叫 Aina。')).toEqual([
      { text: 'A: ', cjk: false },
      { text: '你好！我叫', cjk: true },
      { text: ' Aina。', cjk: false },
    ])
  })

  it('treats punctuation-only full-width runs as non-CJK and merges them with neighbours', () => {
    expect(splitCjkRuns('Hello！ friend')).toEqual([{ text: 'Hello！ friend', cjk: false }])
  })

  it('preserves line breaks inside runs', () => {
    const runs = splitCjkRuns('谢谢\nxièxie')
    expect(runs).toEqual([
      { text: '谢谢', cjk: true },
      { text: '\nxièxie', cjk: false },
    ])
    expect(runs.map((run) => run.text).join('')).toBe('谢谢\nxièxie')
  })
})
