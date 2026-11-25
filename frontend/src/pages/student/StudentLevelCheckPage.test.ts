import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { mountAtRoute } from '@/test/routes'
import { deferred } from '@/test/helpers'
import { checkDone, checkStep, planSummary } from '@/test/planFixtures'
import type { RetakeResult } from '@/types/plan'

const api = vi.hoisted(() => ({ overview: vi.fn(), retake: vi.fn(), next: vi.fn() }))
const invalidateHintSettings = vi.hoisted(() => vi.fn())

vi.mock('@/api/plan', () => ({ planApi: { overview: api.overview, retake: api.retake } }))
vi.mock('@/api/learn', () => ({ levelCheckApi: { next: api.next } }))
vi.mock('@/composables/useHintSettings', () => ({ invalidateHintSettings }))

import StudentLevelCheckPage from './StudentLevelCheckPage.vue'

function mountPage() {
  return mountAtRoute(StudentLevelCheckPage, '/student/progress/plan/level-check')
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
    setAccessToken(null)
    api.overview.mockReset()
    api.retake.mockReset()
    api.next.mockReset()
    invalidateHintSettings.mockReset()
    api.overview.mockResolvedValue({ plan: planSummary() })
    // A test that leaves in the middle of the check uses only the first answer: the second must not reach the next test.
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
    api.retake.mockResolvedValue({ level: 'BEGINNER_1', changed: false })
    const { wrapper } = await mountPage()

    expect(api.next).toHaveBeenCalledWith('BEGINNER_1', [])
    expect(wrapper.get('.focus-topbar-label').text()).toBe('Level check')
    await answerOnce(wrapper)

    expect(api.retake).toHaveBeenCalledWith([{ questionId: 1, optionId: null }], 'en', null)
    const done = wrapper.get('[data-testid="level-check-done"]')
    expect(done.get('h2').text()).toBe('You’re still at HSK 1 (Basic)')
    expect(done.get('[data-testid="level-check-lead"]').text()).toBe('Your plan stays the same.')
    // No scoring rules, and no way to a new goal: the plan is the same.
    expect(done.text()).not.toMatch(/\d of \d/)
    expect(wrapper.find('[data-testid="retake-new-goal"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="back-to-plan"]').attributes('href')).toBe('/student/progress/plan')
  })

  it('says the target level is reached, offers a new goal, and confirms the level on the plan', async () => {
    api.retake.mockResolvedValue({ level: 'BEGINNER_2', changed: true })
    const { wrapper } = await mountPage()
    await answerOnce(wrapper)

    const done = wrapper.get('[data-testid="level-check-done"]')
    expect(done.get('h2').text()).toBe('You reached HSK 2 (Survival)')
    expect(done.get('[data-testid="level-check-lead"]').text()).toBe('That’s the target level of your plan.')
    const newGoal = wrapper.get('[data-testid="retake-new-goal"]')
    expect(newGoal.text()).toBe('Set a new goal')
    expect(newGoal.attributes('href')).toBe('/student/progress/plan?edit=goal')
    expect(newGoal.classes()).toContain('btn-secondary')
    const back = wrapper.get('[data-testid="back-to-plan"]')
    expect(back.classes()).toContain('btn-primary')
    expect(back.attributes('href')).toBe('/student/progress/plan?updated=level')
  })

  it('says the plan was rebuilt when the level went up but not yet to the target', async () => {
    api.overview.mockResolvedValue({ plan: planSummary({ level: 'NEW_TO_MANDARIN', targetLevel: 'BEGINNER_2' }) })
    api.retake.mockResolvedValue({ level: 'BEGINNER_1', changed: true })
    const { wrapper } = await mountPage()
    await answerOnce(wrapper)

    expect(wrapper.get('[data-testid="level-check-done"] h2').text()).toBe('You reached HSK 1 (Basic)')
    expect(wrapper.get('[data-testid="level-check-lead"]').text()).toBe(
      'We rebuilt your plan from your new level. Your progress is kept.',
    )
    expect(wrapper.find('[data-testid="retake-new-goal"]').exists()).toBe(false)
  })

  it('says where the learner is now when the level went down', async () => {
    api.retake.mockResolvedValue({ level: 'NEW_TO_MANDARIN', changed: true })
    const { wrapper } = await mountPage()
    await answerOnce(wrapper)

    expect(wrapper.get('[data-testid="level-check-done"] h2').text()).toBe('Your level is now Pre-HSK (New)')
    expect(wrapper.find('[data-testid="retake-new-goal"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="back-to-plan"]').attributes('href')).toBe('/student/progress/plan?updated=level')
  })

  it('finishes straight away when no question is published', async () => {
    api.next.mockReset()
    api.next.mockResolvedValue(checkDone())
    api.retake.mockResolvedValue({ level: 'BEGINNER_1', changed: false })

    const { wrapper } = await mountPage()

    expect(api.retake).toHaveBeenCalledWith([], 'en', null)
    expect(wrapper.find('[data-testid="level-check-done"]').exists()).toBe(true)
  })

  it('asks before leaving in the middle of the check: the level stays and the answers are not saved', async () => {
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    const dialog = wrapper.get('[data-testid="confirm-dialog"]')
    expect(dialog.text()).toContain('Leave the level check?')
    expect(dialog.text()).toContain('Your level stays HSK 1 (Basic). Answers from this try aren’t saved.')
    expect(wrapper.get('[data-testid="confirm-dialog-cancel"]').text()).toBe('Keep going')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('student-plan')
    expect(api.retake).not.toHaveBeenCalled()
  })

  it('keeps checking after “Keep going”', async () => {
    const { wrapper, router } = await mountPage()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(router.currentRoute.value.name).toBe('student-level-check')
    expect(wrapper.find('[data-testid="question-stage"]').exists()).toBe(true)
  })

  it('leaves the finished result without asking', async () => {
    api.retake.mockResolvedValue({ level: 'BEGINNER_1', changed: false })
    const { wrapper, router } = await mountPage()
    await answerOnce(wrapper)

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(router.currentRoute.value.name).toBe('student-plan')
  })

  it('waits for the submitted retake before offering to leave', async () => {
    const pending = deferred<RetakeResult>()
    api.retake.mockReturnValueOnce(pending.promise)
    const { wrapper } = await mountPage()
    await answerOnce(wrapper)
    const close = wrapper.get<HTMLButtonElement>('[data-testid="focus-close"]')
    expect(close.element.disabled).toBe(true)
    await close.trigger('click')
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    pending.resolve({ level: 'BEGINNER_1', changed: false })
    await flushPromises()
    expect(close.element.disabled).toBe(false)
  })

  it.each(['success', 'failure'])('keeps the sent answer selected until a pending check ends: %s', async (outcome) => {
    const second = deferred<ReturnType<typeof checkDone>>()
    api.next.mockReset()
    api.next.mockResolvedValueOnce(checkStep()).mockReturnValueOnce(second.promise)
    const { wrapper } = await mountPage()
    await wrapper.get('[data-testid="option-1"]').trigger('click')
    await wrapper.get('[data-testid="check"]').trigger('click')
    await wrapper.get('[data-testid="option-0"]').trigger('click')
    expect(api.next).toHaveBeenLastCalledWith('BEGINNER_1', [{ questionId: 1, optionId: 12 }], null)
    expect(wrapper.get('[data-testid="option-1"]').attributes('aria-pressed')).toBe('true')

    if (outcome === 'success') {
      second.resolve(checkDone())
      await flushPromises()
      expect(wrapper.get('[data-testid="option-1"]').attributes('disabled')).toBeDefined()
    } else {
      second.reject(new Error('offline'))
      await flushPromises()
      await wrapper.get('[data-testid="option-0"]').trigger('click')
      expect(wrapper.get('[data-testid="option-0"]').attributes('aria-pressed')).toBe('true')
      expect(wrapper.get('[data-testid="check"]').attributes('disabled')).toBeUndefined()
    }
  })

  it('retries a failed retake save with the preserved answers without restarting the check', async () => {
    api.retake.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ level: 'BEGINNER_2', changed: true })
    const { wrapper } = await mountPage()
    await answerOnce(wrapper)

    expect(wrapper.text()).toContain('Your result could not be saved')
    await wrapper.get('.state-panel button').trigger('click')
    await flushPromises()

    expect(api.retake).toHaveBeenCalledTimes(2)
    expect(api.retake).toHaveBeenLastCalledWith([{ questionId: 1, optionId: null }], 'en', null)
    expect(invalidateHintSettings).toHaveBeenCalledOnce()
    expect(wrapper.get('[data-testid="level-check-done"] h2').text()).toBe('You reached HSK 2 (Survival)')
  })

  it('shows the no-plan empty state and closes directly without confirming when plan is null', async () => {
    api.overview.mockResolvedValue({ plan: null })
    const { wrapper, router } = await mountPage()

    expect(wrapper.text()).toContain('Build your learning plan first')
    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(router.currentRoute.value.name).toBe('student-plan')
  })

  it('does not invalidate hint settings when a late retake response settles after a session change', async () => {
    const pending = deferred<RetakeResult>()
    api.retake.mockReturnValueOnce(pending.promise)
    const { wrapper } = await mountPage()
    await answerOnce(wrapper)

    setAccessToken('new-session-after-retake')
    wrapper.unmount()
    pending.resolve({ level: 'BEGINNER_2', changed: true })
    await flushPromises()

    expect(invalidateHintSettings).not.toHaveBeenCalled()
  })

  it('offers Start level check again instead of resending expired answers on 400/409 retake failure', async () => {
    api.retake.mockRejectedValueOnce({
      response: { status: 400, data: { message: 'Start the level check again.' } },
    })
    const { wrapper } = await mountPage()
    await answerOnce(wrapper)

    expect(wrapper.get('[data-testid="retake-restart"]').text()).toBe('Start level check again')
    api.next.mockResolvedValueOnce(checkStep())
    await wrapper.get('[data-testid="retake-restart"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="question-stage"]').exists()).toBe(true)
  })
})
