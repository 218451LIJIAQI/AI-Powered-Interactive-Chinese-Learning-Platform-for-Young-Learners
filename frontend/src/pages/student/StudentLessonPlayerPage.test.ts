import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { answerCheck, learnRouter, lessonPlay, lessonResult, today } from '@/test/learnFixtures'

const api = vi.hoisted(() => ({
  lesson: vi.fn(),
  checkLesson: vi.fn(),
  completeLesson: vi.fn(),
  today: vi.fn(),
}))

vi.mock('@/api/learn', () => ({
  learnApi: { lesson: api.lesson, checkLesson: api.checkLesson, completeLesson: api.completeLesson },
  xpApi: { today: api.today },
}))

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
    api.today.mockResolvedValue(today())
  })

  it('checks each answer on the server, shows the verdict, says a phrase and finishes with the result screen', async () => {
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
    const bar = () => wrapper.get('[data-testid="check-bar"]')
    expect(bar().element.tagName).toBe('FOOTER')
    expect(bar().classes()).toContain('focus-footer')
    expect(wrapper.get('[data-testid="check"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="check"]').classes()).toEqual(expect.arrayContaining(['btn-primary', 'btn-lg']))
    expect(wrapper.get('[data-testid="skip"]').classes()).toEqual(expect.arrayContaining(['btn-secondary', 'btn-lg']))
    // The word is set in the hanzi serif with its pinyin, not on a tile; the answers are option rows with keycaps.
    const stage = wrapper.get('[data-testid="question-stage"]')
    expect(stage.find('.hanzi-tile').exists()).toBe(false)
    expect(stage.get('.learn-question-hanzi').text()).toBe('名字')
    expect(stage.get('.learn-question-pinyin').text()).toBe('míngzi')
    expect(wrapper.get('[data-testid="option-0"]').classes()).toContain('option-row')
    expect(wrapper.get('[data-testid="option-0"] .option-keycap').text()).toBe('1')

    await wrapper.get('[data-testid="option-0"]').trigger('click')
    expect(wrapper.get('[data-testid="option-0"]').attributes('aria-pressed')).toBe('true')
    await wrapper.get('[data-testid="check"]').trigger('click')
    await flushPromises()
    expect(api.checkLesson).toHaveBeenLastCalledWith(13, { questionId: 101, answer: 'name' })
    expect(bar().classes()).toContain('is-correct')
    expect(bar().text()).toContain('Nice!')
    expect(wrapper.get('[data-testid="option-0"]').classes()).toContain('is-correct')

    await wrapper.get('[data-testid="continue"]').trigger('click')
    // Second question: build "我叫Amira" from tiles, but only place two of them.
    await wrapper.get('[data-testid="tile-1"]').trigger('click')
    await wrapper.get('[data-testid="tile-0"]').trigger('click')
    expect(wrapper.get('[data-testid="build-answer"]').text()).toContain('我')
    expect(wrapper.get('[data-testid="tile-1"]').classes()).toContain('is-used')
    await wrapper.get('[data-testid="check"]').trigger('click')
    await flushPromises()
    expect(api.checkLesson).toHaveBeenLastCalledWith(13, { questionId: 102, answer: '我 叫' })
    expect(bar().classes()).toContain('is-wrong')
    expect(bar().text()).toContain('Correct answer:')
    expect(bar().text()).toContain('Wǒ jiào Amira')
    expect(wrapper.get('[data-testid="continue"]').text()).toBe('Continue')

    // Not graded and not sent: a phrase from the lesson to say aloud.
    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()
    const say = wrapper.get('[data-testid="say-step"]')
    expect(say.text()).toContain('Listen, then say it')
    expect(say.text()).toContain('我叫Amira')
    expect(say.text()).toContain('Wǒ jiào Amira · My name is Amira.')
    expect(api.completeLesson).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="say-done"]').trigger('click')
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
    expect(done.get('.learn-stamp-caption').text()).toBe('bàng · great job!')
    expect(done.findAll('dt').map((term) => term.text())).toEqual(['XP earned', 'accuracy', 'time'])
    expect(done.findAll('.learn-done-week > span')).toHaveLength(7)
    expect(done.findAll('.learn-done-week > .is-reached')).toHaveLength(6)
    // One wrong answer: See mistakes on the left, Continue on the right.
    expect(wrapper.get('.focus-footer-start a').text()).toBe('See mistakes')
    expect(wrapper.get('.focus-footer-end [data-testid="lesson-continue"]').classes()).toContain('btn-primary')
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

  it('goes straight to the result of a checkpoint: a test has no step to say a phrase', async () => {
    api.lesson.mockResolvedValue(
      lessonPlay({ kind: 'CHECKPOINT', passPercent: 70, questions: lessonPlay().questions.slice(0, 1) }),
    )
    api.checkLesson.mockResolvedValue(answerCheck({ correct: false }))
    api.completeLesson.mockResolvedValue(
      lessonResult({ kind: 'CHECKPOINT', passed: false, passPercent: 70, accuracy: 0, nextLesson: null }),
    )
    const { wrapper } = await mountPage()

    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('Answer 70% or more right to pass.')
    await wrapper.get('[data-testid="skip"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="continue"]').text()).toBe('See my result')

    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="say-step"]').exists()).toBe(false)
    const done = wrapper.get('[data-testid="lesson-complete"]')
    expect(done.text()).toContain('Not passed yet')
    expect(done.text()).toContain('You need 70% to pass and got 0%.')
    // Not passed: Try again is the main action, Continue steps back beside it.
    const end = wrapper.findAll('.focus-footer-end button')
    expect(end.map((button) => button.text())).toEqual(['Continue', 'Try again'])
    expect(end[0]!.classes()).toContain('btn-secondary')
    expect(end[1]!.classes()).toContain('btn-primary')
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
    expect(wrapper.get('[data-testid="confirm-dialog-cancel"]').classes()).toContain('btn-primary')

    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(push).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    expect(push).toHaveBeenCalledWith({ name: 'student-learn' })
    wrapper.unmount()
  })

  it('leaves without asking when nothing is answered, and asks once an answer is chosen', async () => {
    const { wrapper, push } = await mountPage()

    await wrapper.get('[data-testid="option-0"]').trigger('click')
    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(true)
    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    wrapper.unmount()

    const fresh = await mountPage()
    await fresh.wrapper.get('[data-testid="focus-close"]').trigger('click')
    expect(fresh.wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(fresh.push).toHaveBeenCalledWith({ name: 'student-learn' })
    expect(push).not.toHaveBeenCalled()
    fresh.wrapper.unmount()
  })

  it('closing the result screen keeps what unlocked for the path banner', async () => {
    api.lesson.mockResolvedValue(lessonPlay({ questions: lessonPlay().questions.slice(1, 2) }))
    api.checkLesson.mockResolvedValue(answerCheck({ correctAnswer: 'Amira', pinyin: null, meaning: null }))
    api.completeLesson.mockResolvedValue(lessonResult())
    const { wrapper, push } = await mountPage()

    await wrapper.get('[data-testid="skip"]').trigger('click')
    await flushPromises()
    // Nothing Chinese came up in this question, so there is no phrase to say.
    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    expect(push).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'student-learn',
        state: expect.objectContaining({ lessonResult: expect.anything() }),
      }),
    )
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
