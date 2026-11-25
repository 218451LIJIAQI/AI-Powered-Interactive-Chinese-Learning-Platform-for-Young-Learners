import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred, visibleElementRects } from '@/test/helpers'

import AiReportForm from './AiReportForm.vue'

const submitReport = vi.fn()

async function mountForm() {
  const wrapper = mount(AiReportForm, {
    attachTo: document.body,
    props: {
      title: 'Report this answer',
      attached: 'The question and answer are attached.',
      initialReason: 'INCORRECT_CONTENT',
      submitReport,
    },
    slots: {
      default:
        '<option value="INCORRECT_CONTENT">Incorrect</option>' + '<option value="UNCLEAR_EXPLANATION">Unclear</option>',
    },
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

async function submit(): Promise<void> {
  inBody<HTMLFormElement>('[data-testid="ai-report-form"]').dispatchEvent(new Event('submit', { cancelable: true }))
  await flushPromises()
}

describe('AiReportForm', () => {
  beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, 'getClientRects').mockReturnValue(visibleElementRects())
    submitReport.mockReset()
    submitReport.mockResolvedValue(undefined)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('focuses the reason select, trims the optional note and emits sent', async () => {
    const wrapper = await mountForm()

    const dialog = inBody('[data-testid="ai-report-dialog"]')
    expect(dialog.getAttribute('role')).toBe('dialog')
    expect(dialog.querySelector('h2')!.textContent).toBe('Report this answer')
    expect(dialog.textContent).toContain('An admin replies in AI Helper › Ask SparkBot › My reports.')
    const select = inBody<HTMLSelectElement>('select')
    expect(document.activeElement).toBe(select)
    expect(select.required).toBe(true)

    select.value = 'UNCLEAR_EXPLANATION'
    select.dispatchEvent(new Event('change'))
    await write('  Needs an example.  ')
    await submit()

    expect(submitReport).toHaveBeenCalledExactlyOnceWith('UNCLEAR_EXPLANATION', 'Needs an example.')
    expect(wrapper.emitted('sent')).toHaveLength(1)
    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('submits an empty note cleanly', async () => {
    await mountForm()
    await submit()

    expect(submitReport).toHaveBeenCalledExactlyOnceWith('INCORRECT_CONTENT', '')
  })

  it('keeps choices and shows an alert on failure, then clears the error on retry', async () => {
    submitReport.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountForm()
    const note = inBody<HTMLTextAreaElement>('textarea')
    await write('Wrong translation.')
    await submit()

    const error = inBody('[data-testid="ai-report-error"]')
    expect(error.textContent?.trim()).toBe('The report could not be sent. Please try again.')
    expect(error.getAttribute('role')).toBe('alert')
    expect(inBody('textarea')).toBe(note)
    expect(note.value).toBe('Wrong translation.')
    expect(inBody<HTMLButtonElement>('[data-testid="ai-report-send"]').disabled).toBe(false)
    expect(wrapper.emitted('sent')).toBeUndefined()

    await submit()
    expect(submitReport).toHaveBeenCalledTimes(2)
    expect(document.body.querySelector('[data-testid="ai-report-error"]')).toBeNull()
    expect(wrapper.emitted('sent')).toHaveLength(1)
  })

  it('closes from Cancel and Escape', async () => {
    const wrapper = await mountForm()

    inBody<HTMLButtonElement>('.overlay-footer .btn-secondary').click()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(wrapper.emitted('close')).toHaveLength(2)
  })

  it('cannot be closed or submitted twice while sending', async () => {
    const sent = deferred<void>()
    submitReport.mockReturnValue(sent.promise)
    const wrapper = await mountForm()
    await submit()
    await submit()

    expect(submitReport).toHaveBeenCalledOnce()
    const send = inBody<HTMLButtonElement>('[data-testid="ai-report-send"]')
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
