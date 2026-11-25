import type { Translate } from '@/plugins/i18n'
import type { AdminUnitRow } from '@/types/learn'
import { formatNumber } from '@/utils/format'
import { setStatusKey } from '@/utils/learnLabels'

type UnitCounts = Pick<AdminUnitRow, 'lessonCount' | 'bonusCount'>

/**
 * "7 lessons", or "7 lessons + bonus chest" for a unit with one (keys of the `adminLearning` bundle). The server's
 * `lessonCount` is the unit's lessons only, as on the learner's path: its bonus chests are `bonusCount`, named beside.
 */
export function unitLessonsLabel(t: Translate, unit: UnitCounts): string {
  const lessons = t('adminLearning.unitLessons', { count: unit.lessonCount }, unit.lessonCount)
  return unit.bonusCount > 0
    ? t('adminLearning.unitLessonsBonus', { lessons, count: unit.bonusCount }, unit.bonusCount)
    : lessons
}

/** "8 learners started": only a published unit has learners on it. */
function learnersStarted(t: Translate, unit: AdminUnitRow): string[] {
  if (unit.status !== 'PUBLISHED') return []
  return [t('adminLearning.learnersStarted', { count: formatNumber(unit.studentsStarted) }, unit.studentsStarted)]
}

/** A unit row of the Learning path (A4.01): "7 lessons + bonus chest · 8 learners started". */
export function unitRowMeta(t: Translate, unit: AdminUnitRow): string {
  return [unitLessonsLabel(t, unit), ...learnersStarted(t, unit)].join(' · ')
}

/** The unit drawer's line under its title (A4.02): "HSK 1 · 8 learners started · Published". */
export function unitDrawerMeta(t: Translate, unit: AdminUnitRow): string {
  return [t('hsk.number', { level: unit.hskLevel }), ...learnersStarted(t, unit), t(setStatusKey(unit.status))].join(
    ' · ',
  )
}
