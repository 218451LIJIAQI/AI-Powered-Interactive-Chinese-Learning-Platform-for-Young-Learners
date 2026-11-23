/**
 * Helpers for showing lesson text that mixes Chinese characters, Pinyin and English.
 *
 * Chinese runs are rendered as `<span lang="zh-CN" class="cjk-text">` so screen readers and fonts treat them as
 * Simplified Chinese; everything else (Pinyin with tone marks, English, numbers) stays in the page font.
 */
interface TextRun {
  text: string
  cjk: boolean
}

/**
 * A Chinese run: Han characters and the CJK punctuation / full-width forms that sit next to them (，。？！（）：), so
 * `你好吗？` stays one run. Punctuation with no Han character beside it reads better in the surrounding font.
 */
const CJK_RUN = /[\p{Script=Han}\u{3000}-\u{303f}\u{ff00}-\u{ffef}]+/gu

/** True for text with at least one Chinese character: it is then set in the Chinese font and marked `lang="zh-CN"`. */
export function hasHan(text: string): boolean {
  return /\p{Script=Han}/u.test(text)
}

/** Splits text into alternating CJK / non-CJK runs; empty input gives an empty list. */
export function splitCjkRuns(text: string | null | undefined): TextRun[] {
  const source = text ?? ''
  const runs: TextRun[] = []
  let end = 0

  for (const match of source.matchAll(CJK_RUN)) {
    if (!hasHan(match[0])) continue
    if (match.index > end) runs.push({ text: source.slice(end, match.index), cjk: false })
    runs.push({ text: match[0], cjk: true })
    end = match.index + match[0].length
  }

  if (end < source.length) runs.push({ text: source.slice(end), cjk: false })
  return runs
}
