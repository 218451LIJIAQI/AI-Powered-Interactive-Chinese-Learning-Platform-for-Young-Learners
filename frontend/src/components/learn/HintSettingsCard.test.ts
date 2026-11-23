import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setAccessToken } from '@/api/http'
import { deferred } from '@/test/helpers'
import { hintSettings } from '@/test/learnFixtures'
import type { HintSettings } from '@/types/learn'

const hintApi = vi.hoisted(() => ({ settings: vi.fn(), setMode: vi.fn() }))

vi.mock('@/api/learn', () => ({ hintApi }))

import HintSettingsCard from './HintSettingsCard.vue'

async function mountCard() {
  const wrapper = mount(HintSettingsCard, { global: { stubs: { IconGlyph: true } } })
  await flushPromises()
  return wrapper
}

describe('HintSettingsCard', () => {
  beforeEach(() => {
    // A new sign-in: the shared hint settings are loaded again.
    setAccessToken(null)
    hintApi.settings.mockResolvedValue(hintSettings())
    hintApi.setMode.mockImplementation(async (mode: HintSettings['mode']) => hintSettings({ mode }))
  })

  it('says what Automatic means at the learner’s level, and that changes save at once', async () => {
    const wrapper = await mountCard()

    const card = wrapper.get('[data-testid="account-hints"]')
    expect(card.get('h2').text()).toBe('AI hints')
    expect(card.text()).toContain('SparkBot offers a hint when you’ve been stuck on a question for a while.')
    expect(wrapper.get('[data-testid="hint-rule"]').text()).toBe(
      'At your level (HSK 1): offered after about 15 seconds. Hints come later and say less as your level goes up. There are no hints in level checks. Changes save right away.',
    )
    expect(wrapper.get('[data-testid="hint-mode-automatic"]').attributes('aria-pressed')).toBe('true')
  })

  it.each([
    [
      'ON_REQUEST',
      'SparkBot gives a hint only when you ask for one.',
      'On questions with hints, you can ask for one from the start. There are no hints in level checks. Changes save right away.',
      'AI hints: only when you ask.',
    ],
    [
      'OFF',
      'SparkBot hints are off.',
      'No hints on any question. You can turn them back on here at any time. Changes save right away.',
      'AI hints are off.',
    ],
  ] as const)('explains the %s mode once it is saved, with Undo in the toast', async (mode, intro, rule, toast) => {
    const wrapper = await mountCard()

    await wrapper.get(`[data-testid="hint-mode-${mode === 'OFF' ? 'off' : 'on-request'}"]`).trigger('click')
    await flushPromises()

    expect(hintApi.setMode).toHaveBeenCalledWith(mode)
    expect(wrapper.text()).toContain(intro)
    expect(wrapper.get('[data-testid="hint-rule"]').text()).toBe(rule)
    expect(wrapper.emitted('saved')).toEqual([[toast, { label: 'Undo', run: expect.any(Function) }]])
    // The card links nowhere: Practice has no quiz to try the setting on.
    expect(wrapper.find('a').exists()).toBe(false)
  })

  it('goes back to the mode before with Undo, without offering another Undo', async () => {
    const wrapper = await mountCard()
    await wrapper.get('[data-testid="hint-mode-off"]').trigger('click')
    await flushPromises()

    const [, undo] = wrapper.emitted<[string, { label: string; run: () => void }]>('saved')![0]!
    undo.run()
    await flushPromises()

    expect(hintApi.setMode).toHaveBeenLastCalledWith('AUTOMATIC')
    expect(wrapper.get('[data-testid="hint-mode-automatic"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.emitted('saved')![1]).toEqual(['AI hints: automatic.', undefined])
  })

  it('adds the per-set limit of the level to its rule', async () => {
    hintApi.settings.mockResolvedValue(hintSettings({ perSet: 2 }))
    const wrapper = await mountCard()

    expect(wrapper.get('[data-testid="hint-rule"]').text()).toContain('Up to 2 hints per set.')
  })

  it('says at once when the setting cannot be loaded', async () => {
    hintApi.settings.mockRejectedValue(new Error('offline'))
    const wrapper = await mountCard()

    expect(wrapper.get('[role="alert"]').text()).toBe('Your hint setting could not be loaded.')
    expect(wrapper.find('.account-hints-loading').exists()).toBe(false)
  })

  it('keeps the saved mode and says so when saving fails', async () => {
    hintApi.setMode.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountCard()

    await wrapper.get('[data-testid="hint-mode-off"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('Your hint setting could not be saved. Please try again.')
    expect(wrapper.get('[data-testid="hint-mode-automatic"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.emitted('saved')).toBeUndefined()
  })

  it('ignores a saved mode that returns after another learner signs in', async () => {
    const pendingSave = deferred<HintSettings>()
    hintApi.setMode.mockReturnValueOnce(pendingSave.promise)
    const original = await mountCard()
    await original.get('[data-testid="hint-mode-off"]').trigger('click')

    setAccessToken('next-learner')
    hintApi.settings.mockResolvedValueOnce(hintSettings({ mode: 'ON_REQUEST' }))
    const replacement = await mountCard()
    pendingSave.resolve(hintSettings({ mode: 'OFF' }))
    await flushPromises()

    expect(replacement.get('[data-testid="hint-mode-on-request"]').attributes('aria-pressed')).toBe('true')
    expect(original.emitted('saved')).toBeUndefined()
  })
})
