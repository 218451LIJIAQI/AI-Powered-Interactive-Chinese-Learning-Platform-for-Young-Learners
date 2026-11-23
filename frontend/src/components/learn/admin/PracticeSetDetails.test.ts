import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { AdminPracticeSetDetail } from '@/types/learn'
import { tracingSet } from '@/test/learnFixtures'

import PracticeSetDetails from './PracticeSetDetails.vue'

function archivedTracingSet(overrides: Partial<AdminPracticeSetDetail> = {}): AdminPracticeSetDetail {
  return tracingSet({
    status: 'ARCHIVED',
    createdBy: 'Content team',
    playedBy: 1,
    history: [
      { action: 'CREATED', actor: 'Content team', at: new Date(2026, 3, 1, 10, 15).toISOString() },
      { action: 'ARCHIVED', actor: null, at: new Date(2026, 4, 1, 14, 20).toISOString() },
    ],
    ...overrides,
  })
}

function mountDetails({
  set = archivedTracingSet(),
  busy = false,
}: { set?: AdminPracticeSetDetail; busy?: boolean } = {}) {
  return mount(PracticeSetDetails, {
    props: { set, loading: false, error: null, busy, actionError: null },
    global: { stubs: { teleport: true } },
  })
}

function footerButtons(wrapper: ReturnType<typeof mountDetails>) {
  return wrapper.findAll('.overlay-footer button')
}

describe('PracticeSetDetails', () => {
  it('counts a tracing set by its strokes and offers only Restore for an archived set', async () => {
    const wrapper = mountDetails()

    expect(wrapper.get('.overlay-subtitle').text()).toBe('Practice activity · Tracing')
    expect(wrapper.findAll('.kv-list dt, .kv-list dd').map((cell) => cell.text())).toEqual([
      'Level',
      'HSK 1',
      'Strokes',
      '13',
      'Played by',
      '1 learner',
    ])
    expect(footerButtons(wrapper).map((button) => button.text())).toEqual(['Restore'])
    expect(wrapper.get('[data-testid="set-restore"]').classes()).toContain('btn-primary')
    expect(wrapper.find('[data-testid="set-edit"]').exists()).toBe(false)

    await wrapper.get('[data-testid="set-restore"]').trigger('click')
    expect(wrapper.emitted('action')).toEqual([['restore']])
  })

  it('offers Edit and Publish for a draft set and hides Played by', async () => {
    const wrapper = mountDetails({ set: archivedTracingSet({ status: 'DRAFT' }) })

    expect(wrapper.text()).not.toContain('Played by')
    expect(footerButtons(wrapper).map((button) => button.text())).toEqual(['Edit', 'Publish'])

    await wrapper.get('[data-testid="set-edit"]').trigger('click')
    await wrapper.get('[data-testid="set-publish"]').trigger('click')
    expect(wrapper.emitted('edit')).toHaveLength(1)
    expect(wrapper.emitted('action')).toEqual([['publish']])
  })

  it('lists the history with who made each change, the platform team for a seeded one', () => {
    const wrapper = mountDetails()

    expect(wrapper.findAll('.admin-library-history .row-list-title').map((title) => title.text())).toEqual([
      'Created as a draft',
      'Archived',
    ])
    expect(wrapper.findAll('.admin-library-history .row-list-meta').map((meta) => meta.text())).toEqual([
      'Content team · 1 Apr, 10:15 AM',
      'MandarinSpark team · 1 May, 2:20 PM',
    ])
  })

  it('keeps every action waiting while one is on its way', () => {
    const wrapper = mountDetails({ set: archivedTracingSet({ status: 'PUBLISHED' }), busy: true })

    expect(footerButtons(wrapper).map((button) => button.text())).toEqual(['Archive', 'Unpublish', 'Edit'])
    expect(footerButtons(wrapper).every((button) => button.attributes('disabled') !== undefined)).toBe(true)
  })
})
