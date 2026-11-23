import { AI_REPORT_SNAPSHOT_LIMIT, type XiaomeiSummary, type XiaomeiTurn } from '@/types/ai'
import type { AiStoryLine } from '@/types/api'
import { truncateText } from '@/utils/truncateText'

import type { AiReportTarget } from './aiHelper'

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

export const MAX_TURNS = 24
export const MAX_TURN_TEXT = 300

export function xiaomeiTurns(list: readonly TalkEntry[]): XiaomeiTurn[] {
  return list
    .slice(-MAX_TURNS)
    .map((entry) => ({ speaker: entry.speaker, text: truncateText(entry.chinese, MAX_TURN_TEXT) }))
}

/** Keep the latest full turns that fit so the context still ends immediately before the reported answer. */
export function buildReportHistory(history: readonly TalkEntry[], youLabel: string): string {
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
  return {
    targetId: entry.id,
    payload: {
      featureKey: 'XIAOMEI_CONVERSATION',
      promptSnapshot: buildReportHistory(slice, youLabel),
      aiResponseSnapshot: [entry.chinese, entry.pinyin, entry.translation].filter((part) => part?.trim()).join('\n'),
    },
    titleKey: 'aiHelper.reportReplyTitle',
    attachedKey: 'aiHelper.reportReplyAttached',
  }
}

export function buildSummaryReportTarget(
  entries: readonly TalkEntry[],
  written: XiaomeiSummary,
  youLabel: string,
): TalkReportTarget {
  const lines = (items: readonly AiStoryLine[]) =>
    items.map((line) => `${line.chinese} ${line.translation ?? ''}`.trim())
  return {
    targetId: 'summary',
    payload: {
      featureKey: 'XIAOMEI_CONVERSATION',
      promptSnapshot: buildReportHistory(entries.slice(-MAX_TURNS), youLabel),
      aiResponseSnapshot: [
        written.goodbye?.chinese,
        written.feedback,
        ...lines(written.newExpressions),
        ...lines(written.wordsUsed),
      ]
        .filter((part) => part?.trim())
        .join('\n'),
    },
    titleKey: 'aiHelper.reportAiTitle',
    attachedKey: 'aiHelper.reportAiAttached',
  }
}
