/** Dates, spans and titles of the learning plan, written in the interface language. */
import type { PlanGoal, PlanReason } from '@/types/plan'
import { formatDate } from '@/utils/format'

import { GOAL_KEYS, REASONS } from './planLabels'

type Translate = (key: string, values?: Record<string, unknown>, plural?: number) => string

/** "Sat 18 Jul 2026". */
export function longDate(iso: string | null | undefined): string {
  return formatDate(iso, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })
}

/** "18 Jul". */
export function shortDate(iso: string | null | undefined): string {
  return formatDate(iso, { day: 'numeric', month: 'short' })
}

/** "Monday, May 18" for the Home eyebrow. */
export function weekdayDate(iso: string | null | undefined): string {
  return formatDate(iso, { weekday: 'long', month: 'long', day: 'numeric' })
}

/** The local calendar date as yyyy-MM-dd. */
export function isoDay(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

export function addDays(from: Date, days: number): Date {
  const date = new Date(from.getFullYear(), from.getMonth(), from.getDate())
  date.setDate(date.getDate() + days)
  return date
}

export function addMonths(from: Date, months: number): Date {
  const date = new Date(from.getFullYear(), from.getMonth(), from.getDate())
  date.setMonth(date.getMonth() + months)
  return date
}

/** "in 2 months" / "in 3 weeks" between today and a deadline. */
export function spanUntil(t: Translate, deadline: string, today = new Date()): string {
  const [year, month, day] = deadline.split('-').map(Number)
  const days = Math.max(
    1,
    Math.round((new Date(year, month - 1, day).getTime() - addDays(today, 0).getTime()) / 86_400_000),
  )
  const span =
    days >= 56
      ? t('plan.common.months', { count: Math.round(days / 30) }, Math.round(days / 30))
      : t('plan.common.weeks', { count: Math.max(1, Math.round(days / 7)) }, Math.max(1, Math.round(days / 7)))
  return t('plan.common.inSpan', { span })
}

/** SparkBot's goal title, or the reason ("Travel") for a plan built from rules. */
export function goalTitle(t: Translate, title: string | null | undefined, reason: PlanReason): string {
  return title?.trim() || t(REASONS[reason].label)
}

/** "15 min · 5 days" or "20 min · every day". */
export function paceText(t: Translate, pace: { minutesPerDay: number; daysPerWeek: number }): string {
  return pace.daysPerWeek >= 7
    ? t('plan.pace.everyDay', { minutes: pace.minutesPerDay })
    : t('plan.pace.daysAWeek', { minutes: pace.minutesPerDay, days: pace.daysPerWeek })
}

/** Goal labels joined for a sentence ("order food, ask for directions"). */
export function goalList(t: Translate, goals: readonly PlanGoal[]): string {
  return goals.map((goal) => t(GOAL_KEYS[goal])).join(', ')
}
