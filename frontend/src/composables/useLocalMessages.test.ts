import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { createI18n } from 'vue-i18n'

import { useLocalMessages } from './useLocalMessages'

describe('useLocalMessages', () => {
  it('resolves local and global keys and warns only about a key that is missing everywhere', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const i18n = createI18n({
      legacy: false,
      locale: 'ms',
      fallbackLocale: 'en',
      messages: { en: { nav: { help: 'Help' } }, ms: { nav: { help: 'Bantuan' } } },
    })
    const Probe = defineComponent({
      setup() {
        const { t } = useLocalMessages({ page: { en: { hi: 'Hi' }, ms: { hi: 'Hai' }, 'zh-CN': { hi: '你好' } } })
        return () => h('p', `${t('page.hi')} ${t('nav.help')} ${t('nowhere.key')}`)
      },
    })

    const wrapper = mount(Probe, { global: { plugins: [i18n] } })

    expect(wrapper.text()).toBe('Hai Bantuan nowhere.key')
    const warnings = warn.mock.calls.map((call) => String(call[0]))
    expect(warnings.filter((text) => text.includes("'nav.help'"))).toEqual([])
    expect(warnings.some((text) => text.includes("'nowhere.key'"))).toBe(true)
  })
})
