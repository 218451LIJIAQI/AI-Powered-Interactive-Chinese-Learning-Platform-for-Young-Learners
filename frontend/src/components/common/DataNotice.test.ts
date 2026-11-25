import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import DataNotice from './DataNotice.vue'

function mountNotice(props: Record<string, unknown>) {
  return mount(DataNotice, { props, global: { stubs: { IconGlyph: true } } })
}

describe('DataNotice', () => {
  it('titles a refusal of the server neutrally, not as a connection problem', () => {
    const notice = mountNotice({ error: 'This support request is already solved.' })

    expect(notice.attributes('role')).toBe('alert')
    expect(notice.get('strong').text()).toBe('Something went wrong')
    expect(notice.text()).not.toMatch(/connection/i)
  })

  it('takes the page’s own title, renders an optional retry button, and shows nothing without an error', async () => {
    const onRetry = vi.fn()
    const titled = mountNotice({ error: 'Not saved.', errorTitle: 'Your message was not sent.', onRetry })
    expect(titled.get('strong').text()).toBe('Your message was not sent.')
    expect(titled.text()).toContain('Not saved.')

    await titled.get('button').trigger('click')
    expect(onRetry).toHaveBeenCalledTimes(1)

    expect(mountNotice({}).html()).toBe('<!--v-if-->')
  })
})
