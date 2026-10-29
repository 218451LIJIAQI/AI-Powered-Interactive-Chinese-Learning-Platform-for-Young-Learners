/**
 * Shared data of the AI Helper tabs: Xiaomei's scenarios, the topic ideas, report reasons and labels. Every map holds
 * FULL i18n keys of the `aiHelper` bundle (plugins/aiHelperMessages.ts) or the `aiSafety` bundle.
 */
import type { PillTone } from '@/components/common/StatusPill.vue'
import { AI_REPORT_REASON_KEYS } from '@/plugins/aiSafetyMessages'
import type { AiFeatureKey, AiResponseReportReason, AiResponseReportStatus, AiStoryLine } from '@/types/api'
import type { AiReportPayload, XiaomeiScenario, XiaomeiTurn } from '@/types/ai'

export type AiHelperTab = 'talk' | 'ask' | 'quiz' | 'story' | 'voice'

/** Why the AI Helper cannot send a request right now, as the page shows it. */
export type AiBlockedReason = 'unavailable' | 'limit' | null

interface XiaomeiScenarioOption {
  key: XiaomeiScenario
  labelKey: string
  /** Xiaomei's scripted first line: it opens the scenario, the AI writes everything after it. */
  opener: { chinese: string; pinyin: string; translationKey: string }
}

export const XIAOMEI_SCENARIOS: readonly XiaomeiScenarioOption[] = [
  {
    key: 'MEET_XIAOMEI',
    labelKey: 'aiHelper.scenarioMeet',
    opener: {
      chinese: '你好！我是小美。今天过得怎么样？',
      pinyin: 'Nǐ hǎo! Wǒ shì Xiǎoměi. Jīntiān guò de zěnmeyàng?',
      translationKey: 'aiHelper.openerMeet',
    },
  },
  {
    key: 'CAFE',
    labelKey: 'aiHelper.scenarioCafe',
    opener: {
      chinese: '你好！欢迎光临。你想喝点儿什么？',
      pinyin: 'Nǐ hǎo! Huānyíng guānglín. Nǐ xiǎng hē diǎnr shénme?',
      translationKey: 'aiHelper.openerCafe',
    },
  },
  {
    key: 'ASK_THE_WAY',
    labelKey: 'aiHelper.scenarioWay',
    opener: {
      chinese: '你好！你要去哪儿？我可以帮你。',
      pinyin: 'Nǐ hǎo! Nǐ yào qù nǎr? Wǒ kěyǐ bāng nǐ.',
      translationKey: 'aiHelper.openerWay',
    },
  },
  {
    key: 'WEEKEND_PLANS',
    labelKey: 'aiHelper.scenarioWeekend',
    opener: {
      chinese: '嗨！这个周末你想做什么？',
      pinyin: 'Hāi! Zhège zhōumò nǐ xiǎng zuò shénme?',
      translationKey: 'aiHelper.openerWeekend',
    },
  },
  {
    key: 'PASAR_MALAM',
    labelKey: 'aiHelper.scenarioPasar',
    opener: {
      chinese: '你好！我们有好吃的炒面和饮料，你想买什么？',
      pinyin: 'Nǐ hǎo! Wǒmen yǒu hǎochī de chǎomiàn hé yǐnliào, nǐ xiǎng mǎi shénme?',
      translationKey: 'aiHelper.openerPasar',
    },
  },
  {
    key: 'CAMPUS_TOUR',
    labelKey: 'aiHelper.scenarioCampus',
    opener: {
      chinese: '欢迎来到我们的大学！你想先去哪儿看看？',
      pinyin: 'Huānyíng lái dào wǒmen de dàxué! Nǐ xiǎng xiān qù nǎr kànkan?',
      translationKey: 'aiHelper.openerCampus',
    },
  },
]

