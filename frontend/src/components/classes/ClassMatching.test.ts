import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useHintSettings } from '@/composables/useHintSettings'
import { i18n } from '@/plugins/i18n'
import { buttonNamed, deferred, withSetup } from '@/test/helpers'
import type { HintMode, HintSettings } from '@/types/learn'
import type { ActivityOutcome } from '@/types/classes'

const api = vi.hoisted(() => ({ checkMatching: vi.fn(), matchingHint: vi.fn() }))
const hintApi = vi.hoisted(() => ({ settings: vi.fn(), report: vi.fn() }))

vi.mock('@/api/classes', () => ({ studentClassesApi: api }))
vi.mock('@/api/learn', () => ({ hintApi }))

import ClassMatching from './ClassMatching.vue'

const words = [
  { id: 1, chinese: '妈妈', pinyin: 'māma' },
  { id: 2, chinese: '爸爸', pinyin: 'bàba' },
]

const BOTH_WRONG: ActivityOutcome = {
  done: false,
  pairs: [
    { wordId: 1, correct: false },
    { wordId: 2, correct: false },
  ],
  lessonStatus: 'IN_PROGRESS',
}

function settings(mode: HintMode): HintSettings {
  return { mode, tier: 'HSK1', delaySeconds: 4, perSet: 0 }
}

let hintSettings: ReturnType<typeof useHintSettings>

/** The learner's hint setting, as the Account page would have saved it. */
function useMode(mode: HintMode): void {
  hintSettings.remember(settings(mode))
}

function mountMatching(hintsAllowed = true, meanings = ['dad', 'mum']) {
  return mount(ClassMatching, {
    props: { title: 'Family', activityId: 5, lessonTitle: 'My Family', words, meanings, hintsAllowed, done: false },
    global: { stubs: { teleport: true } },
  })
}

async function pair(wrapper: ReturnType<typeof mountMatching>, wordId: number, meaning: string) {
  await wrapper.get(`[data-testid="match-word-${wordId}"]`).trigger('click')
  await wrapper
    .findAll('.class-matching-item')
    .find((item) => item.text() === meaning)!
    .trigger('click')
}

