import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

import type { ToastAction } from '@/composables/useToast'

import AppToast from './AppToast.vue'

function makeAction(): ToastAction & {
  run: ReturnType<typeof vi.fn<() => void>>
  hold: ReturnType<typeof vi.fn<() => void>>
  release: ReturnType<typeof vi.fn<() => void>>
} {
  return {
    label: 'Undo',
    run: vi.fn(),
    hold: vi.fn(),
    release: vi.fn(),
  }
}

describe('AppToast', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('holds on pointer hover and releases on pointer leave when not focused', async () => {
    const action = makeAction()
    const wrapper = mount(AppToast, {
      attachTo: document.body,
      props: { message: 'Archived.', action },
      global: { stubs: { IconGlyph: true } },
    })

    const toast = document.body.querySelector<HTMLElement>('[data-testid="toast"]')!
    toast.dispatchEvent(new MouseEvent('mouseenter'))
    expect(action.hold).toHaveBeenCalledTimes(1)

    toast.dispatchEvent(new MouseEvent('mouseleave'))
    expect(action.release).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('keeps holding across focusout while hovered or moving focus within the toast, and releases on leaving both', async () => {
    const action = makeAction()
    const wrapper = mount(AppToast, {
      attachTo: document.body,
      props: { message: 'Archived.', action },
      global: { stubs: { IconGlyph: true } },
    })

    const toast = document.body.querySelector<HTMLElement>('[data-testid="toast"]')!
    const button = toast.querySelector<HTMLButtonElement>('.toast-action')!

    // Focus inside the toast holds the timer without marking it hovered.
    toast.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    expect(action.hold).toHaveBeenCalledTimes(1)

    // Moving focus to another child inside the toast does not release.
    toast.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: button }))
    expect(action.release).not.toHaveBeenCalled()

    // Leaving focus when not hovered releases immediately.
    toast.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: document.body }))
    expect(action.release).toHaveBeenCalledTimes(1)

    // When hovered, focusout keeps holding until mouseleave.
    toast.dispatchEvent(new MouseEvent('mouseenter'))
    toast.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: document.body }))
    expect(action.release).toHaveBeenCalledTimes(1)
    toast.dispatchEvent(new MouseEvent('mouseleave'))
    expect(action.release).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })

  it('clears hovered state when an action toast is replaced in place by a plain toast', async () => {
    const first = makeAction()
    const second = makeAction()
    const wrapper = mount(AppToast, {
      attachTo: document.body,
      props: { message: 'First action.', action: first },
      global: { stubs: { IconGlyph: true } },
    })

    const toast = document.body.querySelector<HTMLElement>('[data-testid="toast"]')!
    toast.dispatchEvent(new MouseEvent('mouseenter'))
    expect(first.hold).toHaveBeenCalledTimes(1)

    // Replace with a non-action toast (pointer-events becomes none, so mouseleave never fires).
    await wrapper.setProps({ message: 'Plain notice.', action: null })
    await nextTick()

    // Later action toast must NOT inherit the stale hovered state.
    await wrapper.setProps({ message: 'Second action.', action: second })
    await nextTick()
    expect(second.hold).not.toHaveBeenCalled()

    // Clicking the action button invokes run().
    document.body.querySelector<HTMLButtonElement>('.toast-action')!.click()
    expect(second.run).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
})
