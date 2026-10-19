import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'

import { learningContextMessages } from '@/plugins/learningContextMessages'

import LearningBreadcrumb from './LearningBreadcrumb.vue'
import LinkifiedText from './LinkifiedText.vue'

function plugins(locale = 'en') {
  const i18n = createI18n({ legacy: false, locale, fallbackLocale: 'en', messages: { en: {}, ms: {}, 'zh-CN': {} } })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      { path: '/student/learn', name: 'student-learn', component: { template: '<div />' } },
    ],
  })
  return [i18n, router]
}

describe('LearningBreadcrumb (FR-LRN-02)', () => {
  it('shows course, module and lesson with kind labels and marks the lesson as the current page', () => {
    const wrapper = mount(LearningBreadcrumb, {
      props: {
        items: [
          { kind: 'section', label: 'Learn', to: { name: 'student-learn' } },
          { kind: 'course', label: 'Mandarin for Beginners' },
          { kind: 'module', label: 'Unit 1: First Conversations' },
          { kind: 'module', label: '   ' },
          { kind: 'lesson', label: '你好 Greetings' },
        ],
      },
      global: { plugins: plugins() },
    })

    const items = wrapper.findAll('[data-testid="learning-breadcrumb-item"]')
    expect(items).toHaveLength(4)
    expect(items[1]?.text()).toContain('Course')
    expect(items[1]?.text()).toContain('Mandarin for Beginners')
    expect(items[2]?.text()).toContain('Module')
    expect(wrapper.find('a').attributes('href')).toBe('/student/learn')
    const current = wrapper.find('[aria-current="page"]')
    expect(current.text()).toContain('Greetings')
    expect(current.find('[lang="zh-CN"]').text()).toBe('你好')
    expect(wrapper.find('nav').attributes('aria-label')).toBe(learningContextMessages.en.breadcrumb)
  })

  it('uses the interface language for the kind labels', () => {
    const wrapper = mount(LearningBreadcrumb, {
      props: { items: [{ kind: 'module', label: 'Unit 1' }] },
      global: { plugins: plugins('zh-CN') },
    })
    expect(wrapper.text()).toContain(learningContextMessages['zh-CN'].module)
  })
})

describe('LinkifiedText (FR-CONT-05)', () => {
  it('renders safe URLs as new-tab links with noopener noreferrer and never renders HTML', () => {
    const wrapper = mount(LinkifiedText, {
      props: { text: 'Audio: https://example.com/a <b>bold</b> 谢谢' },
      global: { plugins: plugins() },
    })
    const link = wrapper.get('a')
    expect(link.attributes('href')).toBe('https://example.com/a')
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')).toBe('noopener noreferrer')
    expect(wrapper.find('b').exists()).toBe(false)
    expect(wrapper.text()).toContain('<b>bold</b>')
    expect(wrapper.find('[lang="zh-CN"]').text()).toBe('谢谢')
  })

  it('has key parity for the learner context strings in every language', () => {
    const englishKeys = Object.keys(learningContextMessages.en).sort()
    for (const locale of ['ms', 'zh-CN'] as const) {
      expect(Object.keys(learningContextMessages[locale]).sort()).toEqual(englishKeys)
    }
  })
})
