import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { classesTestRouter } from '@/test/classRoutes'
import type { ActivityOutcome, ActivityPlay } from '@/types/classes'

const api = vi.hoisted(() => ({ activity: vi.fn(), completeActivity: vi.fn() }))

vi.mock('@/api/classes', () => ({
  studentClassesApi: api,
  activitySlug: (type: string) => type.toLowerCase(),
}))

import StudentClassActivityPage from './StudentClassActivityPage.vue'

const play: ActivityPlay = {
  type: 'FLASHCARDS',
  id: 6,
  title: 'Classmate Phrases',
  stage: 'GUIDED',
  number: 1,
  activityCount: 3,
  lessonTitle: 'Meeting Classmates',
  done: false,
  previous: null,
  next: { type: 'TRACING', id: 6, title: 'Trace 名 and 字' },
  cards: [
    { chinese: '你好', pinyin: 'nǐ hǎo', meaning: 'Hello' },
    { chinese: '再见', pinyin: 'zàijiàn', meaning: 'Goodbye' },
  ],
  characters: [],
  words: [],
  meanings: [],
}

const outcome: ActivityOutcome = { done: true, pairs: [], stepsDone: 3, stepsTotal: 4, lessonStatus: 'IN_PROGRESS' }
const PATH = '/student/classes/1/lessons/1/activities/flashcards/6'

async function mountActivity() {
  const router = await classesTestRouter(PATH)
  const wrapper = mount(StudentClassActivityPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return { wrapper, router }
}

function buttonNamed(wrapper: Awaited<ReturnType<typeof mountActivity>>['wrapper'], name: string) {
  return wrapper.findAll('button').find((button) => button.text() === name)!
}

async function reviewEveryCard(wrapper: Awaited<ReturnType<typeof mountActivity>>['wrapper']) {
  await buttonNamed(wrapper, 'Next').trigger('click')
  await wrapper.get('[data-testid="activity-finish"]').trigger('click')
  await flushPromises()
}

describe('StudentClassActivityPage', () => {
  beforeEach(() => {
    api.activity.mockReset()
    api.completeActivity.mockReset()
    api.activity.mockResolvedValue(play)
    api.completeActivity.mockResolvedValue(outcome)
  })

  it('asks for every card first, then shows the done band with Practise again and Next activity', async () => {
    const { wrapper } = await mountActivity()
    expect(wrapper.get('h1').text()).toBe('Classmate Phrases')
    expect(wrapper.text()).toContain('Meeting Classmates · Activity 1 of 3')
    expect(wrapper.text()).toContain('1 of 2 cards seen')
    expect(wrapper.get('[data-testid="activity-finish"]').attributes('disabled')).toBeDefined()

    await reviewEveryCard(wrapper)

    expect(api.completeActivity).toHaveBeenCalledWith('FLASHCARDS', 6)
    const band = wrapper.get('[data-testid="activity-done"]')
    expect(band.text()).toContain('Completed')
    expect(band.text()).toContain('All 2 cards reviewed.')
    expect(wrapper.find('[data-testid="activity-finish"]').exists()).toBe(false)
    expect(
      wrapper
        .findAll('a')
        .find((link) => link.text() === 'Next activity')!
        .attributes('href'),
    ).toBe('/student/classes/1/lessons/1/activities/tracing/6')

    await buttonNamed(wrapper, 'Practise again').trigger('click')
    expect(wrapper.find('[data-testid="activity-done"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Card 1 of 2')
  })

  it('ends the last activity with Finish lesson', async () => {
    api.activity.mockResolvedValue({ ...play, next: null })
    const { wrapper } = await mountActivity()

    await reviewEveryCard(wrapper)

    expect(wrapper.get('[data-testid="finish-lesson"]').attributes('href')).toBe('/student/classes/1/lessons/1')
  })

  it('confirms before leaving unfinished work, and leaves straight away once it is done', async () => {
    const { wrapper, router } = await mountActivity()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    expect(wrapper.text()).toContain('What you did in this activity is not saved until you finish it.')
    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    expect(router.currentRoute.value.name).toBe('student-class-activity')

    await reviewEveryCard(wrapper)
    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('student-class-lesson')
  })

  it('says so when the progress cannot be saved', async () => {
    api.completeActivity.mockRejectedValue(new Error('nope'))
    const { wrapper } = await mountActivity()

    await reviewEveryCard(wrapper)

    expect(wrapper.text()).toContain('Your progress could not be saved.')
    expect(wrapper.find('[data-testid="activity-done"]').exists()).toBe(false)
  })
})
