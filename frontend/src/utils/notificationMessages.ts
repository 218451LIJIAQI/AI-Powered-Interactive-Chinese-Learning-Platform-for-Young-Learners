/**
 * Localiser for in-app notification titles and messages (stored in English by the backend).
 *
 * Kept separate from `serverMessages.ts` so Vite code-splits the notification rows, regex template matcher, and
 * `formatDate` helper into the Notifications page chunk instead of the main application bundle.
 */
import type { AppLanguage } from '@/types/api'
import { formatDate } from '@/utils/format'

import { NOTIFICATION_MESSAGES } from './serverMessageRows/notifications'
import type { MessageRow } from './serverMessageRows/types'
import { collapseWhitespace, messageKey } from './serverMessages'

type TranslatedLocale = Exclude<AppLanguage, 'en'>
type MessageLocalizer = (message: string, locale: AppLanguage) => string

/** A placeholder of a template row: `{title}`. */
const PLACEHOLDER = /\{([A-Za-z][A-Za-z0-9]*)\}/g

/** The placeholders of a row's text in order ("{name} joined {class}." has name, class); none in a fixed message. */
export function placeholderNames(text: string): string[] {
  return Array.from(text.matchAll(PLACEHOLDER), (match) => match[1])
}

/** Fixed text of a template as a pattern: any run of spaces matches any other (case is ignored by the `i` flag). */
function textPattern(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/\s+/g, '\\s+')
}

type Template = {
  pattern: RegExp
  /** The placeholder of each capture group, in the order of the English text. */
  names: string[]
  translations: Readonly<Record<TranslatedLocale, string>>
  /** Characters of fixed text: of two templates that fit one message, the one with more wins. */
  fixedLength: number
}

/**
 * The English text of a template row as an anchored pattern. A value is any text, as short as the rest of the message
 * allows, so it never takes in the fixed words after it. After fixed text at the end a full stop is optional, as for a
 * fixed message; after a value at the end it belongs to the value.
 */
function compileTemplate([en, ms, zh]: MessageRow): Template {
  const english = collapseWhitespace(en)
  let source = ''
  let end = 0
  for (const match of english.matchAll(PLACEHOLDER)) {
    source += textPattern(english.slice(end, match.index)) + '([\\s\\S]+?)'
    end = match.index + match[0].length
  }
  const tail = english.slice(end)
  if (tail) source += textPattern(tail.replace(/\.$/, '')) + '\\.?'
  return {
    pattern: new RegExp(`^${source}$`, 'i'),
    names: placeholderNames(english),
    translations: { ms, 'zh-CN': zh },
    fixedLength: english.replace(PLACEHOLDER, '').length,
  }
}

const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
const MONTHS = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
]
/**
 * "3:00 PM on Saturday 14 November 2026": a live session's start as its stored reminder wrote it (the removed
 * LiveSessionReminderService), a wall-clock time in the session's zone.
 */
const SESSION_START = /^(\d{1,2}):(\d{2}) ([AP]M) on ([A-Za-z]+) (\d{1,2}) ([A-Za-z]+) (\d{4})$/

/** A live session's start in the reader's language, on the same wall clock; any other text is kept as sent. */
function localizedSessionStart(value: string, locale: TranslatedLocale): string {
  const match = SESSION_START.exec(value)
  if (!match) return value
  const [, hour, minute, half, weekday, day, month, year] = match
  const hours = Number(hour)
  const minutes = Number(minute)
  const monthIndex = MONTHS.indexOf(month)
  if (hours < 1 || hours > 12 || minutes >= 60 || monthIndex < 0) return value

  const dayOfMonth = Number(day)
  const date = new Date(
    Date.UTC(Number(year), monthIndex, dayOfMonth, (hours % 12) + (half === 'PM' ? 12 : 0), minutes),
  )
  if (date.getUTCDate() !== dayOfMonth || WEEKDAYS[date.getUTCDay()] !== weekday) return value
  return formatDate(date, { dateStyle: 'full', timeStyle: 'short', timeZone: 'UTC' }, locale)
}

/** Lazy fixed + template matcher; exported so matching rules can be unit-tested on custom rows. */
export function createNotificationMessageLocalizer(rows: readonly MessageRow[]): MessageLocalizer {
  let compiled: {
    fixed: Map<string, Readonly<Record<TranslatedLocale, string>>>
    templates: Template[]
  } | null = null

  function ensureCompiled() {
    if (compiled) return compiled
    const fixed = new Map<string, Readonly<Record<TranslatedLocale, string>>>()
    const templates: Template[] = []
    for (const row of rows) {
      if (placeholderNames(row[0]).length) templates.push(compileTemplate(row))
      else fixed.set(messageKey(row[0]), { ms: row[1], 'zh-CN': row[2] })
    }
    // A stable sort: of two templates with as much fixed text, the earlier row wins.
    templates.sort((a, b) => b.fixedLength - a.fixedLength)
    compiled = { fixed, templates }
    return compiled
  }

  return (message, locale) => {
    if (locale === 'en') return message
    const { fixed, templates } = ensureCompiled()
    const row = fixed.get(messageKey(message))
    if (row) return row[locale]
    const trimmed = message.trim()
    for (const template of templates) {
      const match = template.pattern.exec(trimmed)
      if (!match || match.slice(1).some((value) => !value.trim())) continue
      const values = new Map(template.names.map((name, index) => [name, match[index + 1]]))
      return template.translations[locale].replace(PLACEHOLDER, (_, name: string) => {
        const value = values.get(name) ?? ''
        return name === 'start' ? localizedSessionStart(value, locale) : value
      })
    }
    return message
  }
}

/** Localises a stored English notification title or body into the interface language; unknown text stays unchanged. */
export const localizeNotificationMessage: MessageLocalizer = createNotificationMessageLocalizer(NOTIFICATION_MESSAGES)
