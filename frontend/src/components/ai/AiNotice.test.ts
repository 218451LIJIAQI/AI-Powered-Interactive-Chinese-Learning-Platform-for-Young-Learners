import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ platformSettings: vi.fn() }))

vi.mock('@/api/services', () => ({ publicApi: { platformSettings: mocks.platformSettings } }))

import AiNotice from './AiNotice.vue'

function mountNotice(liveVideo?: boolean) {
  return mount(AiNotice, {
    props: { text: 'AI can make mistakes.', liveVideo },
    global: { stubs: { IconGlyph: true, teleport: true } },
  })
}

describe('AiNotice', () => {
  beforeEach(() => {
    mocks.platformSettings.mockReset()
  })

  it('shows the AI notice with the Responsible AI guidance beside it, as a button, not a link away', () => {
    const wrapper = mountNotice()

    expect(wrapper.text()).toContain('AI can make mistakes.')
    expect(wrapper.get('[data-testid="ai-responsible-link"]').element.tagName).toBe('BUTTON')
    expect(wrapper.find('a').exists()).toBe(false)
  })

  it('opens the seven sections of the guidance in a drawer, and closes it again (X2.22)', async () => {
    mocks.platformSettings.mockResolvedValue({ aiDisclaimerText: 'AI can make mistakes.', aiProvider: '' })
    const wrapper = mountNotice()

    await wrapper.get('[data-testid="ai-responsible-link"]').trigger('click')
    await flushPromises()

    const drawer = wrapper.get('[data-testid="ai-responsible-drawer"]')
    expect(drawer.classes()).toContain('overlay-panel--drawer')
    expect(drawer.get('.overlay-title').text()).toBe('Responsible AI guidance')
    expect(drawer.findAll('.ai-guidance-section')).toHaveLength(7)
    expect(drawer.text()).toContain('The AI text and photo service is currently unavailable.')
    expect(drawer.text()).toContain('sends the recording and the target text to Tencent Cloud')
    expect(drawer.text()).not.toContain('Tavus receives the call audio')

    await drawer.get('.overlay-header button').trigger('click')
    expect(wrapper.find('[data-testid="ai-responsible-drawer"]').exists()).toBe(false)
  })

  it('discloses live video and the text service outage independently', async () => {
    mocks.platformSettings.mockResolvedValue({ aiProvider: '' })
    const wrapper = mountNotice(true)

    await wrapper.get('[data-testid="ai-responsible-link"]').trigger('click')
    await flushPromises()

    const drawer = wrapper.get('[data-testid="ai-responsible-drawer"]')
    expect(drawer.text()).toContain(
      'Tavus receives the call audio and any recent messages from the current conversation',
    )
    expect(drawer.text()).toContain('The AI text and photo service is currently unavailable.')
    expect(drawer.text()).not.toContain('Responses API requests use store=false')
  })
})
