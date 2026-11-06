/**
 * Full message keys (of the `plan` bundle, plugins/planMessages.ts) for the learning plan's enum values, and the
 * icons that go with them.
 */
import type { LevelCheckSkill, MandarinLevel, PracticeType } from '@/types/learn'
import type { PlanFocus, PlanGoal, PlanPace, PlanReason, PlanSelfLevel, PlanStatus, StepStatus } from '@/types/plan'

/** A setup choice as a card: its label, hint and icon. */
interface ChoiceLabel {
  readonly label: string
  readonly hint: string
  readonly icon: string
}

/** A reason also names what a focus or a class is for ("For your trip", "Then: pick a class for your trip"). */
interface ReasonLabel extends ChoiceLabel {
  readonly forLabel: string
  readonly forkTitle: string
}

/** A focus also says what it adds to the week, and where. */
interface FocusLabel extends ChoiceLabel {
  readonly week: string
  readonly where: string
}

export const REASONS: Readonly<Record<PlanReason, ReasonLabel>> = {
  TRAVEL: {
    label: 'plan.reason.TRAVEL',
    hint: 'plan.reason.TRAVEL_hint',
    forLabel: 'plan.reason.TRAVEL_for',
    forkTitle: 'plan.reason.TRAVEL_fork',
    icon: 'map',
  },
  WORK: {
    label: 'plan.reason.WORK',
    hint: 'plan.reason.WORK_hint',
    forLabel: 'plan.reason.WORK_for',
    forkTitle: 'plan.reason.WORK_fork',
    icon: 'briefcase',
  },
  STUDY: {
    label: 'plan.reason.STUDY',
    hint: 'plan.reason.STUDY_hint',
    forLabel: 'plan.reason.STUDY_for',
    forkTitle: 'plan.reason.STUDY_fork',
    icon: 'classes',
  },
  EVERYDAY: {
    label: 'plan.reason.EVERYDAY',
    hint: 'plan.reason.EVERYDAY_hint',
    forLabel: 'plan.reason.EVERYDAY_for',
    forkTitle: 'plan.reason.EVERYDAY_fork',
    icon: 'users',
  },
  FRIENDS_FAMILY: {
    label: 'plan.reason.FRIENDS_FAMILY',
    hint: 'plan.reason.EVERYDAY_hint',
    forLabel: 'plan.reason.EVERYDAY_for',
    forkTitle: 'plan.reason.EVERYDAY_fork',
    icon: 'users',
  },
  CULTURE: {
    label: 'plan.reason.CULTURE',
    hint: 'plan.reason.JUST_FOR_ME_hint',
    forLabel: 'plan.reason.JUST_FOR_ME_for',
    forkTitle: 'plan.reason.JUST_FOR_ME_fork',
    icon: 'globe',
  },
  JUST_FOR_ME: {
    label: 'plan.reason.JUST_FOR_ME',
    hint: 'plan.reason.JUST_FOR_ME_hint',
    forLabel: 'plan.reason.JUST_FOR_ME_for',
    forkTitle: 'plan.reason.JUST_FOR_ME_fork',
    icon: 'globe',
  },
  OTHER: {
    label: 'plan.reason.OTHER',
    hint: 'plan.reason.OTHER_hint',
    forLabel: 'plan.reason.OTHER_for',
    forkTitle: 'plan.reason.OTHER_fork',
    icon: 'pen',
  },
}

/** The six reasons the setup offers. FRIENDS_FAMILY and CULTURE stay valid for older plans (see `shownReason`). */
export const SHOWN_REASONS: readonly PlanReason[] = ['TRAVEL', 'WORK', 'STUDY', 'EVERYDAY', 'JUST_FOR_ME', 'OTHER']

/** The reason chip an older plan's reason falls under. */
export function shownReason(reason: PlanReason): PlanReason {
  if (reason === 'FRIENDS_FAMILY') return 'EVERYDAY'
  return reason === 'CULTURE' ? 'JUST_FOR_ME' : reason
}

export const GOAL_KEYS: Readonly<Record<PlanGoal, string>> = {
  INTRODUCE_MYSELF: 'plan.goal.INTRODUCE_MYSELF',
  GREET_CUSTOMERS: 'plan.goal.GREET_CUSTOMERS',
  ORDER_FOOD: 'plan.goal.ORDER_FOOD',
  ASK_DIRECTIONS: 'plan.goal.ASK_DIRECTIONS',
  GET_AROUND: 'plan.goal.GET_AROUND',
  GO_SHOPPING: 'plan.goal.GO_SHOPPING',
  UNDERSTAND_PRICES: 'plan.goal.UNDERSTAND_PRICES',
  READ_SIGNS: 'plan.goal.READ_SIGNS',
  SHORT_CHATS: 'plan.goal.SHORT_CHATS',
  SMALL_TALK: 'plan.goal.SMALL_TALK',
  TALK_ABOUT_WORK: 'plan.goal.TALK_ABOUT_WORK',
  WRITE_MESSAGES: 'plan.goal.WRITE_MESSAGES',
  TALK_IN_CLASS: 'plan.goal.TALK_IN_CLASS',
  TELL_TIME: 'plan.goal.TELL_TIME',
  TALK_ABOUT_FAMILY: 'plan.goal.TALK_ABOUT_FAMILY',
  ENJOY_MEDIA: 'plan.goal.ENJOY_MEDIA',
  NOT_SURE: 'plan.goal.NOT_SURE',
}

