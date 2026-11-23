import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

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

  it('takes the page’s own title, and shows nothing without an error', () => {
    const titled = mountNotice({ error: 'Not saved.', errorTitle: 'Your message was not sent.' })
    expect(titled.get('strong').text()).toBe('Your message was not sent.')
    expect(titled.text()).toContain('Not saved.')

    expect(mountNotice({}).html()).toBe('<!--v-if-->')
  })
})
