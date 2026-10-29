import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, h, ref } from 'vue'

import AppOverlay from './AppOverlay.vue'
import ConfirmDialog from './ConfirmDialog.vue'

const mounted: VueWrapper[] = []

function mountAttached<T>(component: T, options: Record<string, unknown> = {}) {
  const wrapper = mount(component as never, { attachTo: document.body, ...options }) as VueWrapper
  mounted.push(wrapper)
  return wrapper
}

function byTestId(id: string): HTMLElement {
  const element = document.body.querySelector<HTMLElement>(`[data-testid="${id}"]`)
  if (!element) throw new Error(`no [data-testid="${id}"]`)
  return element
}

function pressEscape(): void {
  document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
}

afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  document.body.innerHTML = ''
})

describe('ConfirmDialog', () => {
  it('starts on Cancel and names the action on the confirm button', async () => {
    const wrapper = mountAttached(ConfirmDialog, {
      props: {
        title: 'Archive the unit?',
        message: 'Students stop seeing it.',
        confirmLabel: 'Archive',
        tone: 'danger',
      },
    })
    await flushPromises()

    const [cancel, confirm] = Array.from(byTestId('confirm-dialog').querySelectorAll('button'))
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

  it('makes Keep going the focused primary choice of a leave step, and Leave the confirm', async () => {
    const wrapper = mountAttached(ConfirmDialog, {
      props: { title: 'Leave the quiz?', confirmLabel: 'Leave', cancelLabel: 'Keep going', tone: 'leave' },
    })
    await flushPromises()

    const leave = byTestId('confirm-dialog-confirm')
    const keepGoing = byTestId('confirm-dialog-cancel')
    expect(leave.classList).toContain('btn-outline')
    expect(keepGoing.classList).toContain('btn-primary')
    expect(document.activeElement).toBe(keepGoing)

    leave.click()
    expect(wrapper.emitted('confirm')).toHaveLength(1)
    expect(wrapper.emitted('cancel')).toBeUndefined()
  })

  it('cannot be dismissed while its action runs', async () => {
    const wrapper = mountAttached(ConfirmDialog, {
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
  it('closes only the innermost of two open overlays on Escape and keeps the page locked until both close', async () => {
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
    mountAttached(Host)
    await flushPromises()
    expect(document.documentElement.classList).toContain('has-overlay-open')

    pressEscape()
    await flushPromises()
    expect(closed).toEqual(['dialog'])
    expect(document.documentElement.classList).toContain('has-overlay-open')

    pressEscape()
    await flushPromises()
    expect(closed).toEqual(['dialog', 'drawer'])
    expect(document.documentElement.classList).not.toContain('has-overlay-open')
  })

  it('takes the focus and Escape when it is shown with v-if (already open on mount)', async () => {
    const wrapper = mountAttached(AppOverlay, {
      props: { title: 'Report this answer' },
      slots: { default: '<textarea data-autofocus aria-label="Reason"></textarea>' },
    })
    await flushPromises()

    expect(document.activeElement?.tagName).toBe('TEXTAREA')
    pressEscape()
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
