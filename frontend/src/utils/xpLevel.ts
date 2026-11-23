import type { XpLevelTitleKey } from '@/plugins/xpLevelMessages'

export const DAILY_GOALS = [10, 20, 30] as const

/** XP ranks (Progress, sidebar): every 100 XP is one rank; the titles grow with the rank ("Rank 3 · Regular"). */
const XP_PER_LEVEL = 100

const LEVEL_TITLE_KEYS = [
  'xpLevel.starter',
  'xpLevel.explorer',
  'xpLevel.regular',
  'xpLevel.committed',
  'xpLevel.champion',
] as const satisfies readonly XpLevelTitleKey[]

interface XpLevel {
  /** 1-based level number. */
  readonly level: number
  /** Full i18n key of the level title (global `xpLevel` messages, plugins/xpLevelMessages.ts). */
  readonly titleKey: XpLevelTitleKey
  /** XP earned inside the current level, 0–99. */
  readonly xpIntoLevel: number
}

/** `level` is 1-based; every level above the last title keeps that title. */
export function xpLevelTitleKey(level: number): XpLevelTitleKey {
  const normalized = Number.isFinite(level) ? Math.max(1, Math.floor(level)) : 1
  const index = Math.min(normalized, LEVEL_TITLE_KEYS.length) - 1
  return LEVEL_TITLE_KEYS[index]
}

export function xpLevelFor(totalXp: number): XpLevel {
  const safeXp = Number.isFinite(totalXp) ? Math.max(0, Math.floor(totalXp)) : 0
  const level = Math.floor(safeXp / XP_PER_LEVEL) + 1
  return { level, titleKey: xpLevelTitleKey(level), xpIntoLevel: safeXp % XP_PER_LEVEL }
}