export const QUIZ_TOPIC_KEYS = [
  'aiHelper.topicNumbers',
  'aiHelper.topicFood',
  'aiHelper.topicDirections',
  'aiHelper.topicTime',
  'aiHelper.topicFamily',
  'aiHelper.topicShopping',
] as const

export const STORY_TOPIC_KEYS = [
  'aiHelper.topicLrt',
  'aiHelper.topicMyFamily',
  'aiHelper.topicRainyDay',
  'aiHelper.topicPasarMalam',
  'aiHelper.topicWeekend',
  'aiHelper.topicFirstDay',
] as const

/** The reasons a student can pick for each feature (a score has its own). */
export function reportReasons(feature: AiFeatureKey): readonly AiResponseReportReason[] {
  return feature === 'PRACTICE_CHECK'
    ? ['SCORE_SEEMS_WRONG', 'INCORRECT_CONTENT', 'INAPPROPRIATE_CONTENT', 'OTHER']
    : ['INCORRECT_CONTENT', 'UNCLEAR_EXPLANATION', 'INAPPROPRIATE_CONTENT', 'OTHER']
}

export function reportReasonKey(reason: string): string {
  return AI_REPORT_REASON_KEYS[reason as AiResponseReportReason] ?? 'aiSafety.reasonOther'
}

/** Short feature names shown with a report ("Chat · sent May 16"). */
const REPORT_FEATURE_KEYS: Readonly<Record<string, string>> = {
  CHAT_TUTOR: 'aiHelper.featureChat',
  XIAOMEI_CONVERSATION: 'aiHelper.featureXiaomei',
  QUIZ_GENERATOR: 'aiHelper.featureQuiz',
  STORY_GENERATOR: 'aiHelper.featureStory',
  PRACTICE_CHECK: 'aiHelper.featureCheck',
  LEARNING_PLAN: 'aiHelper.featurePlan',
}

export function reportFeatureKey(feature: string): string {
  return REPORT_FEATURE_KEYS[feature] ?? 'aiHelper.featureChat'
}

export const REPORT_STATUS: Readonly<
  Record<AiResponseReportStatus, { labelKey: string; tone: PillTone; icon: string }>
> = {
  PENDING_REVIEW: { labelKey: 'aiHelper.reportOpen', tone: 'info', icon: 'clock' },
  RESOLVED: { labelKey: 'aiHelper.reportResolved', tone: 'success', icon: 'status' },
  DISMISSED: { labelKey: 'aiHelper.reportDismissed', tone: 'neutral', icon: 'close' },
}

/** A conversation as plain text for a report: "小美: …" and "You: …" lines, the newest last. */
export function transcriptText(turns: readonly XiaomeiTurn[], youLabel: string, limit = 12): string {
  return turns
    .slice(-limit)
    .map((turn) => `${turn.speaker === 'XIAOMEI' ? '小美' : youLabel}: ${turn.text}`)
    .join('\n')
}

/** A line of Chinese with its pinyin and translation as plain text. */
export function lineText(line: AiStoryLine): string {
  return [line.chinese, line.pinyin, line.translation].filter((part) => part?.trim()).join('\n')
}

/** Whole minutes between two times, at least one. */
export function minutesBetween(start: number, end: number): number {
  return Math.max(1, Math.round((end - start) / 60_000))
}

/** What a report is about and how the dialog words it; the student adds the reason and a note. */
export interface AiReportTarget {
  payload: Omit<AiReportPayload, 'reasonCategory' | 'description'>
  titleKey: string
  attachedKey: string
}

/**
 * Brings a new result (a score, a quiz, a story, the conversation summary) into view when it starts above the screen
 * or low on it. A result already in view is left where it is.
 */
export function revealResult(element: Element | null | undefined): void {
  if (!element || typeof window === 'undefined') return
  const top = element.getBoundingClientRect().top
  if (top >= 0 && top <= window.innerHeight * 0.6) return
  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  element.scrollIntoView?.({ block: 'start', behavior: reduceMotion ? 'auto' : 'smooth' })
}
