import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { deferred } from '@/test/helpers'
import { answerCheck, hintSettings, practiceItem, practiceResult } from '@/test/learnFixtures'
import type { AnswerCheck } from '@/types/learn'

import { hintStep, matchingSet, mountPlayer } from './practicePlayerTestHelpers'

const api = vi.hoisted(() => ({ check: vi.fn(), complete: vi.fn(), hint: vi.fn() }))
const hintApi = vi.hoisted(() => ({ settings: vi.fn() }))

vi.mock('@/api/learn', () => ({ practiceApi: api, hintApi }))

import MatchingPlayer from './MatchingPlayer.vue'

describe('MatchingPlayer', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    Object.values(hintApi).forEach((fn) => fn.mockReset())
    setAccessToken(null)
    hintApi.settings.mockResolvedValue(hintSettings({ mode: 'OFF' }))
    api.complete.mockResolvedValue(practiceResult({ type: 'MATCHING' }))
  })

  it('numbers each pair, checks every pair on the server and then finishes the set', async () => {
    const set = {
      ...matchingSet,
      items: [...matchingSet.items, practiceItem('3', { hanzi: '学生', pinyin: 'xuésheng' })],
      matchingChoices: [...matchingSet.matchingChoices, 'student'],
    }
    api.check
      .mockResolvedValueOnce(answerCheck({ correct: false, correctAnswer: 'mum' }))
      .mockResolvedValueOnce(answerCheck({ correct: false, correctAnswer: 'dad' }))
      .mockResolvedValueOnce(answerCheck({ correct: true, correctAnswer: 'student' }))
    api.complete.mockResolvedValue(
      practiceResult({ type: 'MATCHING', correctCount: 1, itemCount: 3, allCorrect: false }),
    )
    const wrapper = await mountPlayer(MatchingPlayer, set)

    expect(wrapper.get('[data-testid="matching-check"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="word-1"]').trigger('click')
    await wrapper.get('[data-testid="meaning-mum"]').trigger('click')
    await wrapper.get('[data-testid="word-2"]').trigger('click')
    await wrapper.get('[data-testid="meaning-mum"]').trigger('click')
    // Pairing a meaning again moves it to the new word.
    expect(wrapper.text()).toContain('1 of 3 paired')
    await wrapper.get('[data-testid="word-1"]').trigger('click')
    await wrapper.get('[data-testid="meaning-dad"]').trigger('click')
    expect(wrapper.get('[data-testid="meaning-dad"]').attributes('aria-label')).toBe('dad, paired with 妈妈')
    await wrapper.get('[data-testid="word-3"]').trigger('click')
    await wrapper.get('[data-testid="meaning-student"]').trigger('click')

    await wrapper.get('[data-testid="matching-check"]').trigger('click')
    await flushPromises()
    expect(api.check).toHaveBeenCalledWith('MATCHING', 9, { itemKey: '1', answer: 'dad' })
    expect(api.check).toHaveBeenCalledWith('MATCHING', 9, { itemKey: '2', answer: 'mum' })
    expect(api.check).toHaveBeenCalledWith('MATCHING', 9, { itemKey: '3', answer: 'student' })
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('1 of 3 pairs right')
    expect(wrapper.get('[data-testid="word-1"]').classes()).toContain('is-wrong')
    expect(wrapper.get('[data-testid="word-2"]').classes()).toContain('is-wrong')
    expect(wrapper.get('[data-testid="word-3"]').classes()).toContain('is-correct')

    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()
    expect(api.complete).toHaveBeenCalledWith(
      'MATCHING',
      9,
      [
        { itemKey: '1', answer: 'dad' },
        { itemKey: '2', answer: 'mum' },
        { itemKey: '3', answer: 'student' },
      ],
      undefined,
    )
    expect(wrapper.emitted('finished')).toHaveLength(1)
    expect(wrapper.emitted('finished')![0]![1]).toBe('妈妈 dad · 爸爸 mum · 学生 student')
  })

  it('Reset takes every pair apart, then stays off but keeps the focus', async () => {
    const wrapper = await mountPlayer(MatchingPlayer, matchingSet)
    const reset = () => wrapper.get<HTMLButtonElement>('[data-testid="reset"]')
    expect(reset().attributes('aria-disabled')).toBe('true')
    await wrapper.get('[data-testid="word-1"]').trigger('click')
    await wrapper.get('[data-testid="meaning-mum"]').trigger('click')
    expect(reset().attributes('aria-disabled')).toBe('false')

    reset().element.focus()
    await reset().trigger('click')

    expect(wrapper.text()).toContain('0 of 2 paired')
    expect(reset().attributes('aria-disabled')).toBe('true')
    expect(document.activeElement).toBe(reset().element)
  })

  it('finishes the pending pair before retrying just the check that failed', async () => {
    const accepted = deferred<AnswerCheck>()
    api.check.mockReturnValueOnce(accepted.promise).mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountPlayer(MatchingPlayer, matchingSet)
    for (const [key, meaning] of [
      ['1', 'mum'],
      ['2', 'dad'],
    ]) {
      await wrapper.get(`[data-testid="word-${key}"]`).trigger('click')
      await wrapper.get(`[data-testid="meaning-${meaning}"]`).trigger('click')
    }
    const check = wrapper.get('[data-testid="matching-check"]')
    await check.trigger('click')
    await flushPromises()
    expect(check.attributes('disabled')).toBeDefined()
    accepted.resolve(answerCheck({ correct: true, correctAnswer: 'mum' }))
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)

    api.check.mockResolvedValueOnce(answerCheck({ correct: true, correctAnswer: 'dad' }))
    await check.trigger('click')
    await flushPromises()
    expect(api.check.mock.calls.map(([, , answer]) => answer.itemKey)).toEqual(['1', '2', '2'])
    expect(wrapper.get('[data-testid="check-bar"]').text()).toContain('2 of 2 pairs right')
  })

  it.each(['reset', 're-pair'])('keeps matching answers fixed during a pending check: %s', async (action) => {
    const checked = deferred<AnswerCheck>()
    api.check.mockReturnValue(checked.promise)
    const wrapper = await mountPlayer(MatchingPlayer, matchingSet)
    for (const [key, meaning] of [
      ['1', 'mum'],
      ['2', 'dad'],
    ]) {
      await wrapper.get(`[data-testid="word-${key}"]`).trigger('click')
      await wrapper.get(`[data-testid="meaning-${meaning}"]`).trigger('click')
    }
    await wrapper.get('[data-testid="matching-check"]').trigger('click')
    if (action === 'reset') await wrapper.get('[data-testid="reset"]').trigger('click')
    else {
      await wrapper.get('[data-testid="word-1"]').trigger('click')
      await wrapper.get('[data-testid="meaning-dad"]').trigger('click')
    }
    checked.resolve(answerCheck())
    await flushPromises()
    await wrapper.get('[data-testid="continue"]').trigger('click')
    await flushPromises()

    expect(api.complete).toHaveBeenCalledWith(
      'MATCHING',
      9,
      [
        { itemKey: '1', answer: 'mum' },
        { itemKey: '2', answer: 'dad' },
      ],
      undefined,
    )
    expect(wrapper.emitted('finished')![0]![1]).toBe('妈妈 mum · 爸爸 dad')
  })

  it('asks the server for one pair, the first word not paired yet: the board has one hint', async () => {
    hintApi.settings.mockResolvedValue(hintSettings({ mode: 'ON_REQUEST' }))
    api.hint.mockResolvedValueOnce(hintStep({ kind: 'PAIR', words: ['妈妈', 'mum'], ruledOut: [] }))
    const wrapper = await mountPlayer(MatchingPlayer, matchingSet)

    await wrapper.get('[data-testid="hint-show"]').trigger('click')
    await flushPromises()
    expect(api.hint).toHaveBeenCalledTimes(1)
    expect(api.hint).toHaveBeenLastCalledWith(
      'MATCHING',
      9,
      '1',
      expect.objectContaining({ runStartedAt: matchingSet.runStartedAt, runSignature: matchingSet.runSignature }),
    )
    const card = wrapper.get('[data-testid="hint-card"]')
    expect(card.text()).toContain('SparkBot hint')
    expect(card.text()).not.toMatch(/\d of \d/)
    expect(card.get('.hint-panel-note').text()).toBe('Hints never cost XP.')
    // One hint for the board: nothing more is offered.
    expect(wrapper.find('[data-testid="hint-show"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="hint-next"]').exists()).toBe(false)
  })
})
