/** XP levels (Progress, sidebar): every 100 XP is one level; level titles grow with the level. */
const XP_PER_LEVEL = 100

const LEVEL_TITLE_KEYS = [
  'xpLevel.starter',
  'xpLevel.explorer',
  'xpLevel.regular',
  'xpLevel.committed',
  'xpLevel.champion',
] as const

type XpLevelTitleKey = (typeof LEVEL_TITLE_KEYS)[number]

interface XpLevel {
  /** 1-based level number. */
  readonly level: number
  /** Full i18n key of the level title (global `xpLevel` bundle). */
  readonly titleKey: XpLevelTitleKey
  /** XP earned inside the current level, 0–99. */
  readonly xpIntoLevel: number
  /** Total XP needed to reach the next level. */
  readonly nextLevelXp: number
}

export function xpLevelTitleKey(level: number): XpLevelTitleKey {
  const index = Math.min(Math.max(1, Math.floor(level)), LEVEL_TITLE_KEYS.length) - 1
  return LEVEL_TITLE_KEYS[index]
}

export function xpLevelFor(totalXp: number): XpLevel {
  const xp = Math.max(0, Math.floor(totalXp) || 0)
  const level = Math.floor(xp / XP_PER_LEVEL) + 1
  return {
    level,
    titleKey: xpLevelTitleKey(level),
    xpIntoLevel: xp % XP_PER_LEVEL,
    nextLevelXp: level * XP_PER_LEVEL,
  }
}

export const xpLevelMessages = {
  en: {
    starter: 'Starter',
    explorer: 'Explorer',
    regular: 'Regular Learner',
    committed: 'Committed Learner',
    champion: 'Spark Champion',
  },
  ms: {
    starter: 'Permulaan',
    explorer: 'Penjelajah',
    regular: 'Pelajar Tetap',
    committed: 'Pelajar Komited',
    champion: 'Juara Spark',
  },
  'zh-CN': {
    starter: '新手',
    explorer: '探索者',
    regular: '常规学习者',
    committed: '坚持学习者',
    champion: '星火冠军',
  },
} as const
