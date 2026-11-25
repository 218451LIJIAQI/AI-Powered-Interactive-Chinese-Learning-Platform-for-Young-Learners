import { describe, expect, it } from 'vitest'

import { installAxiosCapture, restoreAxiosAdapterAfterEach } from '@/test/axiosCapture'
import type { GoalInput } from '@/types/plan'

import { planApi } from './plan'

restoreAxiosAdapterAfterEach()

const GOAL: GoalInput = {
  reason: 'EVERYDAY',
  reasonText: null,
  goals: ['ORDER_FOOD'],
  goalText: null,
  deadline: '2026-09-01',
  pace: 'MINUTES_30_TO_60',
  focus: ['SPEAKING'],
}

describe('learning plan API wrappers', () => {
  it('maps every plan read, confirmation, pick and skip route', async () => {
    const calls = installAxiosCapture()

    await planApi.home()
    await planApi.options()
    await planApi.plan()
    await planApi.overview()
    await planApi.confirm()
    await planApi.pickClass(7)
    await planApi.pickClass(null)
    await planApi.skip()

    expect(calls).toHaveLength(8)
    expect(calls).toMatchObject([
      { method: 'GET', url: '/students/home', params: undefined, data: undefined },
      { method: 'GET', url: '/students/plan/options', params: undefined, data: undefined },
      { method: 'GET', url: '/students/plan', params: undefined, data: undefined },
      { method: 'GET', url: '/students/plan/overview', params: undefined, data: undefined },
      { method: 'POST', url: '/students/plan/confirm', params: undefined, data: undefined },
      { method: 'PUT', url: '/students/plan/class-pick', params: undefined, data: { classId: 7 } },
      { method: 'PUT', url: '/students/plan/class-pick', params: undefined, data: { classId: null } },
      { method: 'POST', url: '/students/plan/skip', params: undefined, data: undefined },
    ])
  })

  it('sends plan build, goal update and level-check retake requests with the 60-second AI build timeout', async () => {
    const calls = installAxiosCapture()

    await planApi.build({
      goal: GOAL,
      levelCheck: {
        selfLevel: 'NEW_TO_MANDARIN',
        skipped: false,
        answers: [{ questionId: 1, optionId: 10 }],
      },
      language: 'en',
    })
    await planApi.updateGoal(GOAL, 'ms')
    await planApi.retake([{ questionId: 2, optionId: 20 }], 'zh-CN')

    expect(calls).toHaveLength(3)
    expect(calls).toMatchObject([
      {
        method: 'POST',
        url: '/students/plan',
        data: {
          goal: GOAL,
          levelCheck: {
            selfLevel: 'NEW_TO_MANDARIN',
            skipped: false,
            answers: [{ questionId: 1, optionId: 10 }],
          },
          language: 'en',
        },
        timeout: 60_000,
      },
      {
        method: 'PUT',
        url: '/students/plan/goal',
        data: { goal: GOAL, language: 'ms' },
        timeout: 60_000,
      },
      {
        method: 'POST',
        url: '/students/plan/level-check',
        data: { answers: [{ questionId: 2, optionId: 20 }], language: 'zh-CN' },
        timeout: 60_000,
      },
    ])
  })
})
