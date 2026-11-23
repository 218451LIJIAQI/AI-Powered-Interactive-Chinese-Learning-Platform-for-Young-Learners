import type { Translate } from '@/plugins/i18n'

/** Study time, levels and titles of the learning plan, written in the interface language. */
import type { MandarinLevel, PracticeType } from '@/types/learn'
import type { PlanPace, PlanPaceBand, PlanReason, PlanStatus } from '@/types/plan'

import { LEVEL_CODE_KEYS, PACES } from './planLabels'

/** "61–122 hours": the study time of so many days of a band, in whole hours. */
function studyHours(t: Translate, days: number, pace: PlanPaceBand): string {
  const hours = (minutes: number) => Math.round((days * minutes) / 60)
  const least = hours(pace.minMinutesPerDay)
  if (pace.maxMinutesPerDay === null) return t('plan.hours.atLeast', { hours: Math.max(1, least) })
  const most = Math.max(1, hours(pace.maxMinutesPerDay))
  if (least === 0) return t('plan.hours.under', { hours: most }, most)
  if (least === most) return t('plan.hours.exact', { hours: least }, least)
  return t('plan.hours.range', { least, most })
}

export function paceInSentence(t: Translate, key: PlanPace): string {
  const pace = PACES[key]
  return pace ? t(pace.inline ?? pace.label) : ''
}

/**
 * The line that joins the two choices. In the edit dialog and drawer it shows the sum, "61 days × 1–2 hours a day =
 * 61–122 hours of Chinese"; in the setup, under a date field that already shows the date and the days to go, it says
 * only what they add up to (`sentence`): "1–2 hours a day adds up to 61–122 hours of Chinese." (S1.18). Every number
 * comes from the learner's own date and band.
 */
export function studyFormula(t: Translate, days: number, pace: PlanPaceBand, sentence = false): string {
  const safeDays = Math.max(1, Math.round(days))
  const hours = studyHours(t, safeDays, pace)
  if (sentence) return t('plan.formula.total', { daily: paceDaily(t, pace), hours })
  return t('plan.formula.plain', {
    days: t('plan.common.days', { count: safeDays }, safeDays),
    pace: paceInSentence(t, pace.key),
    hours,
  })
}

/**
 * "1–2 hours a day"; empty for a time band this version does not know (a plan the server has not migrated yet), so
 * the page still shows the rest of the plan.
 */
export function paceDaily(t: Translate, pace: PlanPaceBand): string {
  const band = PACES[pace.key]
  return band ? t('plan.pace.daily', { pace: t(band.label) }) : ''
}

/** SparkBot's goal title, or the reason ("Travel") for a plan built from rules. */
export function goalTitle(t: Translate, title: string | null, reason: PlanReason): string {
  return title ?? t(`plan.reason.${reason}`)
}

/** Only unfinished plans expire, on the platform day after the goal date. */
export function planExpired(status: PlanStatus, deadline: string | null, today: string | null): boolean {
  return status !== 'COMPLETE' && deadline !== null && today !== null && deadline < today
}

/** A practice set the plan suggests, named with its type ("Listening set: At the Café"). */
export function practiceSetTitle(t: Translate, type: PracticeType, title: string): string {
  return t('plan.practiceType.setTitle', { type: t(`practiceType.${type}`), title })
}

/** Short stats name a level by its code: one level when the goal is the level already reached ("HSK 2"). */
export function levelSpan(t: Translate, placed: MandarinLevel, target: MandarinLevel): string {
  if (placed === target) return t(LEVEL_CODE_KEYS[placed])
  return t('plan.level.fromTo', { from: t(LEVEL_CODE_KEYS[placed]), to: t(LEVEL_CODE_KEYS[target]) })
}

/** A group of Learn units, by the first and last unit in plan order. */
export function unitsWord(t: Translate, units: readonly number[]): string {
  return units.length === 1
    ? t('plan.path.unitOne', { unit: units[0] })
    : t('plan.path.unitRange', { from: units[0], to: units.at(-1) })
}
