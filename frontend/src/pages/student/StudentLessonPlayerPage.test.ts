import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { mountAtRoute } from '@/test/routes'
import { buttonNamed, checkChoice, deferred } from '@/test/helpers'
import { answerCheck, hintSettings, lessonPlay, lessonResult, today, xpResult } from '@/test/learnFixtures'
import type { AnswerCheck, TodaySummary } from '@/types/learn'

const api = vi.hoisted(() => ({
  lesson: vi.fn(),
  checkLesson: vi.fn(),
  completeLesson: vi.fn(),
  hint: vi.fn(),
  today: vi.fn(),
}))
const hintApi = vi.hoisted(() => ({ settings: vi.fn() }))

vi.mock('@/api/learn', () => ({
  learnApi: { lesson: api.lesson, checkLesson: api.checkLesson, completeLesson: api.completeLesson, hint: api.hint },
  hintApi,
  xpApi: { today: api.today },
}))

import StudentLessonPlayerPage from './StudentLessonPlayerPage.vue'

async function mountPage() {
  const { wrapper, router } = await mountAtRoute(StudentLessonPlayerPage, '/student/learn/lessons/13', {
    attachTo: document.body,
  })
  return { wrapper, push: vi.spyOn(router, 'push') }
}

async function skipToResult(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('[data-testid="skip"]').trigger('click')
  await flushPromises()
  await wrapper.get('[data-testid="continue"]').trigger('click')
  await flushPromises()
}

function singleQuestionCheckpoint() {
  const lesson = lessonPlay({ kind: 'CHECKPOINT', passPercent: 70 })
  return { ...lesson, questions: lesson.questions.slice(0, 1) }
}

async function finishOneQuestionLesson(result = lessonResult()) {
  const lesson = lessonPlay()
  api.lesson.mockResolvedValue({ ...lesson, questions: lesson.questions.slice(1, 2) })
  api.checkLesson.mockResolvedValue(answerCheck({ correctAnswer: 'Amira', pinyin: null, meaning: null }))
  api.completeLesson.mockResolvedValue(result)
  const page = await mountPage()
  await skipToResult(page.wrapper)
  return page
}

