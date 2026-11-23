/**
 * Full message keys (of the `plan` bundles: planSummaryMessages / planSetupMessages) for the learning plan's enum
 * values, and the icons that go with them.
 */
import type { LevelCheckSkill, MandarinLevel } from '@/types/learn'
import type { PlanFocus, PlanPace, PlanReason, PlanSelfLevel } from '@/types/plan'

/** A setup choice as a card: its label, hint and icon. */
interface ChoiceLabel {
  readonly label: string
  readonly hint: string
  readonly icon: string
}

export const REASON_ICONS: Readonly<Record<PlanReason, string>> = {
  TRAVEL: 'map',
  WORK: 'briefcase',
  STUDY: 'classes',
  EVERYDAY: 'users',
  JUST_FOR_ME: 'globe',
  OTHER: 'pen',
}

/**
 * "Time I can give each day": the option's label ("Under 30 min"), the same band inside a sentence ("under 30 min"),
 * and its icon.
 */
export const PACES: Readonly<
  Record<PlanPace, { readonly label: string; readonly inline?: string; readonly icon: string }>
> = {
  UNDER_30_MIN: { label: 'plan.pace.UNDER_30_MIN', inline: 'plan.pace.UNDER_30_MIN_inline', icon: 'hint' },
  MINUTES_30_TO_60: { label: 'plan.pace.MINUTES_30_TO_60', icon: 'clock' },
  HOURS_1_TO_2: { label: 'plan.pace.HOURS_1_TO_2', icon: 'streak' },
  HOURS_2_PLUS: { label: 'plan.pace.HOURS_2_PLUS', icon: 'flash' },
}

/** The four focuses the setup offers, in the prototype's order. None picked means a balanced plan. */
export const FOCUS_ICONS: Readonly<Record<PlanFocus, string>> = {
  LISTENING: 'listening',
  SPEAKING: 'mic',
  READING: 'learn',
  WRITING: 'pen',
}

/** The code alone, for short stats and the first three rungs of the ladder. */
export const LEVEL_CODE_KEYS: Readonly<Record<MandarinLevel, string>> = {
  NEW_TO_MANDARIN: 'plan.ladder.PRE_HSK',
  BEGINNER_1: 'plan.ladder.HSK_1',
  BEGINNER_2: 'plan.ladder.HSK_2',
}

/**
 * The six answers to "How much Mandarin do you know?": the HSK code as the label and its one word as the tag. Each
 * only sets where the level check starts (`self`).
 */
export type SelfLevelChoice = 'NEW' | 'BEGINNER_1' | 'BEGINNER_2' | 'ELEMENTARY' | 'INTERMEDIATE' | 'NOT_SURE'

export const SELF_LEVELS: Readonly<
  Record<SelfLevelChoice, { readonly self: PlanSelfLevel; readonly tag: string | null } & ChoiceLabel>
> = {
  NEW: {
    self: 'NEW_TO_MANDARIN',
    label: LEVEL_CODE_KEYS.NEW_TO_MANDARIN,
    tag: 'plan.ladder.NEW',
    hint: 'plan.selfLevel.NEW_hint',
    icon: 'star',
  },
  BEGINNER_1: {
    self: 'A_LITTLE',
    label: LEVEL_CODE_KEYS.BEGINNER_1,
    tag: 'plan.ladder.BASIC',
    hint: 'plan.selfLevel.BEGINNER_1_hint',
    icon: 'hand',
  },
  BEGINNER_2: {
    self: 'THE_BASICS',
    label: LEVEL_CODE_KEYS.BEGINNER_2,
    tag: 'plan.ladder.SURVIVAL',
    hint: 'plan.selfLevel.BEGINNER_2_hint',
    icon: 'utensils',
  },
  ELEMENTARY: {
    self: 'THE_BASICS',
    label: 'plan.ladder.HSK_3',
    tag: 'plan.ladder.CONVERSATIONAL',
    hint: 'plan.selfLevel.ELEMENTARY_hint',
    icon: 'chat',
  },
  INTERMEDIATE: {
    self: 'THE_BASICS',
    label: 'plan.ladder.HSK_4',
    tag: 'plan.ladder.CONFIDENT',
    hint: 'plan.selfLevel.INTERMEDIATE_hint',
    icon: 'classes',
  },
  NOT_SURE: {
    self: 'NOT_SURE',
    label: 'plan.selfLevel.NOT_SURE',
    tag: null,
    hint: 'plan.selfLevel.NOT_SURE_hint',
    icon: 'target',
  },
}

/** A Mandarin level as the HSK code with its one word: "HSK 1 (Basic)". */
export const LEVEL_KEYS: Readonly<Record<MandarinLevel, string>> = {
  NEW_TO_MANDARIN: 'plan.level.NEW_TO_MANDARIN',
  BEGINNER_1: 'hsk.level1',
  BEGINNER_2: 'hsk.level2',
}

/** The Mandarin ladder as the Result shows it; the platform places learners on its first three rungs. */
export const LADDER: readonly { readonly level: MandarinLevel | null; readonly code: string }[] = [
  { level: 'NEW_TO_MANDARIN', code: LEVEL_CODE_KEYS.NEW_TO_MANDARIN },
  { level: 'BEGINNER_1', code: LEVEL_CODE_KEYS.BEGINNER_1 },
  { level: 'BEGINNER_2', code: LEVEL_CODE_KEYS.BEGINNER_2 },
  { level: null, code: 'plan.ladder.HSK_3' },
  { level: null, code: 'plan.ladder.HSK_4' },
]

/** How high a level is on the ladder, to compare two levels. */
export function ladderRung(level: MandarinLevel): number {
  return LADDER.findIndex((rung) => rung.level === level)
}

/** The focus the level check suggests for a skill that was new to the learner. */
export const FOCUS_FOR_SKILL: Readonly<Record<LevelCheckSkill, PlanFocus>> = {
  VOCABULARY: 'READING',
  LISTENING: 'LISTENING',
  CHARACTERS: 'READING',
  SENTENCES: 'SPEAKING',
  PINYIN: 'SPEAKING',
  NUMBERS: 'LISTENING',
  GRAMMAR: 'SPEAKING',
  IDIOMS: 'READING',
}