/** "By the end you should be able to …" when SparkBot wrote no outcomes. */
export const OUTCOME_KEYS: Readonly<Record<PlanGoal, string>> = {
  INTRODUCE_MYSELF: 'plan.outcome.INTRODUCE_MYSELF',
  GREET_CUSTOMERS: 'plan.outcome.GREET_CUSTOMERS',
  ORDER_FOOD: 'plan.outcome.ORDER_FOOD',
  ASK_DIRECTIONS: 'plan.outcome.ASK_DIRECTIONS',
  GET_AROUND: 'plan.outcome.GET_AROUND',
  GO_SHOPPING: 'plan.outcome.GO_SHOPPING',
  UNDERSTAND_PRICES: 'plan.outcome.UNDERSTAND_PRICES',
  READ_SIGNS: 'plan.outcome.READ_SIGNS',
  SHORT_CHATS: 'plan.outcome.SHORT_CHATS',
  SMALL_TALK: 'plan.outcome.SMALL_TALK',
  TALK_ABOUT_WORK: 'plan.outcome.TALK_ABOUT_WORK',
  WRITE_MESSAGES: 'plan.outcome.WRITE_MESSAGES',
  TALK_IN_CLASS: 'plan.outcome.TALK_IN_CLASS',
  TELL_TIME: 'plan.outcome.TELL_TIME',
  TALK_ABOUT_FAMILY: 'plan.outcome.TALK_ABOUT_FAMILY',
  ENJOY_MEDIA: 'plan.outcome.ENJOY_MEDIA',
  NOT_SURE: 'plan.outcome.NOT_SURE',
}

export const PACES: Readonly<Record<PlanPace, ChoiceLabel>> = {
  LIGHT: { label: 'plan.pace.LIGHT', hint: 'plan.pace.LIGHT_hint', icon: 'clock' },
  REGULAR: { label: 'plan.pace.REGULAR', hint: 'plan.pace.REGULAR_hint', icon: 'calendar' },
  SERIOUS: { label: 'plan.pace.SERIOUS', hint: 'plan.pace.SERIOUS_hint', icon: 'streak' },
  INTENSE: { label: 'plan.pace.INTENSE', hint: 'plan.pace.INTENSE_hint', icon: 'flash' },
}

export const FOCUSES: Readonly<Record<PlanFocus, FocusLabel>> = {
  SPEAKING: {
    label: 'plan.focus.SPEAKING',
    hint: 'plan.focus.SPEAKING_hint',
    week: 'plan.focus.SPEAKING_week',
    where: 'plan.focus.SPEAKING_where',
    icon: 'mic',
  },
  LISTENING: {
    label: 'plan.focus.LISTENING',
    hint: 'plan.focus.LISTENING_hint',
    week: 'plan.focus.LISTENING_week',
    where: 'plan.focus.LISTENING_where',
    icon: 'listening',
  },
  READING: {
    label: 'plan.focus.READING',
    hint: 'plan.focus.READING_hint',
    week: 'plan.focus.READING_week',
    where: 'plan.focus.READING_where',
    icon: 'learn',
  },
  WRITING: {
    label: 'plan.focus.WRITING',
    hint: 'plan.focus.WRITING_hint',
    week: 'plan.focus.WRITING_week',
    where: 'plan.focus.WRITING_where',
    icon: 'pen',
  },
  VOCABULARY: {
    label: 'plan.focus.VOCABULARY',
    hint: 'plan.focus.READING_hint',
    week: 'plan.focus.VOCABULARY_week',
    where: 'plan.focus.VOCABULARY_where',
    icon: 'layers',
  },
  CONVERSATION: {
    label: 'plan.focus.CONVERSATION',
    hint: 'plan.focus.SPEAKING_hint',
    week: 'plan.focus.SPEAKING_week',
    where: 'plan.focus.CONVERSATION_where',
    icon: 'chat',
  },
  BALANCED: {
    label: 'plan.focus.BALANCED',
    hint: 'plan.focus.READING_hint',
    week: 'plan.focus.READING_week',
    where: 'plan.focus.READING_where',
    icon: 'checklist',
  },
}

