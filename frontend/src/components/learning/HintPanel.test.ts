import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, onTestFinished, vi } from 'vitest'

import { visibleElementRects } from '@/test/helpers'

const aiHelperApi = vi.hoisted(() => ({ report: vi.fn() }))

vi.mock('@/api/ai', () => ({ aiHelperApi }))

import HintPanel from './HintPanel.vue'

describe('HintPanel', () => {
  beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, 'getClientRects').mockReturnValue(visibleElementRects())
    aiHelperApi.report.mockReset()
    aiHelperApi.report.mockResolvedValue(undefined)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('reports a hint as feature HINT with hint-worded reasons and the page pathname', async () => {
    const originalUrl = window.location.href
    onTestFinished(() => window.history.replaceState(null, '', originalUrl))
    window.history.replaceState(null, '', '/student/practice/listening?set=12&from=invitation')

    const wrapper = mount(HintPanel, {
      attachTo: document.body,
      props: {
        variant: 'practice',
        text: 'The answer starts with the sound “m…”.',
        offered: false,
        loading: false,
        error: null,
        footnote: 'Hints never cost XP.',
        reportContext: 'LISTENING #12 / 0',
        hintKey: 'step-1',
        reported: false,
      },
      global: { stubs: { IconGlyph: true } },
    })
    await wrapper.get('[data-testid="hint-report"]').trigger('click')
    await flushPromises()

    const dialog = document.body.querySelector('[data-testid="hint-report-dialog"]')!
    expect(dialog.querySelector('h2')!.textContent).toBe('Report this hint')
    expect(dialog.textContent).toContain('The question and this hint are attached to the report.')
    const select = dialog.querySelector<HTMLSelectElement>('select')!
    expect([...select.options].map((option) => option.textContent?.trim())).toEqual([
      'Hint seems wrong',
      'Hint is unclear',
      'Unsafe or inappropriate',
      'Other',
    ])

    select.value = 'UNCLEAR_EXPLANATION'
    select.dispatchEvent(new Event('change'))
    const note = dialog.querySelector<HTMLTextAreaElement>('textarea')!
    note.value = '  The word starts with “b”.  '
    note.dispatchEvent(new Event('input'))
    dialog
      .querySelector<HTMLFormElement>('[data-testid="hint-report-form"]')!
      .dispatchEvent(new Event('submit', { cancelable: true }))
    await flushPromises()

    expect(aiHelperApi.report).toHaveBeenCalledExactlyOnceWith({
      featureKey: 'HINT',
      reasonCategory: 'UNCLEAR_EXPLANATION',
      description: 'The word starts with “b”.',
      promptSnapshot: 'LISTENING #12 / 0 · /student/practice/listening',
      aiResponseSnapshot: 'The answer starts with the sound “m…”.',
    })
    expect(wrapper.emitted('reported')).toHaveLength(1)
    wrapper.unmount()
  })

  it('keeps a persistent polite status region and closes the report dialog when hidden', async () => {
    const wrapper = mount(HintPanel, {
      attachTo: document.body,
      props: {
        variant: 'practice',
        visible: true,
        text: 'Try tone 3.',
        offered: false,
        loading: false,
        error: null,
        footnote: 'Hints never cost XP.',
        reportContext: 'LISTENING #12 / 0',
        hintKey: 'step-1',
        reported: false,
      },
      global: { stubs: { IconGlyph: true } },
    })

    expect(wrapper.get('[role="status"]').text()).toBe('Try tone 3.')
    await wrapper.get('[data-testid="hint-report"]').trigger('click')
    await flushPromises()
    expect(document.body.querySelector('[data-testid="hint-report-dialog"]')).not.toBeNull()

    await wrapper.setProps({ visible: false })
    await flushPromises()
    expect(document.body.querySelector('[data-testid="hint-report-dialog"]')).toBeNull()
    expect(wrapper.get('[role="status"]').text()).toBe('')

    await wrapper.setProps({ visible: true })
    await flushPromises()
    expect(document.body.querySelector('[data-testid="hint-report-dialog"]')).toBeNull()
    wrapper.unmount()
  })
})
