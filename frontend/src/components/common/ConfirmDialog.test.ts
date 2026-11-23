import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, KeepAlive, ref } from 'vue'

import { visibleElementRects } from '@/test/helpers'

import AppOverlay from './AppOverlay.vue'
import ConfirmDialog from './ConfirmDialog.vue'

function byTestId(id: string): HTMLElement {
  const element = document.body.querySelector<HTMLElement>(`[data-testid="${id}"]`)
  if (!element) throw new Error(`no [data-testid="${id}"]`)
  return element
}

function pressEscape(): void {
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
}

function pressTab(shiftKey = false): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key: 'Tab', shiftKey, bubbles: true, cancelable: true })
  document.dispatchEvent(event)
  return event
}

beforeEach(() => {
  vi.spyOn(HTMLElement.prototype, 'getClientRects').mockReturnValue(visibleElementRects())
})

afterEach(() => {
  document.body.innerHTML = ''
})

describe('ConfirmDialog', () => {
  it('starts on Cancel and names the action on the confirm button', async () => {
    const wrapper = mount(ConfirmDialog, {
      attachTo: document.body,
      props: {
        title: 'Archive the unit?',
        message: 'Students stop seeing it.',
        confirmLabel: 'Archive',
        tone: 'danger',
      },
    })
    await flushPromises()

    const [cancel, confirm] = Array.from(
      byTestId('confirm-dialog').querySelectorAll<HTMLButtonElement>('.overlay-footer button'),
    )
    expect(cancel.textContent?.trim()).toBe('Cancel')
    expect(confirm.textContent?.trim()).toBe('Archive')
    expect(confirm.classList).toContain('btn-danger')
    expect(document.activeElement).toBe(cancel)
    expect(byTestId('confirm-dialog').getAttribute('role')).toBe('alertdialog')
    confirm.click()
    pressEscape()
    expect(wrapper.emitted('confirm')).toHaveLength(1)
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })

  it('also backs out through the × in the header, like every dialog', async () => {
    const wrapper = mount(ConfirmDialog, {
      attachTo: document.body,
      props: { title: 'Leave Mandarin for Campus Life?', confirmLabel: 'Leave class', cancelLabel: 'Stay in class' },
    })
    await flushPromises()

    byTestId('confirm-dialog').querySelector<HTMLButtonElement>('.overlay-header button[aria-label="Close"]')?.click()
    expect(wrapper.emitted('cancel')).toHaveLength(1)
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('puts Keep going left with the focus and the primary Leave right in a leave step', async () => {
    const wrapper = mount(ConfirmDialog, {
      attachTo: document.body,
      props: { title: 'Leave the quiz?', confirmLabel: 'Leave', cancelLabel: 'Keep going' },
    })
    await flushPromises()

    const [left, right] = Array.from(
      byTestId('confirm-dialog').querySelectorAll<HTMLButtonElement>('.overlay-footer button'),
    )
    const leave = byTestId('confirm-dialog-confirm')
    const keepGoing = byTestId('confirm-dialog-cancel')
    expect(left).toBe(keepGoing)
    expect(right).toBe(leave)
    expect(keepGoing.textContent?.trim()).toBe('Keep going')
    expect(keepGoing.classList).toContain('btn-secondary')
    expect(leave.textContent?.trim()).toBe('Leave')
    expect(leave.classList).toContain('btn-primary')
    // Enter on the focused button stays; only the button the title names leaves.
    expect(document.activeElement).toBe(keepGoing)

    keepGoing.click()
    expect(wrapper.emitted('cancel')).toHaveLength(1)
    expect(wrapper.emitted('confirm')).toBeUndefined()
    leave.click()
    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })

  it('cannot be dismissed while its action runs', async () => {
    const wrapper = mount(ConfirmDialog, {
      attachTo: document.body,
      props: { title: 'Delete the check?', confirmLabel: 'Delete', busy: true, error: 'Could not delete.' },
    })
    await flushPromises()

    pressEscape()
    byTestId('confirm-dialog-cancel').click()
    expect(wrapper.emitted('cancel')).toBeUndefined()
    expect(byTestId('confirm-dialog-confirm').hasAttribute('disabled')).toBe(true)
    expect(byTestId('confirm-dialog').textContent).toContain('Could not delete.')
  })
})

describe('AppOverlay', () => {
  it('holds focus on the panel while every action is disabled', async () => {
    mount(ConfirmDialog, {
      attachTo: document.body,
      props: { title: 'Saving', confirmLabel: 'Save', busy: true },
    })
    await flushPromises()
    const panel = byTestId('confirm-dialog')
    expect(document.activeElement).toBe(panel)
    expect(pressTab().defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(panel)
    expect(pressTab(true).defaultPrevented).toBe(true)
  })

  it('wraps Tab around enabled tab stops and recovers focus from outside', async () => {
    mount(AppOverlay, {
      attachTo: document.body,
      props: { title: 'Edit', testId: 'editor' },
      slots: {
        default:
          '<button data-testid="first" data-autofocus>First</button><button data-testid="last">Last</button>' +
          '<button tabindex="-1">Skip</button><button disabled tabindex="0">Disabled</button>',
      },
    })
    await flushPromises()
    const first = byTestId('first')
    const last = byTestId('last')
    const close = byTestId('editor').querySelector<HTMLButtonElement>('.overlay-header button')!
    expect(document.activeElement).toBe(first)
    close.focus()
    expect(pressTab(true).defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(last)
    expect(pressTab().defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(close)

    const outside = document.createElement('button')
    document.body.append(outside)
    outside.focus()
    expect(pressTab().defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(close)
    outside.focus()
    expect(pressTab(true).defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(last)
  })

  it('does not move focus out of the top dialog when an underlying drawer closes', async () => {
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    const drawer = mount(AppOverlay, { attachTo: document.body, props: { title: 'Drawer' } })
    await flushPromises()
    mount(ConfirmDialog, { attachTo: document.body, props: { title: 'Confirm', confirmLabel: 'Save' } })
    await flushPromises()
    const focused = document.activeElement
    await drawer.setProps({ open: false })
    expect(document.activeElement).toBe(focused)
    expect(document.documentElement.classList).toContain('has-overlay-open')
  })

  it('closes only the innermost of two open overlays on Escape and keeps the page locked until both close', async () => {
    const app = document.createElement('div')
    app.id = 'app'
    document.body.append(app)
    const drawerOpen = ref(true)
    const dialogOpen = ref(true)
    const closed: string[] = []
    const Host = defineComponent({
      setup: () => () => [
        h(
          AppOverlay,
          {
            open: drawerOpen.value,
            title: 'Unit',
            variant: 'drawer',
            testId: 'drawer',
            onClose: () => {
              closed.push('drawer')
              drawerOpen.value = false
            },
          },
          () => h('button', { type: 'button' }, 'Unpublish'),
        ),
        h(ConfirmDialog, {
          open: dialogOpen.value,
          title: 'Unpublish the unit?',
          confirmLabel: 'Unpublish',
          onCancel: () => {
            closed.push('dialog')
            dialogOpen.value = false
          },
        }),
      ],
    })
    mount(Host, { attachTo: document.body })
    await flushPromises()
    expect(document.documentElement.classList).toContain('has-overlay-open')
    expect(app.hasAttribute('inert')).toBe(true)
    expect(byTestId('drawer').hasAttribute('inert')).toBe(true)
    expect(byTestId('confirm-dialog').hasAttribute('inert')).toBe(false)

    pressEscape()
    await flushPromises()
    expect(closed).toEqual(['dialog'])
    expect(document.documentElement.classList).toContain('has-overlay-open')
    expect(app.hasAttribute('inert')).toBe(true)
    expect(byTestId('drawer').hasAttribute('inert')).toBe(false)

    pressEscape()
    await flushPromises()
    expect(closed).toEqual(['dialog', 'drawer'])
    expect(document.documentElement.classList).not.toContain('has-overlay-open')
    expect(app.hasAttribute('inert')).toBe(false)
  })

  it('leaves the screen with a tab the page keeps alive, and comes back with it', async () => {
    const onTab = ref(true)
    const confirmed = vi.fn()
    const cancelled = vi.fn()
    const Tab = defineComponent(
      () => () =>
        h(ConfirmDialog, {
          title: 'Use your microphone?',
          confirmLabel: 'Allow microphone',
          onConfirm: confirmed,
          onCancel: cancelled,
        }),
    )
    const OtherTab = defineComponent(() => () => h('p', 'Other tab'))
    mount(
      defineComponent(() => () => h(KeepAlive, null, () => (onTab.value ? h(Tab) : h(OtherTab)))),
      { attachTo: document.body },
    )
    await flushPromises()
    expect(byTestId('confirm-dialog')).toBeTruthy()

    onTab.value = false
    await flushPromises()
    // Drawn in the body, it would stay over the next tab, its buttons acting in a tab no longer on screen.
    expect(document.body.querySelector('[data-testid="confirm-dialog"]')).toBeNull()
    expect(document.documentElement.classList).not.toContain('has-overlay-open')
    pressEscape()
    expect(cancelled).not.toHaveBeenCalled()

    onTab.value = true
    await flushPromises()
    expect(document.activeElement).toBe(byTestId('confirm-dialog-cancel'))
    expect(document.documentElement.classList).toContain('has-overlay-open')
    byTestId('confirm-dialog-confirm').click()
    expect(confirmed).toHaveBeenCalledOnce()
  })
})
