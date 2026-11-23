/**
 * Titles of the XP ranks (Progress, sidebar): "Rank 3 · Regular". i18n.ts mounts them globally under `xpLevel`;
 * utils/xpLevel returns the full keys.
 */
export const xpLevelMessages = {
  en: {
    starter: 'Starter',
    explorer: 'Explorer',
    regular: 'Regular',
    committed: 'Committed',
    champion: 'Spark Champion',
  },
  ms: {
    starter: 'Permulaan',
    explorer: 'Penjelajah',
    regular: 'Tetap',
    committed: 'Komited',
    champion: 'Juara Spark',
  },
  'zh-CN': {
    starter: '新手',
    explorer: '探索者',
    regular: '常客',
    committed: '坚持者',
    champion: '星火冠军',
  },
} as const

export type XpLevelKey = keyof (typeof xpLevelMessages)['en']
export type XpLevelTitleKey = `xpLevel.${XpLevelKey}`
