import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { answerCheck, learnRouter, lessonPlay, lessonResult } from '@/test/learnFixtures'

const api = vi.hoisted(() => ({
  lesson: vi.fn(),
  checkLesson: vi.fn(),
  completeLesson: vi.fn(),
}))

vi.mock('@/api/learn', () => ({ learnApi: api }))

import StudentLessonPlayerPage from './StudentLessonPlayerPage.vue'

async function mountPage() {
  const router = await learnRouter('/student/learn/lessons/13')
  const push = vi.spyOn(router, 'push')
  const wrapper = mount(StudentLessonPlayerPage, {
    attachTo: document.body,
    global: { plugins: [router], stubs: { teleport: true } },
  })
  await flushPromises()
  return { wrapper, push }
}

describe('StudentLessonPlayerPage (focus mode lesson)', () => {
  beforeEach(() => {
    api.lesson.mockResolvedValue(lessonPlay())
  })

  it('checks each answer on the server, shows the verdict and finishes with the result screen', async () => {
    api.checkLesson.mockResolvedValueOnce(answerCheck()).mockResolvedValueOnce(
      answerCheck({
        correct: false,
        correctAnswer: '我叫Amira',
        pinyin: 'Wǒ jiào Amira',
        meaning: 'My name is Amira.',
      }),
    )
    api.completeLesson.mockResolvedValue(lessonResult())
    const { wrapper } = await mountPage()

    expect(api.lesson).toHaveBeenCalledWith(13)
    expect(wrapper.get('[data-testid="check"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Question 1 of 2')
    // A two-character word sits on two 田字格 tiles, its pinyin under them.
    expect(wrapper.findAll('[data-testid="question-stage"] .hanzi-tile').map((tile) => tile.text())).toEqual([
      '名',
      '字',
    ])

    await wrapper.get('[data-testid="option-0"]').trigger('click')
    await wrapper.get('[data-testid="check"]').trigger('click')
    await flushPromises()
    expect(api.checkLesson).toHaveBeenLastCalledWith(13, { questionId: 101, answer: 'name' })
    expect(wrapper.get('[data-testid="check-bar"]').classes()).toContain('is-correct')
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Nice!')

    await wrapper.get('[data-testid="continue"]').trigger('click')
    // Second question: build "我叫Amira" from tiles, but only place two of them.
    await wrapper.get('[data-testid="tile-1"]').trigger('click')
    await wrapper.get('[data-testid="tile-0"]').trigger('click')
    expect(wrapper.get('[data-testid="build-answer"]').text()).toContain('我')
    await wrapper.get('[data-testid="check"]').trigger('click')
    await flushPromises()
    expect(api.checkLesson).toHaveBeenLastCalledWith(13, { questionId: 102, answer: '我 叫' })
    const bar = wrapper.get('[data-testid="check-bar"]')
    expect(bar.classes()).toContain('is-wrong')
    expect(bar.text()).toContain('Correct answer:')
    expect(bar.text()).toContain('Wǒ jiào Amira')
    expect(wrapper.get('[data-testid="continue"]').text()).toBe('See my result')

    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()
    expect(api.completeLesson).toHaveBeenCalledWith(
      13,
      [
        { questionId: 101, answer: 'name' },
        { questionId: 102, answer: '我 叫' },
      ],
      expect.any(Number),
    )
    const done = wrapper.get('[data-testid="lesson-complete"]')
    expect(done.text()).toContain('Lesson complete!')
    expect(done.text()).toContain('+10')
    expect(done.text()).toContain('Next up: Fruit')
    expect(done.text()).toContain('New badge: Unit Champion')
    expect(done.get('.learn-stamp-seal').text()).toBe('棒')
    expect(done.findAll('dt').map((term) => term.text())).toEqual(['XP earned', 'accuracy', 'time'])
    wrapper.unmount()
  })

  it('sends a skipped question as a null answer', async () => {
    api.checkLesson.mockResolvedValue(answerCheck({ correct: false }))
    const { wrapper } = await mountPage()

    await wrapper.get('[data-testid="skip"]').trigger('click')
    await flushPromises()

    expect(api.checkLesson).toHaveBeenCalledWith(13, { questionId: 101, answer: null })
    expect(wrapper.get('[data-testid="check-bar"]').classes()).toContain('is-wrong')
    wrapper.unmount()
  })

  it('asks before leaving a lesson in progress: Keep going stays, Leave goes back to the path', async () => {
    api.checkLesson.mockResolvedValue(answerCheck())
    const { wrapper, push } = await mountPage()
    await wrapper.get('[data-testid="option-0"]').trigger('click')
    await wrapper.get('[data-testid="check"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    const dialog = wrapper.get('[data-testid="confirm-dialog"]')
    expect(dialog.text()).toContain('Leave this lesson?')
    expect(wrapper.get('[data-testid="confirm-dialog-confirm"]').classes()).toContain('btn-outline')
    expect(wrapper.get('[data-testid="confirm-dialog-cancel"]').classes()).toContain('btn-primary')

    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(push).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    expect(push).toHaveBeenCalledWith({ name: 'student-learn' })
    wrapper.unmount()
  })

  it('shows why a locked lesson cannot be opened', async () => {
    api.lesson.mockRejectedValue({
      response: { status: 409, data: { message: 'This lesson is still locked. Finish the lessons before it first.' } },
    })
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('This lesson is still locked. Finish the lessons before it first.')
    expect(wrapper.find('[data-testid="question-stage"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
