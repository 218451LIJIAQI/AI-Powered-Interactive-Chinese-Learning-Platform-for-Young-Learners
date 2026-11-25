import {
  AI_REPORT_SNAPSHOT_LIMIT,
  type AiStoryLine,
  type XiaomeiScenario,
  type XiaomeiSummary,
  type XiaomeiTurn,
} from '@/types/ai'
import { truncateText } from '@/utils/truncateText'

import type { AiReportTarget } from './aiHelper'

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

export interface TalkEntry extends AiStoryLine {
  id: number
  speaker: XiaomeiTurn['speaker']
  /** False for the scenario's scripted opener. */
  ai: boolean
  via?: 'voice' | 'typed'
  natural?: string | null
  tip?: string | null
  reported?: boolean
}

export type TalkReportTarget = AiReportTarget & {
  /** Identifies the specific reply entry or summary being reported so late submits never mark another item. */
  targetId: number | 'summary'
}

const MAX_TURNS = 24
const MAX_TURN_TEXT = 300

export function xiaomeiTurns(list: readonly TalkEntry[]): XiaomeiTurn[] {
  return list
    .slice(-MAX_TURNS)
    .map((entry) => ({ speaker: entry.speaker, text: truncateText(entry.chinese, MAX_TURN_TEXT) }))
}

/** Keep the latest full turns that fit so the context still ends immediately before the reported answer. */
function buildReportHistory(history: readonly TalkEntry[], youLabel: string): string {
  let snapshot = ''
  for (let index = history.length - 1; index >= 0; index--) {
    const entry = history[index]!
    const line = `${entry.speaker === 'XIAOMEI' ? '小美' : youLabel}: ${entry.chinese}`
    const next = snapshot ? `${line}\n${snapshot}` : line
    if (next.length > AI_REPORT_SNAPSHOT_LIMIT) return snapshot || truncateText(line, AI_REPORT_SNAPSHOT_LIMIT)
    snapshot = next
  }
  return snapshot
}

export function buildLineReportTarget(
  entries: readonly TalkEntry[],
  entry: TalkEntry,
  youLabel: string,
): TalkReportTarget {
  const index = entries.indexOf(entry)
  const slice = index >= 0 ? entries.slice(Math.max(0, index - 12), index) : entries.slice(-12)
  const prior = slice.at(-1)?.speaker === 'STUDENT' ? slice.at(-1) : undefined
  return {
    targetId: entry.id,
    payload: {
      featureKey: 'XIAOMEI_CONVERSATION',
      promptSnapshot: buildReportHistory(slice, youLabel),
      aiResponseSnapshot: truncateText(
        [entry.chinese, entry.pinyin, entry.translation, prior?.natural, prior?.tip]
          .filter((part) => part?.trim())
          .join('\n'),
        AI_REPORT_SNAPSHOT_LIMIT,
      ),
    },
    titleKey: 'aiHelper.reportReplyTitle',
    attachedKey: 'aiHelper.reportReplyAttached',
  }
}

/** "Say it more naturally": the learner's last two lines that have a more natural version. */
export function summaryNaturalTips(entries: readonly TalkEntry[]): TalkEntry[] {
  return entries.filter((item) => item.speaker === 'STUDENT' && item.natural).slice(-2)
}

export function buildSummaryReportTarget(
  entries: readonly TalkEntry[],
  written: XiaomeiSummary,
  youLabel: string,
): TalkReportTarget {
  const lines = (items: readonly AiStoryLine[]) =>
    items.map((line) => `${line.chinese} ${line.translation ?? ''}`.trim())
  const naturalLines = summaryNaturalTips(entries).flatMap((item) => [item.natural, item.tip])
  return {
    targetId: 'summary',
    payload: {
      featureKey: 'XIAOMEI_CONVERSATION',
      promptSnapshot: buildReportHistory(entries.slice(-MAX_TURNS), youLabel),
      aiResponseSnapshot: truncateText(
        [
          written.goodbye?.chinese,
          written.feedback,
          ...lines(written.newExpressions),
          ...naturalLines,
          ...lines(written.wordsUsed),
        ]
          .filter((part) => part?.trim())
          .join('\n'),
        AI_REPORT_SNAPSHOT_LIMIT,
      ),
    },
    titleKey: 'aiHelper.reportAiTitle',
    attachedKey: 'aiHelper.reportAiAttached',
  }
}
