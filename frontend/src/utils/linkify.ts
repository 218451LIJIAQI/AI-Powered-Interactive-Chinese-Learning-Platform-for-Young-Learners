import { safeHttpUrl } from '@/utils/url'

/**
 * Plain-text links in teacher-written lesson text (FR-CONT-05 external learning links).
 *
 * The text is never parsed as HTML: it is split into plain runs and link runs, and the page renders
 * each run with text interpolation (`{{ }}`), so markup typed by a teacher is shown literally.
 * Only absolute http(s) URLs that pass {@link safeHttpUrl} become links; anything else stays text.
 */
export interface LinkRun {
  text: string
  /** Normalised safe URL when this run is a link. */
  href?: string
}

/** `http(s)://…` or `www.…` up to the next whitespace, quote or angle bracket. */
const URL_CANDIDATE = /\b(?:https?:\/\/|www\.)[^\s<>"'`]+/gi
/** Punctuation that usually ends a sentence rather than the URL (ASCII and full-width CJK). */
const TRAILING_PUNCTUATION = /[.,;:!?'")\]}>。，；：！？、）」』】]+$/u
const MAX_LINK_LENGTH = 2048

function trimTrailingPunctuation(candidate: string): string {
  let url = candidate.replace(TRAILING_PUNCTUATION, '')
  // Keep a closing parenthesis that belongs to the URL, e.g. https://en.wikipedia.org/wiki/Pinyin_(disambiguation)
  const opens = (url.match(/\(/g) ?? []).length
  const closes = (url.match(/\)/g) ?? []).length
  if (opens > closes && candidate.slice(url.length).startsWith(')')) {
    url += ')'
  }
  return url
}

export function splitLinkRuns(text: string | null | undefined): LinkRun[] {
  const source = text ?? ''
  const runs: LinkRun[] = []
  let cursor = 0

  for (const match of source.matchAll(URL_CANDIDATE)) {
    const start = match.index ?? 0
    const raw = trimTrailingPunctuation(match[0])
    if (!raw || raw.length > MAX_LINK_LENGTH) continue
    const href = safeHttpUrl(raw.toLowerCase().startsWith('www.') ? `https://${raw}` : raw)
    if (!href) continue

    if (start > cursor) runs.push({ text: source.slice(cursor, start) })
    runs.push({ text: raw, href })
    cursor = start + raw.length
  }

  if (cursor < source.length) runs.push({ text: source.slice(cursor) })
  return mergeTextRuns(runs)
}

function mergeTextRuns(runs: LinkRun[]): LinkRun[] {
  return runs.reduce<LinkRun[]>((merged, run) => {
    const previous = merged[merged.length - 1]
    if (previous && !previous.href && !run.href) {
      previous.text += run.text
    } else {
      merged.push({ ...run })
    }
    return merged
  }, [])
}

/** Cover images must be absolute http(s) URLs; anything else is not rendered. */
export function safeImageUrl(value: string | null | undefined): string | null {
  return safeHttpUrl(value)
}