/** The four focuses the setup offers, in the prototype's order. None picked means a balanced plan. */
export const SHOWN_FOCUSES: readonly PlanFocus[] = ['LISTENING', 'SPEAKING', 'READING', 'WRITING']

/** The six answers to "How much Mandarin do you know?". Each only sets where the level check starts (`self`). */
export type SelfLevelChoice = 'NEW' | 'BEGINNER_1' | 'BEGINNER_2' | 'ELEMENTARY' | 'INTERMEDIATE' | 'NOT_SURE'

export const SELF_LEVELS: Readonly<
  Record<SelfLevelChoice, { readonly self: PlanSelfLevel; readonly tag: string | null } & ChoiceLabel>
> = {
  NEW: {
    self: 'NEW_TO_MANDARIN',
    label: 'plan.selfLevel.NEW',
    hint: 'plan.selfLevel.NEW_hint',
    tag: 'plan.ladder.PRE_HSK',
    icon: 'star',
  },
  BEGINNER_1: {
    self: 'A_LITTLE',
    label: 'plan.selfLevel.BEGINNER_1',
    hint: 'plan.selfLevel.BEGINNER_1_hint',
    tag: 'plan.ladder.HSK_1',
    icon: 'hand',
  },
  BEGINNER_2: {
    self: 'THE_BASICS',
    label: 'plan.selfLevel.BEGINNER_2',
    hint: 'plan.selfLevel.BEGINNER_2_hint',
    tag: 'plan.ladder.HSK_2',
    icon: 'utensils',
  },
  ELEMENTARY: {
    self: 'THE_BASICS',
    label: 'plan.selfLevel.ELEMENTARY',
    hint: 'plan.selfLevel.ELEMENTARY_hint',
    tag: 'plan.ladder.HSK_3',
    icon: 'chat',
  },
  INTERMEDIATE: {
    self: 'THE_BASICS',
    label: 'plan.selfLevel.INTERMEDIATE',
    hint: 'plan.selfLevel.INTERMEDIATE_hint',
    tag: 'plan.ladder.HSK_4',
    icon: 'classes',
  },
  NOT_SURE: {
    self: 'NOT_SURE',
    label: 'plan.selfLevel.NOT_SURE',
    hint: 'plan.selfLevel.NOT_SURE_hint',
    tag: null,
    icon: 'target',
  },
}

/** Where the level check starts for the student's own estimate (the server uses the same rule). */
export const CHECK_START: Readonly<Record<PlanSelfLevel, MandarinLevel>> = {
  NEW_TO_MANDARIN: 'NEW_TO_MANDARIN',
  A_LITTLE: 'BEGINNER_1',
  THE_BASICS: 'BEGINNER_2',
  NOT_SURE: 'BEGINNER_1',
}

export const LEVEL_KEYS: Readonly<Record<MandarinLevel, string>> = {
  NEW_TO_MANDARIN: 'plan.level.NEW_TO_MANDARIN',
  BEGINNER_1: 'plan.level.BEGINNER_1',
  BEGINNER_2: 'plan.level.BEGINNER_2',
}

/** The level with its HSK level ("Beginner 1 · HSK 1"). */
export const LEVEL_HSK_KEYS: Readonly<Record<MandarinLevel, string>> = {
  NEW_TO_MANDARIN: 'plan.level.NEW_TO_MANDARIN_hsk',
  BEGINNER_1: 'plan.level.BEGINNER_1_hsk',
  BEGINNER_2: 'plan.level.BEGINNER_2_hsk',
}

/** The Mandarin ladder as the Result shows it; the platform places students on its first three rungs. */
export const LADDER: readonly { readonly level: MandarinLevel | null; readonly hsk: string; readonly name: string }[] =
  [
    { level: 'NEW_TO_MANDARIN', hsk: 'plan.ladder.PRE_HSK', name: 'plan.ladder.NEW' },
    { level: 'BEGINNER_1', hsk: 'plan.ladder.HSK_1', name: 'plan.level.BEGINNER_1' },
    { level: 'BEGINNER_2', hsk: 'plan.ladder.HSK_2', name: 'plan.level.BEGINNER_2' },
    { level: null, hsk: 'plan.ladder.HSK_3', name: 'plan.selfLevel.ELEMENTARY' },
    { level: null, hsk: 'plan.ladder.HSK_4', name: 'plan.ladder.INTERMEDIATE' },
  ]

/** A knowledge point of the level check ("Basic vocabulary"), for the Done chips. */
export const SKILL_KEYS: Readonly<Record<LevelCheckSkill, string>> = {
  VOCABULARY: 'plan.skill.VOCABULARY',
  LISTENING: 'plan.skill.LISTENING',
  CHARACTERS: 'plan.skill.CHARACTERS',
  SENTENCES: 'plan.skill.SENTENCES',
  PINYIN: 'plan.skill.PINYIN',
  NUMBERS: 'plan.skill.NUMBERS',
  GRAMMAR: 'plan.skill.GRAMMAR',
  IDIOMS: 'plan.skill.IDIOMS',
}

