import type { Translate } from '@/plugins/i18n'

const NAMED_LEVELS: Readonly<Record<number, string>> = {
  1: 'hsk.level1',
  2: 'hsk.level2',
  3: 'hsk.level3',
}

/**
 * The name of an HSK level that learning content is tagged with (the Learn path, Practice sets): "HSK 1 (Basic)" to
 * "HSK 3 (Conversational)", then just the number ("HSK 4", "HSK 5"), as each higher level is tagged on its own.
 */
export function hskLevelName(t: Translate, level: number): string {
  const key = NAMED_LEVELS[level]
  return key ? t(key) : t('hsk.number', { level })
}
