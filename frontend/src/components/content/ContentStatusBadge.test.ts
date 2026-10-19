import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'

import { i18n } from '@/plugins/i18n'

import ContentReviewNotice from './ContentReviewNotice.vue'
import ContentStatusBadge from './ContentStatusBadge.vue'
import LearningStageTag from './LearningStageTag.vue'

const global = { plugins: [i18n], stubs: { IconGlyph: true } }

describe('content status components', () => {
  beforeEach(() => {
    i18n.global.locale.value = 'en'
  })

  it('shows the rejection reason prominently next to a rejected badge', () => {
    const wrapper = mount(ContentStatusBadge, {
      props: { status: 'REJECTED', rejectionReason: 'Add pinyin to every example.' },
      global,
    })

    expect(wrapper.text()).toContain('Rejected')
    expect(wrapper.get('[data-testid="content-rejection-reason"]').text()).toContain('Add pinyin to every example.')
  })

  it('labels submitted items as in review and never shows an Approved state', () => {
    expect(mount(ContentStatusBadge, { props: { status: 'SUBMITTED' }, global }).text()).toBe('In review')
    const legacy = mount(ContentStatusBadge, { props: { status: 'APPROVED' }, global })
    expect(legacy.text()).toBe('Archived')
    expect(legacy.find('[data-testid="content-rejection-reason"]').exists()).toBe(false)
  })

  it('warns that saving published content sends it back for review', () => {
    const wrapper = mount(ContentReviewNotice, { props: { status: 'PUBLISHED' }, global })
    expect(wrapper.text()).toContain('Saving changes sends it back for review')
  })

  it('translates learning stage labels and hides unset stages unless asked', () => {
    i18n.global.locale.value = 'zh-CN'
    expect(mount(LearningStageTag, { props: { stage: 'GUIDED' }, global }).text()).toBe('引导练习')
    expect(
      mount(LearningStageTag, { props: { stage: null }, global })
        .find('span')
        .exists(),
    ).toBe(false)
    expect(mount(LearningStageTag, { props: { stage: null, showUnset: true }, global }).text()).toBe('未设置')
  })
})