describe('StudentLessonPlayerPage (focus mode lesson)', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    hintApi.settings.mockReset()
    api.lesson.mockResolvedValue(lessonPlay())
    api.today.mockResolvedValue(today())
    // A new sign-in, so the shared hint settings are read again: hints are off unless a test turns them on.
    setAccessToken(null)
    hintApi.settings.mockResolvedValue(null)
  })

  it('asks for a hint with its run, and says why the server refused one and offers none more, as Practice does', async () => {
    const lesson = lessonPlay()
    api.lesson.mockResolvedValue(lesson)
    hintApi.settings.mockResolvedValue(hintSettings({ mode: 'ON_REQUEST', perSet: 2 }))
    api.hint.mockRejectedValueOnce({
      response: { status: 400, data: { success: false, message: 'There are no hints left in this set.' } },
    })
    const { wrapper } = await mountPage()
    await wrapper.get('[data-testid="hint-show"]').trigger('click')
    await flushPromises()

    // The run is the copy of the lesson read: the server counts its hints against the level's limit.
    expect(api.hint).toHaveBeenCalledWith(
      13,
      101,
      expect.objectContaining({ runStartedAt: lesson.runStartedAt, runSignature: lesson.runSignature }),
    )
    expect(wrapper.get('[data-testid="hint-failed"]').text()).toBe('There are no hints left in this set.')
    expect(wrapper.find('[data-testid="hint-offer"]').exists()).toBe(false)
    expect(api.hint).toHaveBeenCalledTimes(1)
    // The question is still answered and checked as usual.
    api.checkLesson.mockResolvedValue(answerCheck())
    await checkChoice(wrapper, 0)
    expect(api.checkLesson).toHaveBeenLastCalledWith(13, { questionId: 101, answer: 'name' })
  })

  it('takes back an answer a hint rules out, and its number key no longer picks it', async () => {
    hintApi.settings.mockResolvedValue(hintSettings({ mode: 'ON_REQUEST' }))
    api.hint.mockResolvedValue({
      kind: 'RULE_OUT',
      words: ['teacher'],
      pinyin: [],
      text: null,
      count: null,
      ruledOut: ['teacher'],
    })
    const { wrapper } = await mountPage()
    const pressed = (index: number) => wrapper.get(`[data-testid="option-${index}"]`).attributes('aria-pressed')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: '2' }))
    await flushPromises()
    expect(pressed(1)).toBe('true')

    await wrapper.get('[data-testid="hint-show"]').trigger('click')
    await flushPromises()
    expect(pressed(1)).toBe('false')
    expect(wrapper.get('[data-testid="check"]').attributes('disabled')).toBeDefined()

    document.dispatchEvent(new KeyboardEvent('keydown', { key: '2' }))
    await flushPromises()
    expect(pressed(1)).toBe('false')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: '1' }))
    await flushPromises()
    expect(pressed(0)).toBe('true')
  })

  it.each(['mouse', 'keyboard'])('keeps the submitted choice while checking despite %s input', async (input) => {
    const checked = deferred<AnswerCheck>()
    api.checkLesson.mockReturnValue(checked.promise)
    const { wrapper } = await mountPage()
    await wrapper.get('[data-testid="option-0"]').trigger('click')
    await wrapper.get('[data-testid="check"]').trigger('click')
    if (input === 'mouse') await wrapper.get('[data-testid="option-1"]').trigger('click')
    else document.dispatchEvent(new KeyboardEvent('keydown', { key: '2' }))
    await flushPromises()
    expect(wrapper.get('[data-testid="option-0"]').attributes('aria-pressed')).toBe('true')
    expect(api.checkLesson).toHaveBeenCalledOnce()
    checked.resolve(answerCheck())
    await flushPromises()
    expect(wrapper.get('[data-testid="check-bar"]').classes()).toContain('is-correct')
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
    // Until the learner is stuck, the footer's middle names the keys.
    expect(wrapper.get('.focus-footer-middle').text()).toBe('Press 1–2 to choose · Enter to check')

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

    // Not graded and not sent: a phrase from the lesson to say aloud. Continue is the only way on.
    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()
    const say = wrapper.get('[data-testid="say-step"]')
    expect(say.text()).toContain('Listen, then say it')
    expect(say.text()).toContain('我叫Amira')
    expect(say.text()).toContain('Wǒ jiào Amira · My name is Amira.')
    expect(wrapper.findAll('.focus-footer button').map((button) => button.text())).toEqual(['Continue'])
    expect(api.completeLesson).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="say-done"]').trigger('click')
    await flushPromises()
    expect(api.completeLesson).toHaveBeenCalledWith(13, [
      { questionId: 101, answer: 'name' },
      { questionId: 102, answer: '我 叫' },
    ])
    const done = wrapper.get('[data-testid="lesson-complete"]')
    expect(done.text()).toContain('Lesson complete!')
    expect(done.text()).toContain('+10')
    expect(done.text()).toContain('Next up: Fruit')
    expect(done.text()).toContain('New badge: Unit Champion')
    expect(done.get('.learn-stamp-seal').text()).toBe('棒')
    expect(done.get('.learn-stamp-caption').text()).toBe('bàng · great job!')
    // The result leads with the XP and the accuracy; the streak line says where today's goal stands.
    expect(done.findAll('dt').map((term) => term.text())).toEqual(['XP earned', 'accuracy'])
    expect(done.get('.learn-done-streak').text()).toBe('10 XP more reaches today’s goal')
    expect(done.findAll('.learn-done-week > span')).toHaveLength(7)
    expect(done.findAll('.learn-done-week > .is-reached')).toHaveLength(6)
    // One wrong answer: Review mistakes on the left, Continue on the right.
    expect(wrapper.get('.focus-footer-start a').text()).toBe('Review mistakes')
    expect(wrapper.get('.focus-footer-end [data-testid="lesson-continue"]').classes()).toContain('btn-primary')
  })

  it('sends a skipped question as a null answer and calls the verdict Skipped', async () => {
    api.checkLesson.mockResolvedValue(answerCheck({ correct: false }))
    const { wrapper } = await mountPage()

    await wrapper.get('[data-testid="skip"]').trigger('click')
    await flushPromises()

    expect(api.checkLesson).toHaveBeenCalledWith(13, { questionId: 101, answer: null })
    const bar = wrapper.get('[data-testid="check-bar"]')
    expect(bar.classes()).toContain('is-wrong')
    expect(bar.get('.learn-checkbar-title').text()).toBe('Skipped')
    // The right option is marked in place, so the band does not write it again.
    expect(wrapper.get('[data-testid="option-0"]').classes()).toContain('is-correct')
    expect(bar.find('.learn-checkbar-answer').exists()).toBe(false)
  })

  it('says “Not quite.” and why after a wrong pick, with the right option marked in place', async () => {
    api.checkLesson.mockResolvedValue(
      answerCheck({ correct: false, explanation: '名字 (míngzi) means “name”; 老师 is a teacher.' }),
    )
    const { wrapper } = await mountPage()

    await checkChoice(wrapper, 1)

    const bar = wrapper.get('[data-testid="check-bar"]')
    expect(bar.get('.learn-checkbar-title').text()).toBe('Not quite.')
    expect(bar.get('.learn-checkbar-explanation').text()).toBe('名字 (míngzi) means “name”; 老师 is a teacher.')
    expect(bar.find('.learn-checkbar-answer').exists()).toBe(false)
    expect(wrapper.get('[data-testid="option-0"]').classes()).toContain('is-correct')
    expect(wrapper.get('[data-testid="option-1"]').classes()).toContain('is-wrong')
  })

  it('offers SparkBot’s hint in the footer once the learner is stuck, with its AI note and Report', async () => {
    hintApi.settings.mockResolvedValue(hintSettings({ mode: 'ON_REQUEST' }))
    api.hint.mockResolvedValue({
      kind: 'CLUE',
      words: [],
      pinyin: [],
      text: 'It is what people call you.',
      count: null,
      ruledOut: [],
    })
    const { wrapper } = await mountPage()

    const middle = wrapper.get('.focus-footer-middle')
    expect(middle.get('[data-testid="hint-offer"]').text()).toContain('Want a hint? Ask SparkBot whenever you like.')
    await wrapper.get('[data-testid="hint-show"]').trigger('click')
    await flushPromises()

    const card = wrapper.get('.focus-footer-middle [data-testid="hint-card"]')
    expect(card.get('.hint-panel-title').text()).toBe('SparkBot hint')
    expect(card.text()).toContain('AI can make mistakes.')
    expect(card.text()).toContain('It is what people call you.')
    expect(card.get('.hint-panel-note').text()).toBe('Hints never cost XP.')
    expect(card.get('[data-testid="hint-report"]').text()).toBe('Report')
    // The question above never moves: the hint is not part of the stage.
    expect(wrapper.find('.focus-stage [data-testid="hint-card"]').exists()).toBe(false)
  })

  it('goes straight to the result of a checkpoint: a test has no step to say a phrase', async () => {
    api.lesson.mockResolvedValue(singleQuestionCheckpoint())
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
  })

  it('lets Try again answer at once while the week’s streak is still on its way, and shows the latest week', async () => {
    api.lesson.mockResolvedValue(singleQuestionCheckpoint())
    api.checkLesson.mockResolvedValue(answerCheck({ correct: false }))
    api.completeLesson.mockResolvedValue(
      lessonResult({ kind: 'CHECKPOINT', passed: false, passPercent: 70, accuracy: 0, nextLesson: null }),
    )
    const firstWeek = deferred<TodaySummary>()
    const secondWeek = deferred<TodaySummary>()
    api.today.mockReturnValue(firstWeek.promise)
    const { wrapper } = await mountPage()
    await skipToResult(wrapper)
    expect(wrapper.get('[data-testid="lesson-complete"]').text()).toContain('Not passed yet')

    await buttonNamed(wrapper, 'Try again').trigger('click')
    expect(wrapper.get('[data-testid="option-0"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-testid="option-0"]').trigger('click')
    expect(wrapper.get('[data-testid="option-0"]').attributes('aria-pressed')).toBe('true')
    await wrapper.get('[data-testid="check"]').trigger('click')
    await flushPromises()
    expect(api.checkLesson).toHaveBeenLastCalledWith(13, { questionId: 101, answer: 'name' })

    api.today.mockReturnValue(secondWeek.promise)
    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()
    expect(api.completeLesson).toHaveBeenCalledTimes(2)
    // The first attempt's week answers last: the strip still shows the second one's.
    secondWeek.resolve(today({ week: today().week.map((day) => ({ ...day, goalReached: true })) }))
    await flushPromises()
    firstWeek.resolve(today())
    await flushPromises()
    expect(wrapper.findAll('.learn-done-week > .is-reached')).toHaveLength(7)
  })

  it('finishes once and waits for the saved result before allowing a close', async () => {
    api.lesson.mockResolvedValue(singleQuestionCheckpoint())
    api.checkLesson.mockResolvedValue(answerCheck({ correct: false }))
    const saving = deferred<ReturnType<typeof lessonResult>>()
    api.completeLesson.mockReturnValueOnce(saving.promise)
    const { wrapper } = await mountPage()
    await wrapper.get('[data-testid="skip"]').trigger('click')
    await flushPromises()

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
    await flushPromises()

    expect(api.completeLesson).toHaveBeenCalledOnce()
    const close = wrapper.get<HTMLButtonElement>('[data-testid="focus-close"]')
    expect(close.element.disabled).toBe(true)
    await close.trigger('click')
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    saving.resolve(lessonResult())
    await flushPromises()
    expect(close.element.disabled).toBe(false)
  })

  it('asks before leaving a lesson in progress: Keep going stays, Leave goes back to the path', async () => {
    api.checkLesson.mockResolvedValue(answerCheck())
    const { wrapper, push } = await mountPage()
    await checkChoice(wrapper, 0)

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    const dialog = wrapper.get('[data-testid="confirm-dialog"]')
    expect(dialog.text()).toContain('Leave this lesson?')
    expect(wrapper.get('[data-testid="confirm-dialog-cancel"]').classes()).toContain('btn-secondary')
    expect(wrapper.get('[data-testid="confirm-dialog-confirm"]').classes()).toContain('btn-primary')

    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(push).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    expect(push).toHaveBeenCalledWith({ name: 'student-learn' })
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
  })

  it('opens the rank-up dialog on Continue when the lesson passed a rank, then goes on to the map', async () => {
    const { wrapper, push } = await finishOneQuestionLesson(
      lessonResult({
        xp: xpResult({ currentStreak: 8, rankUp: { rank: 4, rankXp: 300, nextRankXp: 400 } }),
      }),
    )
    await wrapper.get('[data-testid="lesson-continue"]').trigger('click')
    const dialog = wrapper.get('[data-testid="rank-up"]')
    expect(dialog.text()).toContain('You reached Rank 4 · ')
    expect(dialog.text()).toContain(
      'You reached 300 XP with today’s lesson. Keep the 8-day streak going — Rank 5 opens at 400 XP.',
    )
    expect(push).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="rank-up-keep-going"]').trigger('click')
    expect(push).toHaveBeenCalledWith(expect.objectContaining({ name: 'student-learn' }))
  })

  it('goes straight back to the map when the lesson passed no rank', async () => {
    const { wrapper, push } = await finishOneQuestionLesson()
    await wrapper.get('[data-testid="lesson-continue"]').trigger('click')

    expect(wrapper.find('[data-testid="rank-up"]').exists()).toBe(false)
    expect(push).toHaveBeenCalledWith(expect.objectContaining({ name: 'student-learn' }))
  })

  it('closing the result screen keeps what unlocked for the path toast', async () => {
    // Nothing Chinese came up in this question, so there is no phrase to say.
    const { wrapper, push } = await finishOneQuestionLesson()

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    expect(push).toHaveBeenCalledWith({
      name: 'student-learn',
      state: {
        lessonResult: {
          kind: 'LESSON',
          passed: true,
          nextLesson: { title: 'Fruit', unlockedNow: true },
          xp: { xpEarned: 10, bonusXp: 0 },
        },
      },
    })
  })

  it('shows why a locked lesson cannot be opened', async () => {
    api.lesson.mockRejectedValue({
      response: { status: 409, data: { message: 'This lesson is still locked. Finish the lessons before it first.' } },
    })
    const { wrapper } = await mountPage()

    expect(wrapper.text()).toContain('This lesson is still locked. Finish the lessons before it first.')
    expect(wrapper.find('[data-testid="question-stage"]').exists()).toBe(false)
  })

  it('shows an alert icon and distinct message when a lesson has no questions', async () => {
    api.lesson.mockResolvedValue({ ...lessonPlay(), questions: [] })
    const { wrapper } = await mountPage()

    expect(wrapper.get('.state-panel .state-title').text()).toBe('This lesson cannot be opened')
    expect(wrapper.get('.state-panel .muted').text()).toBe('This lesson has no questions right now.')
    expect(wrapper.findAll('.state-panel button')).toHaveLength(0)
  })
})
