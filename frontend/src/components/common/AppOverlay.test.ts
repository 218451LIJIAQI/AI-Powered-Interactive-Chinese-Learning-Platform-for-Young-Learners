import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, nextTick, ref } from 'vue'

import AppOverlay from './AppOverlay.vue'

describe('AppOverlay', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('teleports newly opened overlays to the end of body so stacking order follows open order', async () => {
    const firstOpen = ref(false)
    const secondOpen = ref(true)

    const Host = defineComponent({
      components: { AppOverlay },
      setup() {
        return { firstOpen, secondOpen }
      },
      template: `
        <div>
          <AppOverlay :open="firstOpen" title="Confirm action" test-id="first-overlay" @close="firstOpen = false">
            <p>First mounted, opened later</p>
          </AppOverlay>
          <AppOverlay :open="secondOpen" variant="drawer" title="Detail drawer" test-id="second-overlay" @close="secondOpen = false">
            <p>Second mounted, opened earlier</p>
          </AppOverlay>
        </div>
      `,
    })

    const wrapper = mount(Host, { attachTo: document.body, global: { stubs: { IconGlyph: true } } })
    await nextTick()

    expect(document.body.querySelector('[data-testid="first-overlay"]')).toBeNull()
    expect(document.body.querySelector('[data-testid="second-overlay"]')).not.toBeNull()

    // Opening the earlier-mounted overlay while the drawer is open must place it after the drawer in DOM order.
    firstOpen.value = true
    await nextTick()

    const overlays = Array.from(document.body.querySelectorAll('.overlay-panel')).map((el) =>
      el.getAttribute('data-testid'),
    )
    expect(overlays).toEqual(['second-overlay', 'first-overlay'])

    wrapper.unmount()
  })

  it('reclaims focus onto the overlay panel when busy disables the active button', async () => {
    const wrapper = mount(AppOverlay, {
      attachTo: document.body,
      props: { open: true, title: 'Save changes', busy: false, testId: 'busy-overlay' },
      slots: {
        footer: '<button type="button" data-testid="confirm-btn">Confirm</button>',
      },
      global: { stubs: { IconGlyph: true } },
    })
    await nextTick()

    const panel = document.body.querySelector<HTMLElement>('[data-testid="busy-overlay"]')!
    const confirmBtn = document.body.querySelector<HTMLButtonElement>('[data-testid="confirm-btn"]')!
    confirmBtn.focus()
    expect(document.activeElement).toBe(confirmBtn)

    // Simulate disabling the focused button as busy flips to true (browsers drop focus to document.body).
    confirmBtn.disabled = true
    confirmBtn.blur()
    await wrapper.setProps({ busy: true })
    await nextTick()

    expect(document.activeElement).toBe(panel)
    wrapper.unmount()
  })
})
