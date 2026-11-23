import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { mountAtRoute } from '@/test/routes'
import { checkChoice, deferred } from '@/test/helpers'
import { answerCheck, hintSettings, lessonPlay, xpResult } from '@/test/learnFixtures'
import type { MistakeReview, MistakeReviewResult } from '@/types/learn'

const api = vi.hoisted(() => ({ review: vi.fn(), check: vi.fn(), complete: vi.fn(), hint: vi.fn() }))
const hintApi = vi.hoisted(() => ({ settings: vi.fn() }))

vi.mock('@/api/learn', () => ({ mistakesApi: api, hintApi }))

import StudentMistakesReviewPage from './StudentMistakesReviewPage.vue'

const RUN = '2026-05-18T03:00:00.000001'
const SIGNATURE = 'c2lnbmVkLWJ5LXRoZS1zZXJ2ZXI'

function review(context: Partial<MistakeReview['context'][number]> = {}): MistakeReview {
  const [first] = lessonPlay().questions
  return {
    questions: [{ ...first!, id: 7 }],
    context: [
      { sourceTitle: 'Polite phrases', fromLearn: true, yourAnswer: 'teacher', stillLearning: false, ...context },
    ],
    runStartedAt: RUN,
    runSignature: SIGNATURE,
  }
}

async function mountPage() {
  return (await mountAtRoute(StudentMistakesReviewPage, '/student/practice/review')).wrapper
}

