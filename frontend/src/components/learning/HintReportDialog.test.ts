import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, onTestFinished, vi } from 'vitest'

import { deferred, visibleElementRects } from '@/test/helpers'

const hintApi = vi.hoisted(() => ({ report: vi.fn() }))

vi.mock('@/api/learn', () => ({ hintApi }))

import HintReportDialog from './HintReportDialog.vue'

/** In the document, as the dialog is in the app: it renders in the body and keeps the focus. */
async function mountDialog() {
  const wrapper = mount(HintReportDialog, {
    attachTo: document.body,
    props: { text: 'The answer starts with the sound “m…”.', context: 'LISTENING #12 / 0' },
  })
  await flushPromises()
  return wrapper
}

function inBody<T extends HTMLElement>(selector: string): T {
  const element = document.body.querySelector<T>(selector)
  if (!element) throw new Error(`no ${selector}`)
  return element
}

async function write(text: string): Promise<void> {
  const note = inBody<HTMLTextAreaElement>('textarea')
  note.value = text
  note.dispatchEvent(new Event('input'))
  await flushPromises()
}

async function choose(reason: string): Promise<void> {
  const select = inBody<HTMLSelectElement>('select')
  select.value = reason
  select.dispatchEvent(new Event('change'))
  await flushPromises()
}

async function submit(): Promise<void> {
  inBody<HTMLFormElement>('[data-testid="hint-report-form"]').dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

describe('HintReportDialog', () => {
  beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, 'getClientRects').mockReturnValue(visibleElementRects())
    hintApi.report.mockReset()
    hintApi.report.mockResolvedValue(undefined)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('asks why, takes an optional note and sends the hint as an AI report with what it was for', async () => {
    const originalUrl = window.location.href
    onTestFinished(() => window.history.replaceState(null, '', originalUrl))
    window.history.replaceState(null, '', '/student/practice/listening?set=12&from=invitation')
    const wrapper = await mountDialog()

    const dialog = inBody('[data-testid="hint-report-dialog"]')
    expect(dialog.getAttribute('role')).toBe('dialog')
    expect(dialog.querySelector('h2')!.textContent).toBe('Report this hint')
    expect(dialog.textContent).toContain('An admin replies in Ask SparkBot › My reports.')
    expect(dialog.textContent).toContain('The question and this hint are attached to the report.')
    // The reason is required and starts on "Hint seems wrong"; the note is optional.
    const select = inBody<HTMLSelectElement>('select')
    expect(document.activeElement).toBe(select)
    expect(select.required).toBe(true)
    expect([...select.options].map((option) => option.textContent)).toEqual([
      'Hint seems wrong',
      'Hint is unclear',
      'Unsafe or inappropriate',
      'Other',
    ])
    expect(inBody(`label[for="${inBody('textarea').id}"]`).textContent).toBe('Note (optional)')
    expect([...dialog.querySelectorAll('.overlay-footer button')].map((button) => button.textContent?.trim())).toEqual([
      'Cancel',
      'Send report',
    ])

    await choose('UNCLEAR_EXPLANATION')
    await write('  The word starts with “b”.  ')
    await submit()

    expect(hintApi.report).toHaveBeenCalledExactlyOnceWith({
      reasonCategory: 'UNCLEAR_EXPLANATION',
      description: 'The word starts with “b”.',
      promptSnapshot: 'LISTENING #12 / 0 · /student/practice/listening',
      aiResponseSnapshot: 'The answer starts with the sound “m…”.',
    })
    expect(wrapper.emitted('sent')).toHaveLength(1)
    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('sends without a note', async () => {
    await mountDialog()
    await submit()

    expect(hintApi.report).toHaveBeenCalledWith(
      expect.objectContaining({ reasonCategory: 'INCORRECT_CONTENT', description: null }),
    )
  })

  it('keeps the choices and says why when the report cannot be sent, then sends it on a second try', async () => {
    hintApi.report.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountDialog()
    const note = inBody<HTMLTextAreaElement>('textarea')
    await write('It names the wrong answer.')
    await submit()

    const error = inBody('[data-testid="hint-report-error"]')
    expect(error.textContent?.trim()).toBe('The report could not be sent. Please try again.')
    expect(error.getAttribute('role')).toBe('alert')
    expect(inBody('textarea')).toBe(note)
    expect(note.value).toBe('It names the wrong answer.')
    expect(inBody<HTMLButtonElement>('[data-testid="hint-report-send"]').disabled).toBe(false)
    expect(wrapper.emitted('sent')).toBeUndefined()

    await submit()
    expect(hintApi.report).toHaveBeenCalledTimes(2)
    expect(document.body.querySelector('[data-testid="hint-report-error"]')).toBeNull()
    expect(wrapper.emitted('sent')).toHaveLength(1)
  })

  it('closes from Cancel and Escape', async () => {
    const wrapper = await mountDialog()

    inBody<HTMLButtonElement>('.overlay-footer .btn-secondary').click()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(wrapper.emitted('close')).toHaveLength(2)
  })

  it('keeps what is typed away from the page’s own shortcuts, but lets Escape and Tab through', async () => {
    const wrapper = await mountDialog()
    const pageShortcut = vi.fn()
    document.addEventListener('keydown', pageShortcut)
    onTestFinished(() => document.removeEventListener('keydown', pageShortcut))
    const note = inBody('textarea')
    const localKeys = vi.fn()
    note.addEventListener('keydown', localKeys)

    // A lesson picks answers with 1–9 and checks with Enter: none of it may act on the question behind the dialog,
    // wherever the focus is in it.
    for (const key of ['2', 'Enter', 'a']) {
      note.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }))
    }
    inBody('[data-testid="hint-report-send"]').dispatchEvent(new KeyboardEvent('keydown', { key: '1', bubbles: true }))
    inBody('[data-testid="hint-report-dialog"]').dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }),
    )
    expect(pageShortcut).not.toHaveBeenCalled()
    expect(localKeys).toHaveBeenCalledTimes(3)

    note.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))
    note.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(pageShortcut.mock.calls.map(([event]) => (event as KeyboardEvent).key)).toEqual(['Tab', 'Escape'])

    // Closed, the page has its keys back.
    wrapper.unmount()
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: '2', bubbles: true }))
    expect(pageShortcut).toHaveBeenCalledTimes(3)
  })

  it('cannot be closed or sent twice while the report is on its way', async () => {
    const sent = deferred<void>()
    hintApi.report.mockReturnValue(sent.promise)
    const wrapper = await mountDialog()
    await submit()
    await submit()

    expect(hintApi.report).toHaveBeenCalledOnce()
    const send = inBody<HTMLButtonElement>('[data-testid="hint-report-send"]')
    expect(send.disabled).toBe(true)
    expect(inBody<HTMLSelectElement>('select').disabled).toBe(true)
    expect(inBody<HTMLTextAreaElement>('textarea').disabled).toBe(true)
    expect(send.getAttribute('aria-busy')).toBe('true')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(wrapper.emitted('close')).toBeUndefined()

    sent.resolve()
    await flushPromises()
    expect(wrapper.emitted('sent')).toHaveLength(1)
  })
})