/** The skill's name in a sentence ("listening was new to you"). */
export const SKILL_NAME_KEYS: Readonly<Record<LevelCheckSkill, string>> = {
  VOCABULARY: 'plan.skillName.VOCABULARY',
  LISTENING: 'plan.skillName.LISTENING',
  CHARACTERS: 'plan.skillName.CHARACTERS',
  SENTENCES: 'plan.skillName.SENTENCES',
  PINYIN: 'plan.skillName.PINYIN',
  NUMBERS: 'plan.skillName.NUMBERS',
  GRAMMAR: 'plan.skillName.GRAMMAR',
  IDIOMS: 'plan.skillName.IDIOMS',
}

/** The eyebrow over a level check question ("Vocabulary · 词语"). */
export const SKILL_EYEBROW_KEYS: Readonly<Record<LevelCheckSkill, string>> = {
  VOCABULARY: 'plan.skillEyebrow.VOCABULARY',
  LISTENING: 'plan.skillEyebrow.LISTENING',
  CHARACTERS: 'plan.skillEyebrow.CHARACTERS',
  SENTENCES: 'plan.skillEyebrow.SENTENCES',
  PINYIN: 'plan.skillEyebrow.PINYIN',
  NUMBERS: 'plan.skillEyebrow.NUMBERS',
  GRAMMAR: 'plan.skillEyebrow.GRAMMAR',
  IDIOMS: 'plan.skillEyebrow.IDIOMS',
}

/** "You can already …" for a skill the level check found known. */
export const SKILL_CAN_DO_KEYS: Readonly<Record<LevelCheckSkill, string>> = {
  VOCABULARY: 'plan.skillCanDo.VOCABULARY',
  LISTENING: 'plan.skillCanDo.LISTENING',
  CHARACTERS: 'plan.skillCanDo.CHARACTERS',
  SENTENCES: 'plan.skillCanDo.SENTENCES',
  PINYIN: 'plan.skillCanDo.PINYIN',
  NUMBERS: 'plan.skillCanDo.NUMBERS',
  GRAMMAR: 'plan.skillCanDo.GRAMMAR',
  IDIOMS: 'plan.skillCanDo.IDIOMS',
}

/** "You'll learn to …" for a skill the level check found new. */
export const SKILL_LEARN_KEYS: Readonly<Record<LevelCheckSkill, string>> = {
  VOCABULARY: 'plan.skillLearn.VOCABULARY',
  LISTENING: 'plan.skillLearn.LISTENING',
  CHARACTERS: 'plan.skillLearn.CHARACTERS',
  SENTENCES: 'plan.skillLearn.SENTENCES',
  PINYIN: 'plan.skillLearn.PINYIN',
  NUMBERS: 'plan.skillLearn.NUMBERS',
  GRAMMAR: 'plan.skillLearn.GRAMMAR',
  IDIOMS: 'plan.skillLearn.IDIOMS',
}

/** The focus the level check suggests for a skill that was new to the student. */
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

export const STATUS_KEYS: Readonly<Record<PlanStatus, string>> = {
  STARTS_TODAY: 'plan.status.STARTS_TODAY',
  ON_TRACK: 'plan.status.ON_TRACK',
  BEHIND: 'plan.status.BEHIND',
  COMPLETE: 'plan.status.COMPLETE',
}

export const STEP_STATUS_KEYS: Readonly<Record<StepStatus, string>> = {
  DONE: 'plan.stepStatus.DONE',
  IN_PROGRESS: 'plan.stepStatus.IN_PROGRESS',
  NEXT: 'plan.stepStatus.NEXT',
  LATER: 'plan.stepStatus.LATER',
  IN_MY_CLASSES: 'plan.stepStatus.IN_MY_CLASSES',
  OPTIONAL: 'plan.stepStatus.OPTIONAL',
  UNAVAILABLE: 'plan.stepStatus.UNAVAILABLE',
}

export const PRACTICE_TYPE_KEYS: Readonly<Record<PracticeType, string>> = {
  FLASHCARDS: 'plan.practiceType.FLASHCARDS',
  QUIZZES: 'plan.practiceType.QUIZZES',
  LISTENING: 'plan.practiceType.LISTENING',
  TRACING: 'plan.practiceType.TRACING',
  DRAG_DROP: 'plan.practiceType.DRAG_DROP',
  MATCHING: 'plan.practiceType.MATCHING',
  STORY: 'plan.practiceType.STORY',
}
