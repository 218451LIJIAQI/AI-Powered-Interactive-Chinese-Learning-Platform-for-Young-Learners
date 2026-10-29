import { parseDate } from '@/utils/format'

/**
 * The characters stamped on seals: a lesson's place in its unit (一 … 十) and a streak day's weekday (周一 … 周日).
 * They double as a small reading lesson; the accessible names always say the same thing in words.
 */
const NUMERALS = ['〇', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十'] as const
const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六'] as const

/** 1–10 as a Chinese numeral; larger numbers stay in digits so a seal never holds two characters. */
export function hanziNumeral(value: number): string {
  return Number.isInteger(value) && value >= 1 && value <= 10 ? NUMERALS[value]! : String(value)
}

/** The weekday character of a calendar date (`yyyy-MM-dd`): 一 for Monday … 日 for Sunday. */
export function hanziWeekday(iso: string): string {
  const date = parseDate(iso)
  return date ? WEEKDAYS[date.getDay()]! : ''
}
