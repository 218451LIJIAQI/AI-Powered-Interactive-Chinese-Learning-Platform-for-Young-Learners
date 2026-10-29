import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { checkDone, checkStep, planRouter, planSummary } from '@/test/planFixtures'

const api = vi.hoisted(() => ({ overview: vi.fn(), retake: vi.fn(), next: vi.fn() }))

vi.mock('@/api/plan', () => ({ planApi: { overview: api.overview, retake: api.retake } }))
vi.mock('@/api/learn', () => ({ levelCheckApi: { next: api.next } }))

import StudentLevelCheckPage from './StudentLevelCheckPage.vue'

async function mountPage() {
  const router = await planRouter('/student/progress/plan/level-check')
  const wrapper = mount(StudentLevelCheckPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return { wrapper, router }
}

async function answerOnce(wrapper: Awaited<ReturnType<typeof mountPage>>['wrapper']): Promise<void> {
  await wrapper.get('[data-testid="skip"]').trigger('click')
  await flushPromises()
  expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('No problem!')
  await wrapper.get('[data-testid="continue"]').trigger('click')
  await flushPromises()
}

describe('StudentLevelCheckPage', () => {
  beforeEach(() => {
    api.overview.mockResolvedValue({ setupState: 'COMPLETED', plan: planSummary() })
    api.next.mockResolvedValueOnce(checkStep()).mockResolvedValueOnce(
      checkDone({
        feedback: {
          correct: false,
          skipped: true,
          correctAnswer: 'Hello',
          explanation: null,
        },
      }),
    )
  })

  it('starts at the plan’s level and says the plan stays when the level is the same', async () => {
    api.retake.mockResolvedValue({
      level: 'BEGINNER_1',
      headStart: true,
      knownSkills: ['VOCABULARY'],
      newSkills: ['LISTENING'],
      answered: 3,
      changed: false,
    })
    const { wrapper } = await mountPage()

    expect(api.next).toHaveBeenCalledWith('BEGINNER_1', [])
    await answerOnce(wrapper)

    expect(api.retake).toHaveBeenCalledWith('BEGINNER_1', [{ questionId: 1, optionId: null }], 'en')
    expect(wrapper.get('[data-testid="retake-outcome"]').text()).toBe(
      'Your level is still Beginner 1, with a head start, so your plan stays the same.',
    )
    expect(wrapper.get('[data-testid="back-to-plan"]').attributes('href')).toBe('/student/progress/plan')
  })

  it('says the plan was rebuilt when the level changed', async () => {
    api.retake.mockResolvedValue({
      level: 'BEGINNER_2',
      headStart: true,
      knownSkills: [],
      newSkills: [],
      answered: 3,
      changed: true,
    })
    const { wrapper } = await mountPage()
    await answerOnce(wrapper)

    expect(wrapper.get('[data-testid="retake-outcome"]').text()).toContain('Your level is now Beginner 2.')
  })

  it('asks before leaving in the middle of the check', async () => {
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    expect(wrapper.get('[data-testid="confirm-dialog"]').text()).toContain('Leave the level check?')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('student-plan')
    expect(api.retake).not.toHaveBeenCalled()
  })
})
