import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CjkText from './CjkText.vue'

describe('CjkText', () => {
  it('wraps Chinese runs in zh-CN spans and keeps Pinyin / English as plain text', () => {
    const wrapper = mount({
      components: { CjkText },
      template: '<p><CjkText text="你好吗？(nǐ hǎo ma?) - How are you?" /></p>',
    })

    const spans = wrapper.findAll('span[lang="zh-CN"]')
    expect(spans).toHaveLength(1)
    expect(spans[0]?.text()).toBe('你好吗？')
    expect(spans[0]?.classes()).toContain('cjk-text')
    expect(wrapper.text()).toBe('你好吗？(nǐ hǎo ma?) - How are you?')
  })

  it('renders nothing for empty text', () => {
    const wrapper = mount({ components: { CjkText }, template: '<p><CjkText :text="null" /></p>' })
    expect(wrapper.text()).toBe('')
    expect(wrapper.findAll('span')).toHaveLength(0)
  })
})