describe('ClassMatching', () => {
  beforeEach(async () => {
    api.checkMatching.mockReset()
    api.matchingHint.mockReset()
    hintApi.report.mockReset()
    hintApi.settings.mockResolvedValue(settings('OFF'))
    // The setting loads once for the file; each test starts with hints off and sets the mode it needs.
    hintSettings = withSetup(useHintSettings).result
    await flushPromises()
    useMode('OFF')
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('checks a full board on the server at once, marks the wrong pairs and finishes when every pair is right', async () => {
    const wrapper = mountMatching()
    api.checkMatching.mockResolvedValueOnce(BOTH_WRONG)
    await pair(wrapper, 1, 'dad')
    // S11.08: no Check button; nothing is sent before the board is full.
    expect(wrapper.find('[data-testid="activity-finish"]').exists()).toBe(false)
    expect(api.checkMatching).not.toHaveBeenCalled()
    await pair(wrapper, 2, 'mum')
    expect(wrapper.text()).toContain('2 of 2 paired')
    await flushPromises()

    expect(api.checkMatching).toHaveBeenCalledWith(5, [
      { wordId: 1, meaning: 'dad' },
      { wordId: 2, meaning: 'mum' },
    ])
    // Wrong pairs stay on show, marked, until the learner changes them.
    expect(wrapper.get('[data-testid="match-word-1"]').classes()).toContain('is-wrong')
    expect(wrapper.text()).toContain('2 of 2 pairs are wrong')
    expect(wrapper.text()).toContain('Tap a marked pair to fix it.')
    expect(wrapper.emitted('outcome')).toBeUndefined()

    // Try again takes the wrong pairs apart. The same button then reads Reset, off with nothing paired: the focus stays.
    const footerStart = buttonNamed(wrapper, 'Try again')
    await footerStart.trigger('click')
    expect(wrapper.text()).toContain('0 of 2 paired')
    expect(wrapper.text()).not.toContain('pairs are wrong')
    expect(buttonNamed(wrapper, 'Reset').element).toBe(footerStart.element)
    expect(footerStart.attributes('aria-disabled')).toBe('true')

    const outcome = {
      done: true,
      pairs: [
        { wordId: 1, correct: true },
        { wordId: 2, correct: true },
      ],
      lessonStatus: 'IN_PROGRESS',
    }
    api.checkMatching.mockResolvedValueOnce(outcome)
    await pair(wrapper, 1, 'mum')
    await pair(wrapper, 2, 'dad')
    await flushPromises()

    expect(api.checkMatching).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('outcome')?.[0]).toEqual([outcome])
    expect(wrapper.get('[data-testid="match-word-1"]').classes()).toContain('is-right')
  })

  it('undoes a marked pair when it is tapped, and checks again only once no marked pair is left', async () => {
    const wrapper = mountMatching()
    api.checkMatching.mockResolvedValueOnce(BOTH_WRONG)
    await pair(wrapper, 1, 'dad')
    await pair(wrapper, 2, 'mum')
    await flushPromises()

    await wrapper.get('[data-testid="match-word-1"]').trigger('click')

    expect(wrapper.get('[data-testid="match-word-1"]').classes()).not.toContain('is-wrong')
    expect(wrapper.text()).toContain('1 of 2 paired')
    expect(wrapper.text()).toContain('1 of 2 pairs is wrong')

    // The board is full again, but 爸爸 is still marked wrong: checking the same pair again would only mark it again.
    await pair(wrapper, 1, 'dad')
    await flushPromises()
    expect(api.checkMatching).toHaveBeenCalledTimes(1)
  })

  it('names the columns after questions and answers when the answers are in Chinese', () => {
    const wrapper = mountMatching(true, ['你好', '再见'])

    expect(wrapper.text()).toContain('Question')
    expect(wrapper.text()).toContain('Tap a question, then its answer.')
  })

  it('offers a SparkBot hint to a stuck learner after their delay, and a pair made starts the wait again', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    useMode('AUTOMATIC')
    const wrapper = mountMatching()
    expect(wrapper.find('[data-testid="class-hint-offer"]').exists()).toBe(false)

    await vi.advanceTimersByTimeAsync(3000)
    await pair(wrapper, 1, 'dad')
    await vi.advanceTimersByTimeAsync(3000)
    expect(wrapper.find('[data-testid="class-hint-offer"]').exists()).toBe(false)

    await vi.advanceTimersByTimeAsync(1000)
    expect(wrapper.get('[data-testid="class-hint-offer"]').text()).toContain('Stuck? SparkBot can give you a hint.')
  })

  it('shows the hint for the first word not paired yet, with the AI note, and lets the learner report it', async () => {
    useMode('ON_REQUEST')
    api.matchingHint.mockResolvedValue({ wordId: 1, meaning: 'mum' })
    hintApi.report.mockResolvedValue(undefined)
    const wrapper = mountMatching()

    await wrapper.get('[data-testid="class-hint-show"]').trigger('click')
    await flushPromises()

    expect(api.matchingHint).toHaveBeenCalledWith(5, 1)
    const card = wrapper.get('[data-testid="class-hint"]')
    expect(card.text()).toContain('SparkBot hint')
    expect(card.text()).toContain('妈妈 means “mum”.')
    expect(card.text()).toContain('AI can make mistakes.')
    // S11.12: a plain footnote; a link would leave the activity.
    const footnote = card.get('[data-testid="class-hint-settings"]')
    expect(footnote.text()).toBe('You can change hints in Account.')
    expect(footnote.element.tagName).toBe('P')

    await wrapper.get('[data-testid="class-hint-report"]').trigger('click')
    await wrapper.get('[data-testid="hint-report-form"]').trigger('submit')
    await flushPromises()

    expect(hintApi.report).toHaveBeenCalledWith(
      expect.objectContaining({ aiResponseSnapshot: '妈妈 means “mum”.', reasonCategory: 'INCORRECT_CONTENT' }),
    )
    expect(hintApi.report.mock.calls[0]![0].promptSnapshot).toContain('My Family · Family')
    expect(wrapper.get('[data-testid="toast"]').text()).toBe(
      'Report sent. An admin replies in Ask SparkBot › My reports.',
    )
    expect(wrapper.get('[data-testid="class-hint-report"]').text()).toBe('Reported')
    i18n.global.locale.value = 'ms'
    await flushPromises()
    expect(wrapper.get('.hint-panel-text').text()).toBe('妈妈 bermaksud “mum”.')
    expect(wrapper.get('[data-testid="class-hint-report"]').attributes('disabled')).toBeDefined()
  })

  it('offers no hint when the lesson does not allow hints or the learner turned them off', async () => {
    useMode('ON_REQUEST')
    expect(mountMatching(false).find('[data-testid="class-hint-offer"]').exists()).toBe(false)

    useMode('OFF')
    expect(mountMatching(true).find('[data-testid="class-hint-offer"]').exists()).toBe(false)
  })

  it('keeps the hint offer available when the requested word was paired before its hint arrived', async () => {
    useMode('ON_REQUEST')
    const pending = deferred<{ wordId: number; meaning: string }>()
    api.matchingHint.mockReturnValueOnce(pending.promise)
    const wrapper = mountMatching()

    await wrapper.get('[data-testid="class-hint-show"]').trigger('click')
    await pair(wrapper, 1, 'mum')
    pending.resolve({ wordId: 1, meaning: 'mum' })
    await flushPromises()

    expect(wrapper.find('[data-testid="class-hint"]').exists()).toBe(false)
    const showHint = wrapper.get('[data-testid="class-hint-show"]')
    expect(showHint.attributes('aria-disabled')).toBeUndefined()
    expect(showHint.attributes('aria-busy')).toBe('false')
    api.matchingHint.mockResolvedValueOnce({ wordId: 2, meaning: 'dad' })
    await showHint.trigger('click')
    await flushPromises()
    expect(api.matchingHint).toHaveBeenLastCalledWith(5, 2)
  })

  it('says when a check fails, and clears it when the next check starts; a failed hint says so in place', async () => {
    useMode('ON_REQUEST')
    api.checkMatching.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(BOTH_WRONG)
    api.matchingHint.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountMatching()

    await wrapper.get('[data-testid="class-hint-show"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('The hint could not be loaded. Try again.')

    await pair(wrapper, 1, 'dad')
    await pair(wrapper, 2, 'mum')
    await flushPromises()
    expect(wrapper.emitted('error')).toEqual([[null], ['Your answers could not be checked.']])

    // A check that did not reach the server offers to send the same pairs again; a successful check with wrong pairs
    // emits no outcome, but still clears the old failure.
    await wrapper.get('[data-testid="matching-check-again"]').trigger('click')
    await flushPromises()
    expect(api.checkMatching).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('error')!.at(-1)).toEqual([null])
    expect(wrapper.find('[data-testid="matching-check-again"]').exists()).toBe(false)
  })

  it('drops a failed hint’s message once a pair is made, as the offer then waits again', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    useMode('AUTOMATIC')
    api.matchingHint.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountMatching()
    await vi.advanceTimersByTimeAsync(4000)
    await wrapper.get('[data-testid="class-hint-show"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('The hint could not be loaded. Try again.')

    await pair(wrapper, 1, 'dad')
    expect(wrapper.find('[data-testid="class-hint-offer"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('The hint could not be loaded.')
  })

  it('keeps submitted pairs unchanged while their result is pending', async () => {
    const checked = deferred<ActivityOutcome>()
    api.checkMatching.mockReturnValueOnce(checked.promise)
    const wrapper = mountMatching()
    await pair(wrapper, 1, 'dad')
    await pair(wrapper, 2, 'mum')

    await wrapper.get('[data-testid="match-word-1"]').trigger('click')
    await wrapper.findAll('.class-matching-column')[1]!.findAll('button')[1]!.trigger('click')
    expect(wrapper.text()).toContain('2 of 2 paired')

    checked.resolve(BOTH_WRONG)
    await flushPromises()
    expect(wrapper.get('[data-testid="match-word-1"]').text()).toContain('dad')
    expect(wrapper.get('[data-testid="match-word-2"]').text()).toContain('mum')
    expect(wrapper.text()).toContain('2 of 2 pairs are wrong')
    expect(wrapper.get('[data-testid="match-word-1"]').attributes('disabled')).toBeUndefined()
  })

  it('keeps Reported hint state across a wrong-check cycle and keeps the confirmation toast after completion', async () => {
    useMode('ON_REQUEST')
    api.matchingHint.mockResolvedValue({ wordId: 1, meaning: 'mum' })
    hintApi.report.mockResolvedValue({ id: 9 })
    api.checkMatching.mockResolvedValueOnce(BOTH_WRONG)
    const wrapper = mountMatching()

    await wrapper.get('[data-testid="class-hint-show"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="class-hint-report"]').trigger('click')
    await wrapper.get('[data-testid="hint-report-form"]').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Report sent')

    // Wrong check unmounts ClassHint; Try again remounts it and must remember that word 1's hint was reported.
    await pair(wrapper, 1, 'dad')
    await pair(wrapper, 2, 'mum')
    await flushPromises()
    expect(wrapper.text()).toContain('2 of 2 pairs are wrong')
    await buttonNamed(wrapper, 'Try again').trigger('click')
    await wrapper.get('[data-testid="class-hint-show"]').trigger('click')
    await flushPromises()
    const reportButton = wrapper.get('[data-testid="class-hint-report"]')
    expect(reportButton.attributes('disabled')).toBeDefined()
    expect(reportButton.text()).toContain('Reported')

    // Toast remains mounted at root even once the board finishes and FocusFooter unmounts.
    await wrapper.setProps({ done: true })
    expect(wrapper.text()).toContain('Report sent')
  })
})