describe('StudentMistakesReviewPage (focus mode review)', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    hintApi.settings.mockReset()
    api.review.mockResolvedValue(review())
    // A new sign-in, so the shared hint settings are read again: hints are off unless a test turns them on.
    setAccessToken(null)
    hintApi.settings.mockResolvedValue(null)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('keeps the submitted choice pending and allows a new answer after a failed check', async () => {
    const checked = deferred()
    api.check.mockReturnValue(checked.promise)
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="option-0"]').trigger('click')
    await wrapper.get('[data-testid="check"]').trigger('click')
    await wrapper.get('[data-testid="option-1"]').trigger('click')
    expect(wrapper.get('[data-testid="option-0"]').attributes('aria-pressed')).toBe('true')

    checked.reject(new Error('offline'))
    await flushPromises()
    await wrapper.get('[data-testid="option-1"]').trigger('click')
    expect(wrapper.get('[data-testid="option-1"]').attributes('aria-pressed')).toBe('true')
    api.check.mockResolvedValue(answerCheck())
    await wrapper.get('[data-testid="check"]').trigger('click')
    await flushPromises()
    expect(api.check).toHaveBeenLastCalledWith({ questionId: 7, answer: 'teacher' })
  })

  it('asks each waiting mistake again with where it came from and clears it when answered right', async () => {
    api.check.mockResolvedValue(answerCheck({ explanation: '名字 (míngzi) means “name”.' }))
    const saving = deferred<MistakeReviewResult>()
    api.complete.mockReturnValueOnce(saving.promise)
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('Mistakes review · 1 of 1')
    // Where it came from follows the word's pinyin.
    expect(wrapper.get('.learn-question-pinyin').text()).toBe(
      'míngzi · Learn · Polite phrases · you answered “teacher” last time',
    )
    expect(wrapper.get('.focus-topbar [role="progressbar"]').attributes('aria-valuenow')).toBe('0')
    expect(wrapper.get('.focus-footer-middle').text()).toBe('Press 1–2 to choose · Enter to check')

    await checkChoice(wrapper, 0)
    expect(api.check).toHaveBeenCalledWith({ questionId: 7, answer: 'name' })
    // Checked: the right option is marked and the footer says why.
    expect(wrapper.get('[data-testid="option-0"]').classes()).toContain('is-correct')
    const bar = wrapper.get('[data-testid="check-bar"]')
    expect(bar.get('.learn-checkbar-title').text()).toBe('Correct!')
    expect(bar.text()).toContain('名字 (míngzi) means “name”.')

    const finish = wrapper.get<HTMLButtonElement>('[data-testid="continue"]').element
    finish.click()
    finish.click()
    await flushPromises()
    expect(api.complete).toHaveBeenCalledTimes(1)
    expect(api.complete).toHaveBeenCalledWith([{ questionId: 7, answer: 'name' }])
    const close = wrapper.get<HTMLButtonElement>('[data-testid="focus-close"]')
    expect(close.element.disabled).toBe(true)
    await close.trigger('click')
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    saving.resolve({ cleared: 1, left: 5, xp: xpResult({ xpEarned: 10 }) })
    await flushPromises()
    expect(close.element.disabled).toBe(false)
    const done = wrapper.get('[data-testid="review-complete"]')
    expect(done.get('h2').text()).toBe('Review complete!')
    expect(done.findAll('dt').map((term) => term.text())).toEqual(['XP earned', 'cleared', 'left for later'])
    expect(done.findAll('dd').map((value) => value.text())).toEqual(['+10', '1', '5'])
    expect(done.get('.practice-review-streak').text()).toBe('2-day streak · 10 / 20 XP today')
    expect(wrapper.get('[data-testid="review-done"]').attributes('href')).toBe('/student/practice/mistakes')
    expect(wrapper.get('.focus-topbar [role="progressbar"]').attributes('aria-valuenow')).toBe('100')
  })

  it('names a flashcard marked “Still learning” as where the mistake came from', async () => {
    api.review.mockResolvedValue(
      review({ sourceTitle: 'Greetings', fromLearn: false, yourAnswer: null, stillLearning: true }),
    )
    const wrapper = await mountPage()

    expect(wrapper.get('.learn-question-pinyin').text()).toBe('míngzi · marked “Still learning” in Flashcards')
  })

  it('shows the right answer after Skip, and that the mistake stays on the list', async () => {
    api.check.mockResolvedValue(
      answerCheck({ correct: false, correctAnswer: 'name', explanation: '名字 (míngzi) means “name”.' }),
    )
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="skip"]').trigger('click')
    await flushPromises()

    expect(api.check).toHaveBeenCalledWith({ questionId: 7, answer: null })
    const bar = wrapper.get('[data-testid="check-bar"]')
    expect(bar.get('.learn-checkbar-title').text()).toBe('Skipped')
    expect(bar.get('.learn-checkbar-explanation').text()).toBe(
      '名字 (míngzi) means “name”. It stays on your Mistakes list.',
    )
    // No pick of the learner's is marked: only the right answer, in place, so the band does not write it again.
    expect(wrapper.get('[data-testid="option-0"]').classes()).toContain('is-correct')
    expect(wrapper.findAll('.option-row.is-wrong')).toHaveLength(0)
    expect(bar.find('.learn-checkbar-answer').exists()).toBe(false)
  })

  it('says a wrong answer stays on the Mistakes list too', async () => {
    api.check.mockResolvedValue(answerCheck({ correct: false, correctAnswer: 'name', explanation: null }))
    const wrapper = await mountPage()

    await checkChoice(wrapper, 1)
    expect(api.check).toHaveBeenCalledWith({ questionId: 7, answer: 'teacher' })
    const bar = wrapper.get('[data-testid="check-bar"]')
    expect(bar.get('.learn-checkbar-title').text()).toBe('Not quite.')
    expect(bar.get('.learn-checkbar-explanation').text()).toBe('It stays on your Mistakes list.')
  })

  it('picks with the number keys and checks with Enter, as a lesson does', async () => {
    api.check.mockResolvedValue(answerCheck())
    const wrapper = await mountPage()

    document.dispatchEvent(new KeyboardEvent('keydown', { key: '1' }))
    await flushPromises()
    expect(wrapper.get('[data-testid="option-0"]').attributes('aria-pressed')).toBe('true')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
    await flushPromises()
    expect(api.check).toHaveBeenCalledWith({ questionId: 7, answer: 'name' })
  })

  // The server reads a built answer as the lesson shows it (Chinese tiles close up, other words keep their spaces).
  it.each(['Good night', 'mā bà dú hē', '我叫'])('shows the last answer “%s” as the server reads it', async (sent) => {
    api.review.mockResolvedValue(review({ yourAnswer: sent }))
    expect((await mountPage()).text()).toContain(`you answered “${sent}” last time`)
  })

  it('asks before leaving once something was answered, and not before', async () => {
    api.check.mockResolvedValue(answerCheck())
    const { wrapper: fresh, router: freshRouter } = await mountAtRoute(
      StudentMistakesReviewPage,
      '/student/practice/review',
    )

    await fresh.get('[data-testid="focus-close"]').trigger('click')
    await flushPromises()
    expect(fresh.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(freshRouter.currentRoute.value.name).toBe('student-practice')
    expect(freshRouter.currentRoute.value.params.type).toBe('mistakes')

    const { wrapper, router } = await mountAtRoute(StudentMistakesReviewPage, '/student/practice/review')
    await checkChoice(wrapper, 0)
    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    const dialog = wrapper.get('[data-testid="confirm-dialog"]')
    expect(dialog.text()).toContain('Leave the review?')
    expect(dialog.text()).toContain(
      'If you leave now, your progress in this review isn’t saved. The mistakes stay on your list.',
    )
    expect(wrapper.get('[data-testid="confirm-dialog-cancel"]').text()).toBe('Keep going')
    expect(wrapper.get('[data-testid="confirm-dialog-confirm"]').text()).toBe('Leave')

    await wrapper.get('[data-testid="confirm-dialog-cancel"]').trigger('click')
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(router.currentRoute.value.name).toBe('student-practice-review')

    await wrapper.get('[data-testid="focus-close"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('student-practice')
    expect(router.currentRoute.value.params.type).toBe('mistakes')
  })

  it('celebrates an empty list once the last mistake is fixed', async () => {
    api.check.mockResolvedValue(answerCheck())
    api.complete.mockResolvedValue({ cleared: 1, left: 0, xp: xpResult({ xpEarned: 10 }) })
    const wrapper = await mountPage()

    await checkChoice(wrapper, 0)
    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="review-complete"] h2').text()).toBe('All fixed!')
  })

  it('opens the rank-up dialog a moment after a review that passed a rank', async () => {
    api.check.mockResolvedValue(answerCheck())
    api.complete.mockResolvedValue({
      cleared: 1,
      left: 5,
      xp: xpResult({ currentStreak: 8, rankUp: { rank: 4, rankXp: 300, nextRankXp: 400 } }),
    })
    const wrapper = await mountPage()
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    await checkChoice(wrapper, 0)
    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="rank-up"]').exists()).toBe(false)

    vi.advanceTimersByTime(600)
    await flushPromises()
    expect(wrapper.get('[data-testid="rank-up"]').text()).toContain('You reached 300 XP with today’s practice.')
    await wrapper.get('[data-testid="rank-up-keep-going"]').trigger('click')
    expect(wrapper.find('[data-testid="rank-up"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="review-complete"]').exists()).toBe(true)
  })

  it.each([
    ['listening set gets the listening wording', '一杯茶', 'Tap Slow and listen to the start: it begins with “y…”.'],
    ['choice keeps the plain wording', null, 'The answer starts with the sound “y…”.'],
  ])('words the hint of a mistake from a Practice %s, in the footer', async (_case, audioText, sentence) => {
    hintApi.settings.mockResolvedValue(hintSettings({ mode: 'ON_REQUEST' }))
    api.hint.mockResolvedValue({
      kind: 'FIRST_SOUND',
      words: [],
      pinyin: [],
      text: 'y',
      count: 3,
      ruledOut: [],
    })
    // As the server sends a Practice mistake: no question type; a listening one has its audio and no characters.
    api.review.mockResolvedValue({
      questions: [
        {
          id: 9,
          answerKind: 'CHOICE',
          type: null,
          prompt: 'Which phrase did you hear?',
          hanzi: null,
          pinyin: null,
          meaning: null,
          audioText,
          options: [
            { text: '一杯茶', pinyin: 'yì bēi chá' },
            { text: '一碗米饭', pinyin: 'yì wǎn mǐfàn' },
          ],
          tiles: [],
        },
      ],
      context: [{ sourceTitle: 'At the café', fromLearn: false, yourAnswer: '一碗米饭', stillLearning: false }],
      runStartedAt: RUN,
      runSignature: SIGNATURE,
    })
    const wrapper = await mountPage()

    expect(wrapper.get('.focus-footer-middle [data-testid="hint-offer"]').text()).toContain('Ask SparkBot')
    await wrapper.get('[data-testid="hint-show"]').trigger('click')
    await flushPromises()
    // The hint names the review's run: the server counts its hints against the level's limit.
    expect(api.hint).toHaveBeenCalledWith(9, expect.objectContaining({ runStartedAt: RUN, runSignature: SIGNATURE }))
    const card = wrapper.get('.focus-footer-middle [data-testid="hint-card"]')
    expect(card.get('.hint-panel-text').text()).toBe(sentence)
    expect(card.get('.hint-panel-note').text()).toBe('Hints never cost XP.')
  })

  it('says why the server refused a hint and offers none more for that mistake, as Practice does', async () => {
    hintApi.settings.mockResolvedValue(hintSettings({ mode: 'ON_REQUEST', perSet: 2 }))
    api.hint.mockRejectedValueOnce({
      response: { status: 400, data: { success: false, message: 'There are no hints left in this set.' } },
    })
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="hint-show"]').trigger('click')
    await flushPromises()

    expect(api.hint).toHaveBeenCalledWith(7, expect.objectContaining({ runStartedAt: RUN, runSignature: SIGNATURE }))
    expect(wrapper.get('[data-testid="hint-failed"]').text()).toBe('There are no hints left in this set.')
    expect(wrapper.find('[data-testid="hint-offer"]').exists()).toBe(false)
    expect(api.hint).toHaveBeenCalledTimes(1)
  })

  it('says there is nothing to review when no mistake is waiting', async () => {
    api.review.mockResolvedValue({ questions: [], context: [], runStartedAt: RUN, runSignature: SIGNATURE })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('Nothing to review')
    expect(wrapper.find('[data-testid="check-bar"]').exists()).toBe(false)
  })
})
