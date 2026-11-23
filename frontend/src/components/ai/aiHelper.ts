/**
 * Shared data of the AI Helper: Xiaomei's scenarios, the labels of a report, what a report is about and attaches, and
 * how a new result comes into view. Every map holds FULL i18n keys of the `aiHelper` bundles
 * (`aiHelperMessages.ts`, `aiTalkMessages.ts`) or the global `aiSafety` bundle.
 */
import type { PillTone } from '@/components/common/StatusPill.vue'
import { AI_REPORT_REASON_KEYS } from '@/plugins/aiSafetyMessages'
import type { AiResponseReportReason, AiResponseReportStatus } from '@/types/api'
import type { AiReportPayload, PracticeCheck, PracticeCheckType, XiaomeiScenario } from '@/types/ai'

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

export function reportReasonKey(reason: AiResponseReportReason): string {
  return AI_REPORT_REASON_KEYS[reason] ?? AI_REPORT_REASON_KEYS.OTHER
}

export function practiceCheckKindKey(kind: PracticeCheckType): 'aiHelper.recording' | 'aiHelper.handwriting' {
  return kind === 'RECORDING' ? 'aiHelper.recording' : 'aiHelper.handwriting'
}

export function formatPracticeCheckMeta(check: PracticeCheck, when: string, fileName?: string | null): string {
  return [check.lessonTitle, fileName, when].filter(Boolean).join(' · ')
}

export const REPORT_STATUS: Readonly<Record<AiResponseReportStatus, { labelKey: string; tone: PillTone }>> = {
  PENDING_REVIEW: { labelKey: 'aiSafety.reportOpen', tone: 'info' },
  RESOLVED: { labelKey: 'aiSafety.reportResolved', tone: 'success' },
  DISMISSED: { labelKey: 'aiSafety.reportDismissed', tone: 'neutral' },
}

/** What a report is about and how the dialog words it; the student adds the reason and a note. */
export interface AiReportTarget {
  payload: Omit<AiReportPayload, 'reasonCategory' | 'description'>
  titleKey: string
  attachedKey: string
  /** Where the admin's reply appears, when not the default "Ask SparkBot › My reports". */
  leadKey?: string
}

/**
 * Brings a new result (a score, a quiz, a story, the conversation summary) into view when it starts above the screen
 * or low on it. A result already in view is left where it is.
 */
export function revealResult(element: Element | null | undefined): void {
  if (!element || !element.isConnected) return
  const top = element.getBoundingClientRect().top
  const minTop = Number.parseFloat(getComputedStyle(element).scrollMarginTop) || 0
  if (top >= minTop && top <= window.innerHeight * 0.6) return
  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  element.scrollIntoView({ block: 'start', behavior: reduceMotion ? 'auto' : 'smooth' })
}
