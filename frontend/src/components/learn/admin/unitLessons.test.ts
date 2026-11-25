import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'

import { adminLearningPathMessages } from '@/plugins/adminLearningPathMessages'
import { adminLearningSharedMessages } from '@/plugins/adminLearningSharedMessages'
import { i18n } from '@/plugins/i18n'
import type { AdminUnitRow } from '@/types/learn'

import { unitDrawerMeta, unitLessonsLabel, unitRowMeta } from './unitLessons'

function translatorFor(locale: 'en' | 'ms' | 'zh-CN') {
  const translator = createI18n({
    legacy: false,
    locale,
    messages: {
      [locale]: {
        adminLearning: { ...adminLearningSharedMessages[locale], ...adminLearningPathMessages[locale] },
        hsk: i18n.global.messages.value[locale].hsk,
      },
    },
  })
  return translator.global.t
}

const greetings: AdminUnitRow = {
  id: 1,
  number: 1,
  title: 'Greetings',
  titleZh: '问候',
  hskLevel: 1,
  lessonCount: 7,
  bonusCount: 1,
  studentsStarted: 8,
  status: 'PUBLISHED',
}

/** The server counts a unit's lessons without its bonus chests (as the learner's path does) and the chests apart. */
describe('unitLessonsLabel', () => {
  it.each([
    ['en', { lessonCount: 7, bonusCount: 0 }, '7 lessons'],
    ['en', { lessonCount: 7, bonusCount: 1 }, '7 lessons + bonus chest'],
    ['en', { lessonCount: 1, bonusCount: 1 }, '1 lesson + bonus chest'],
    ['en', { lessonCount: 1, bonusCount: 0 }, '1 lesson'],
    ['en', { lessonCount: 0, bonusCount: 0 }, '0 lessons'],
    ['en', { lessonCount: 4, bonusCount: 2 }, '4 lessons + 2 bonus chests'],
    ['ms', { lessonCount: 7, bonusCount: 0 }, '7 pelajaran'],
    ['ms', { lessonCount: 7, bonusCount: 1 }, '7 pelajaran + peti bonus'],
    ['ms', { lessonCount: 1, bonusCount: 0 }, '1 pelajaran'],
    ['ms', { lessonCount: 4, bonusCount: 2 }, '4 pelajaran + 2 peti bonus'],
    ['zh-CN', { lessonCount: 7, bonusCount: 0 }, '7 个课时'],
    ['zh-CN', { lessonCount: 7, bonusCount: 1 }, '7 个课时 + 奖励宝箱'],
    ['zh-CN', { lessonCount: 1, bonusCount: 0 }, '1 个课时'],
    ['zh-CN', { lessonCount: 4, bonusCount: 2 }, '4 个课时 + 2 个奖励宝箱'],
  ] as const)('%s: %o reads "%s"', (locale, unit, label) => {
    expect(unitLessonsLabel(translatorFor(locale), unit)).toBe(label)
  })
})

/** A unit row's line (A4.01): its lessons, then the learners who started it; nobody can start a draft. */
describe('unitRowMeta', () => {
  it.each([
    ['en', greetings, '7 lessons + bonus chest · 8 learners started'],
    ['en', { ...greetings, studentsStarted: 1 }, '7 lessons + bonus chest · 1 learner started'],
    ['en', { ...greetings, studentsStarted: 1200 }, '7 lessons + bonus chest · 1,200 learners started'],
    ['en', { ...greetings, status: 'DRAFT' }, '7 lessons + bonus chest'],
    ['ms', greetings, '7 pelajaran + peti bonus · 8 pelajar telah bermula'],
    ['zh-CN', greetings, '7 个课时 + 奖励宝箱 · 8 名学习者已开始'],
  ] as const)('%s: %o reads "%s"', (locale, unit, meta) => {
    expect(unitRowMeta(translatorFor(locale), unit)).toBe(meta)
  })
})

/** The unit drawer's line under its title (A4.02): its level, its learners and its status. */
describe('unitDrawerMeta', () => {
  it.each([
    ['en', greetings, 'HSK 1 · 8 learners started · Published'],
    ['en', { ...greetings, hskLevel: 2, status: 'DRAFT' }, 'HSK 2 · Draft'],
    ['zh-CN', greetings, 'HSK 1 · 8 名学习者已开始 · 已发布'],
  ] as const)('%s: %o reads "%s"', (locale, unit, meta) => {
    expect(unitDrawerMeta(translatorFor(locale), unit)).toBe(meta)
  })
})
