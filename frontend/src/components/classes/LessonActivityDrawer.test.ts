import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import type { EditorActivity } from '@/types/adminClasses'
import type { ClassActivityType } from '@/types/classes'

import LessonActivityDrawer from './LessonActivityDrawer.vue'

interface DrawerProps {
  type?: ClassActivityType
  activity?: EditorActivity | null
  busy?: boolean
  error?: string | null
}

function mountDrawer({ type = 'MATCHING', activity = null, busy = false, error = null }: DrawerProps = {}) {
  return mount(LessonActivityDrawer, {
    props: { type, activity, busy, error },
    global: { stubs: { teleport: true } },
  })
}

describe('LessonActivityDrawer', () => {
  it('adds the activity of its slot, with the type fixed and the rows the type needs', async () => {
    const wrapper = mountDrawer()

    expect(wrapper.get('h2').text()).toBe('Activity 3 · Matching')
    // The slot sets the type: it shows as text, with no choice to make.
    expect(wrapper.find('select').exists()).toBe(false)
    expect(wrapper.get('[data-testid="activity-type"]').text()).toBe('Matching (activity 3 of 3, fixed)')
    // Matching starts with the two pairs it needs at least.
    expect(wrapper.findAll('.lesson-rows-grid:not(.is-head)')).toHaveLength(2)
    expect(wrapper.text()).toContain('Pairs · learners match each word to its meaning')

    const save = () => wrapper.get<HTMLButtonElement>('button[type="submit"]').element
    expect(save().disabled).toBe(true)
    await wrapper.get('[data-testid="activity-title"]').setValue(' Hawker Food Words ')
    const inputs = wrapper.findAll('.lesson-rows-grid:not(.is-head) input')
    for (const [index, value] of ['鸡饭', 'jīfàn', 'chicken rice', '茶', 'chá', 'tea'].entries()) {
      await inputs[index]!.setValue(value)
    }
    expect(save().disabled).toBe(false)
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('save')?.[0]?.[0]).toEqual({
      type: 'MATCHING',
      title: 'Hawker Food Words',
      pairs: [
        { chinese: '鸡饭', pinyin: 'jīfàn', meaning: 'chicken rice' },
        { chinese: '茶', pinyin: 'chá', meaning: 'tea' },
      ],
    })
  })

  it('starts tracing with one character to trace', () => {
    const wrapper = mountDrawer({ type: 'TRACING' })

    expect(wrapper.get('h2').text()).toBe('Activity 2 · Tracing')
    expect(wrapper.findAll('.lesson-rows-grid:not(.is-head)')).toHaveLength(1)
  })

  it('retains a partially filled tracing row so the editor can correct its missing character', async () => {
    const wrapper = mountDrawer({ type: 'TRACING' })
    await wrapper.get('[data-testid="activity-title"]').setValue('Trace a character')
    await wrapper.get('.lesson-rows-grid input[aria-label*="Pinyin"]').setValue('rén')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({
      characters: [{ character: '', pinyin: 'rén', meaning: null }],
    })
  })

  it('makes flashcards from their own cards: Chinese, pinyin and meaning (A3.16)', async () => {
    const wrapper = mountDrawer({ type: 'FLASHCARDS' })

    expect(wrapper.get('h2').text()).toBe('Activity 1 · Flashcards')
    expect(wrapper.get('[data-testid="activity-type"]').text()).toBe('Flashcards (activity 1 of 3, fixed)')
    expect(wrapper.text()).toContain('Cards · flip for pinyin and meaning')
    expect(wrapper.get('.lesson-rows-grid.is-head').text()).toBe('ChinesePinyinMeaning')
    // A new activity starts with one card to fill.
    expect(wrapper.findAll('.lesson-rows-grid:not(.is-head)')).toHaveLength(1)
    await wrapper.get('[data-testid="activity-title"]').setValue('Hawker Food Words')
    await wrapper.get('.lesson-rows-head button').trigger('click')
    await wrapper.get('.lesson-rows-head button').trigger('click')
    expect(wrapper.get('.lesson-rows-head button').text()).toBe('Add card')
    const inputs = wrapper.findAll('.lesson-rows-grid:not(.is-head) input')
    for (const [index, value] of [' 鸡饭 ', 'jīfàn', 'chicken rice', '面', 'miàn', 'noodles'].entries()) {
      await inputs[index]!.setValue(value)
    }
    await wrapper.get('form').trigger('submit')

    // The third card was left blank: it is not sent.
    expect(wrapper.emitted('save')?.[0]?.[0]).toEqual({
      type: 'FLASHCARDS',
      title: 'Hawker Food Words',
      cards: [
        { chinese: '鸡饭', pinyin: 'jīfàn', meaning: 'chicken rice' },
        { chinese: '面', pinyin: 'miàn', meaning: 'noodles' },
      ],
    })
  })

  it('stops adding cards at 20, as the server does', async () => {
    const wrapper = mountDrawer({ type: 'FLASHCARDS' })

    for (let count = 1; count < 20; count++) await wrapper.get('.lesson-rows-head button').trigger('click')
    expect(wrapper.findAll('.lesson-rows-grid:not(.is-head)')).toHaveLength(20)
    expect(wrapper.find('.lesson-rows-head button').exists()).toBe(false)
  })

  it('keeps an older Flashcards activity without cards on the key phrases until cards are added', async () => {
    const activity: EditorActivity = {
      type: 'FLASHCARDS',
      id: 7,
      title: 'University Life Phrases',
      number: 1,
      characters: [],
      pairs: [],
      cards: [],
    }
    const wrapper = mountDrawer({ type: 'FLASHCARDS', activity })

    expect(wrapper.get('[data-testid="no-cards"]').text()).toBe(
      'No cards yet: learners review the lesson’s key phrases instead.',
    )
    expect(wrapper.find('.lesson-rows-box').exists()).toBe(false)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('save')?.[0]?.[0]).toEqual({
      type: 'FLASHCARDS',
      title: 'University Life Phrases',
      cards: [],
    })

    // Every card can go: the activity then falls back on the key phrases again.
    await wrapper.get('.lesson-rows-head button').trigger('click')
    expect(wrapper.find('[data-testid="no-cards"]').exists()).toBe(false)
    await wrapper.get('button[aria-label="Remove card 1"]').trigger('click')
    expect(wrapper.find('[data-testid="no-cards"]').exists()).toBe(true)
  })

  it('edits the cards of a Flashcards activity', async () => {
    const activity: EditorActivity = {
      type: 'FLASHCARDS',
      id: 7,
      title: 'Hawker Food Words',
      number: 1,
      characters: [],
      pairs: [],
      cards: [
        { chinese: '茶', pinyin: 'chá', meaning: 'tea' },
        { chinese: '辣', pinyin: 'là', meaning: 'spicy' },
      ],
    }
    const wrapper = mountDrawer({ type: 'FLASHCARDS', activity })

    expect(
      wrapper.findAll<HTMLInputElement>('.lesson-rows-grid:not(.is-head) input').map((input) => input.element.value),
    ).toEqual(['茶', 'chá', 'tea', '辣', 'là', 'spicy'])
    await wrapper.get('button[aria-label="Remove card 2"]').trigger('click')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({
      cards: [{ chinese: '茶', pinyin: 'chá', meaning: 'tea' }],
    })
  })

  it.each(['TRACING', 'MATCHING'] as const)('edits the existing %s activity', async (type) => {
    const errorHandler = vi.fn()
    // Existing stored content can omit pinyin, even though new submissions require it: the server says what to fix.
    const activity: EditorActivity = {
      type,
      id: 4,
      title: 'Existing activity',
      number: 1,
      characters: [{ character: '你', pinyin: null, meaning: null }],
      pairs: [
        { chinese: '你', pinyin: null, meaning: 'you' },
        { chinese: '好', pinyin: 'hǎo', meaning: 'good' },
      ],
      cards: [],
    }
    const wrapper = mount(LessonActivityDrawer, {
      props: { type, activity, busy: false, error: null },
      global: { stubs: { teleport: true }, config: { errorHandler } },
    })

    expect(wrapper.get<HTMLInputElement>('[data-testid="activity-title"]').element.value).toBe('Existing activity')
    await wrapper.get('form').trigger('submit')

    expect(errorHandler).not.toHaveBeenCalled()
    expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject(
      type === 'TRACING'
        ? { type, characters: [{ character: '你', pinyin: '', meaning: null }] }
        : {
            type,
            pairs: [
              { chinese: '你', pinyin: '', meaning: 'you' },
              { chinese: '好', pinyin: 'hǎo', meaning: 'good' },
            ],
          },
    )
  })

  it('shows why the save failed and waits while it runs', () => {
    const wrapper = mountDrawer({ busy: true, error: 'A matching activity needs at least 2 pairs.' })

    expect(wrapper.get('[role="alert"]').text()).toBe('A matching activity needs at least 2 pairs.')
    expect(wrapper.get<HTMLButtonElement>('button[type="submit"]').element.disabled).toBe(true)
  })
})
