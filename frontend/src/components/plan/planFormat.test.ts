import { describe, expect, it } from 'vitest'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { i18n } from '@/plugins/i18n'
import { planPathMessages } from '@/plugins/planPathMessages'
import { planSetupMessages } from '@/plugins/planSetupMessages'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import { withSetup } from '@/test/helpers'
import { planOptions } from '@/test/planFixtures'
import type { PlanPace, PlanPaceBand } from '@/types/plan'

import { levelSpan, paceDaily, studyFormula, unitsWord } from './planFormat'

function planTranslator() {
  const { result } = withSetup(() =>
    useLocalMessages({ plan: [planSummaryMessages, planSetupMessages, planPathMessages] }),
  )
  return result.t
}

function band(key: PlanPace): PlanPaceBand {
  return planOptions().paces.find((pace) => pace.key === key)!
}

describe('studyFormula', () => {
  it('joins the date and the daily time into the hours of Chinese they make', () => {
    const t = planTranslator()
    // The setup's sentence: its date field already shows the date and the days to go (S1.18).
    expect(studyFormula(t, 61, band('HOURS_1_TO_2'), true)).toBe('1–2 hours a day adds up to 61–122 hours of Chinese.')
    expect(studyFormula(t, 61, band('UNDER_30_MIN'), true)).toBe(
      'Under 30 min a day adds up to under 31 hours of Chinese.',
    )
    expect(studyFormula(t, 61, band('MINUTES_30_TO_60'))).toBe('61 days × 30–60 min a day = 31–61 hours of Chinese')
    expect(studyFormula(t, 184, band('MINUTES_30_TO_60'))).toBe('184 days × 30–60 min a day = 92–184 hours of Chinese')
  })

  it('writes the open bands as an upper or a lower bound', () => {
    const t = planTranslator()
    expect(studyFormula(t, 61, band('UNDER_30_MIN'))).toBe('61 days × under 30 min a day = under 31 hours of Chinese')
    expect(studyFormula(t, 61, band('HOURS_2_PLUS'))).toBe('61 days × 2+ hours a day = 122+ hours of Chinese')
  })

  it('reads in the interface language', () => {
    i18n.global.locale.value = 'zh-CN'
    const t = planTranslator()
    expect(studyFormula(t, 61, band('HOURS_1_TO_2'), true)).toBe('每天 1–2 小时，累计 61–122 小时中文学习。')
    expect(studyFormula(t, 61, band('HOURS_1_TO_2'))).toBe('61 天 × 每天 1–2 小时 = 61–122 小时中文学习')
  })
})

describe('plan labels', () => {
  it('names the daily time and a level span by its codes', () => {
    const t = planTranslator()
    expect(paceDaily(t, band('HOURS_1_TO_2'))).toBe('1–2 hours a day')
    expect(levelSpan(t, 'BEGINNER_1', 'BEGINNER_2')).toBe('HSK 1 → HSK 2')
    expect(levelSpan(t, 'BEGINNER_2', 'BEGINNER_2')).toBe('HSK 2')
    expect(unitsWord(t, [2], 'en')).toBe('Unit 2')
    expect(unitsWord(t, [2, 3, 4], 'en')).toBe('Units 2–4')
    expect(unitsWord(t, [1, 3, 4], 'en')).toBe('Unit 1 and Units 3–4')
  })
})
