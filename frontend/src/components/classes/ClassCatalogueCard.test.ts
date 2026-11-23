import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { routerLinkStub } from '@/test/helpers'
import type { ClassCard } from '@/types/classes'
import { nameTint } from '@/utils/nameTint'

import ClassCatalogueCard from './ClassCatalogueCard.vue'

function card(overrides: Partial<ClassCard> = {}): ClassCard {
  return {
    id: 4,
    title: 'Travel Mandarin',
    description: 'Get around a Chinese city: tickets, directions and hotel check-in.',
    topicName: 'Travel',
    bestAfterUnit: 3,
    lessonCount: 5,
    ...overrides,
  }
}

function mountCard(value: ClassCard) {
  return mount(ClassCatalogueCard, {
    props: { card: value, to: { name: 'class-detail', params: { classId: value.id } } },
    global: { stubs: { RouterLink: routerLinkStub } },
  })
}

describe('ClassCatalogueCard', () => {
  it('leads with the class cover in its own tint, then theme, title, what it is about and the meta', () => {
    const wrapper = mountCard(card())

    const cover = wrapper.get('.class-catalogue-cover')
    expect(wrapper.get('.class-catalogue-heading').classes()).toContain(`tint-${nameTint('Travel Mandarin')}`)
    expect(cover.attributes('aria-hidden')).toBe('true')
    expect(cover.get('.class-avatar').text()).toBe('TM')
    expect(wrapper.get('.tag').text()).toBe('Travel')
    expect(wrapper.get('h3').text()).toBe('Travel Mandarin')
    expect(wrapper.get('[data-testid="class-card-description"]').text()).toBe(
      'Get around a Chinese city: tickets, directions and hotel check-in.',
    )
    expect(wrapper.get('.class-catalogue-card-meta').text()).toBe('5 lessons · best after Unit 3')
  })

  it('leaves the description out when the class has none', () => {
    expect(
      mountCard(card({ description: null }))
        .find('[data-testid="class-card-description"]')
        .exists(),
    ).toBe(false)
  })

  it('keeps a class in one tint wherever it shows', () => {
    expect(nameTint('Travel Mandarin')).toBe(nameTint('  Travel Mandarin '))
  })
})
