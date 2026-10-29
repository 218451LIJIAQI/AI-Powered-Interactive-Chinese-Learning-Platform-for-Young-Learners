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
 * Han characters plus the CJK punctuation / full-width forms that usually sit next to them (，。？！（）：).
 * Kept as one character class so a sentence such as `你好吗？` stays a single run.
 */
const CJK_CHARACTER = /[\p{Script=Han}\u{3000}-\u{303f}\u{ff00}-\u{ffef}]/u
const HAN_CHARACTER = /\p{Script=Han}/u

/** Splits text into alternating CJK / non-CJK runs; empty input gives an empty list. */
export function splitCjkRuns(text: string | null | undefined): TextRun[] {
  const source = text ?? ''
  const runs: TextRun[] = []
  let current = ''
  let currentCjk = false

  for (const character of source) {
    const cjk = CJK_CHARACTER.test(character)
    if (current && cjk !== currentCjk) {
      runs.push({ text: current, cjk: currentCjk })
      current = ''
    }
    current += character
    currentCjk = cjk
  }

  if (current) runs.push({ text: current, cjk: currentCjk })

  // A run made only of full-width punctuation (no Han character) reads better in the surrounding font.
  return mergeAdjacentRuns(runs.map((run) => (run.cjk && !HAN_CHARACTER.test(run.text) ? { ...run, cjk: false } : run)))
}

function mergeAdjacentRuns(runs: TextRun[]): TextRun[] {
  return runs.reduce<TextRun[]>((merged, run) => {
    const previous = merged[merged.length - 1]
    if (previous && previous.cjk === run.cjk) {
      previous.text += run.text
    } else {
      merged.push({ ...run })
    }
    return merged
  }, [])
}
