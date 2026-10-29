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

/** A choice SparkBot answers with a short reply once it is picked. */
interface RepliedChoiceLabel extends ChoiceLabel {
  readonly reply: string
}

export const REASONS: Readonly<Record<PlanReason, RepliedChoiceLabel>> = {
  TRAVEL: {
    label: 'plan.reason.TRAVEL',
    hint: 'plan.reason.TRAVEL_hint',
    reply: 'plan.reason.TRAVEL_reply',
    icon: 'globe',
  },
  WORK: {
    label: 'plan.reason.WORK',
    hint: 'plan.reason.WORK_hint',
    reply: 'plan.reason.WORK_reply',
    icon: 'briefcase',
  },
  STUDY: {
    label: 'plan.reason.STUDY',
    hint: 'plan.reason.STUDY_hint',
    reply: 'plan.reason.STUDY_reply',
    icon: 'classes',
  },
  EVERYDAY: {
    label: 'plan.reason.EVERYDAY',
    hint: 'plan.reason.EVERYDAY_hint',
    reply: 'plan.reason.EVERYDAY_reply',
    icon: 'chat',
  },
  FRIENDS_FAMILY: {
    label: 'plan.reason.FRIENDS_FAMILY',
    hint: 'plan.reason.FRIENDS_FAMILY_hint',
    reply: 'plan.reason.FRIENDS_FAMILY_reply',
    icon: 'users',
  },
  CULTURE: {
    label: 'plan.reason.CULTURE',
    hint: 'plan.reason.CULTURE_hint',
    reply: 'plan.reason.CULTURE_reply',
    icon: 'celebrate',
  },
  JUST_FOR_ME: {
    label: 'plan.reason.JUST_FOR_ME',
    hint: 'plan.reason.JUST_FOR_ME_hint',
    reply: 'plan.reason.JUST_FOR_ME_reply',
    icon: 'star',
  },
  OTHER: { label: 'plan.reason.OTHER', hint: 'plan.reason.OTHER_hint', reply: 'plan.reason.OTHER_reply', icon: 'pen' },
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

export const PACES: Readonly<Record<PlanPace, RepliedChoiceLabel>> = {
  LIGHT: { label: 'plan.pace.LIGHT', hint: 'plan.pace.LIGHT_hint', reply: 'plan.pace.LIGHT_reply', icon: 'clock' },
  REGULAR: {
    label: 'plan.pace.REGULAR',
    hint: 'plan.pace.REGULAR_hint',
    reply: 'plan.pace.REGULAR_reply',
    icon: 'calendar',
  },
  SERIOUS: {
    label: 'plan.pace.SERIOUS',
    hint: 'plan.pace.SERIOUS_hint',
    reply: 'plan.pace.SERIOUS_reply',
    icon: 'streak',
  },
  INTENSE: {
    label: 'plan.pace.INTENSE',
    hint: 'plan.pace.INTENSE_hint',
    reply: 'plan.pace.INTENSE_reply',
    icon: 'flash',
  },
}

export const FOCUSES: Readonly<Record<PlanFocus, ChoiceLabel>> = {
  SPEAKING: { label: 'plan.focus.SPEAKING', hint: 'plan.focus.SPEAKING_hint', icon: 'mic' },
  LISTENING: { label: 'plan.focus.LISTENING', hint: 'plan.focus.LISTENING_hint', icon: 'listening' },
  READING: { label: 'plan.focus.READING', hint: 'plan.focus.READING_hint', icon: 'learn' },
  WRITING: { label: 'plan.focus.WRITING', hint: 'plan.focus.WRITING_hint', icon: 'brush' },
  VOCABULARY: { label: 'plan.focus.VOCABULARY', hint: 'plan.focus.VOCABULARY_hint', icon: 'layers' },
  CONVERSATION: { label: 'plan.focus.CONVERSATION', hint: 'plan.focus.CONVERSATION_hint', icon: 'chat' },
  BALANCED: { label: 'plan.focus.BALANCED', hint: 'plan.focus.BALANCED_hint', icon: 'checklist' },
}

export const SELF_LEVELS: Readonly<Record<PlanSelfLevel, ChoiceLabel>> = {
  NEW_TO_MANDARIN: {
    label: 'plan.selfLevel.NEW_TO_MANDARIN',
    hint: 'plan.selfLevel.NEW_TO_MANDARIN_hint',
    icon: 'star',
  },
  A_LITTLE: { label: 'plan.selfLevel.A_LITTLE', hint: 'plan.selfLevel.A_LITTLE_hint', icon: 'chat' },
  THE_BASICS: { label: 'plan.selfLevel.THE_BASICS', hint: 'plan.selfLevel.THE_BASICS_hint', icon: 'learn' },
  NOT_SURE: { label: 'plan.selfLevel.NOT_SURE', hint: 'plan.selfLevel.NOT_SURE_hint', icon: 'hint' },
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

export const LEVELS: readonly MandarinLevel[] = ['NEW_TO_MANDARIN', 'BEGINNER_1', 'BEGINNER_2']

export const SKILL_KEYS: Readonly<Record<LevelCheckSkill, string>> = {
  VOCABULARY: 'plan.skill.VOCABULARY',
  LISTENING: 'plan.skill.LISTENING',
  CHARACTERS: 'plan.skill.CHARACTERS',
  SENTENCES: 'plan.skill.SENTENCES',
  PINYIN: 'plan.skill.PINYIN',
}

/** The focus the level check suggests for a skill that was new to the student. */
export const FOCUS_FOR_SKILL: Readonly<Record<LevelCheckSkill, PlanFocus>> = {
  VOCABULARY: 'VOCABULARY',
  LISTENING: 'LISTENING',
  CHARACTERS: 'READING',
  SENTENCES: 'CONVERSATION',
  PINYIN: 'SPEAKING',
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
