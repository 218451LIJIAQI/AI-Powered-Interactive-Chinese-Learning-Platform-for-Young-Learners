import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { deferred } from '@/test/helpers'
import { draftLesson, editorActivity } from '@/test/lessonEditorFixtures'
import type { LessonEditor } from '@/types/adminClasses'

const api = vi.hoisted(() => ({ addActivity: vi.fn(), updateActivity: vi.fn() }))

vi.mock('@/api/adminClasses', () => ({ lessonEditorApi: api }))

import LessonActivitiesPanel from './LessonActivitiesPanel.vue'

function mountPanel(editor: LessonEditor) {
  return mount(LessonActivitiesPanel, { props: { editor }, global: { stubs: { teleport: true } } })
}

/** Each slot as "number title meta button". */
const slotTexts = (wrapper: ReturnType<typeof mountPanel>) =>
  wrapper
    .findAll('[data-testid="activity-slot"]')
    .map((slot) =>
      [
        slot.get('.authoring-tile').text(),
        slot.get('.row-list-title').text(),
        slot.get('.row-list-meta').text(),
        ...slot.findAll('button').map((button) => button.text()),
      ].join(' '),
    )

describe('LessonActivitiesPanel', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
  })

  it('lists the 3 fixed activities of a new lesson, each still to add (A3.15)', () => {
    const wrapper = mountPanel(draftLesson())

    expect(wrapper.text()).toContain('Every lesson has these 3 activities, in this order.')
    expect(slotTexts(wrapper)).toEqual([
      '1 Flashcards Not added yet Add',
      '2 Tracing Not added yet Add',
      '3 Matching Not added yet Add',
    ])
    expect(wrapper.find('.state-panel').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Add activity')
    expect(wrapper.get('[data-testid="add-activity-2"]').attributes('aria-label')).toBe('Add activity 2 · Tracing')
  })

  it('adds the activity of the slot it opens from, with that type (A3.16, A3.17)', async () => {
    const added = draftLesson({ activities: [editorActivity('FLASHCARDS', 1, 'Hawker Food Words')] })
    api.addActivity.mockResolvedValue(added)
    const wrapper = mountPanel(draftLesson())

    await wrapper.get('[data-testid="add-activity-1"]').trigger('click')
    const drawer = wrapper.get('[data-testid="activity-drawer"]')
    expect(drawer.get('h2').text()).toBe('Activity 1 · Flashcards')
    await drawer.get('[data-testid="activity-title"]').setValue('Hawker Food Words')
    await drawer.get('form').trigger('submit')
    await flushPromises()

    expect(api.addActivity).toHaveBeenCalledWith(21, {
      type: 'FLASHCARDS',
      title: 'Hawker Food Words',
      // The card the drawer starts with was left blank.
      cards: [],
    })
    expect(wrapper.emitted('updated')?.[0]).toEqual([added])
    expect(wrapper.emitted('notice')?.[0]).toEqual(['Activity saved.'])
    expect(wrapper.find('[data-testid="activity-drawer"]').exists()).toBe(false)

    await wrapper.setProps({ editor: added })
    expect(slotTexts(wrapper)).toEqual([
      '1 Hawker Food Words Flashcards Edit',
      '2 Tracing Not added yet Add',
      '3 Matching Not added yet Add',
    ])
  })

  it('edits an added activity in its own slot', async () => {
    const activities = [
      editorActivity('FLASHCARDS', 1, 'University Life Phrases'),
      editorActivity('TRACING', 2, 'Trace 学 and 生'),
      editorActivity('MATCHING', 3, 'Campus Places'),
    ]
    activities[1]!.characters = [{ character: '学', pinyin: 'xué', meaning: 'study' }]
    api.updateActivity.mockResolvedValue(draftLesson({ activities }))
    const wrapper = mountPanel(draftLesson({ activities }))

    await wrapper.get('button[aria-label="Edit Trace 学 and 生"]').trigger('click')
    const drawer = wrapper.get('[data-testid="activity-drawer"]')
    expect(drawer.get('h2').text()).toBe('Activity 2 · Tracing')
    await drawer.get('form').trigger('submit')
    await flushPromises()

    expect(api.updateActivity).toHaveBeenCalledWith(21, 'TRACING', 42, {
      type: 'TRACING',
      title: 'Trace 学 and 生',
      characters: [{ character: '学', pinyin: 'xué', meaning: 'study' }],
    })
  })

  it('keeps the drawer open with the reason when the save is refused', async () => {
    api.addActivity.mockRejectedValue({
      response: { status: 400, data: { message: 'A matching activity needs at least 2 pairs.' } },
    })
    const wrapper = mountPanel(draftLesson())

    await wrapper.get('[data-testid="add-activity-3"]').trigger('click')
    await wrapper.get('[data-testid="activity-title"]').setValue('Campus Places')
    await wrapper.get('[data-testid="activity-drawer"] form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="activity-drawer"] [role="alert"]').text()).toBe(
      'A matching activity needs at least 2 pairs.',
    )
    expect(wrapper.emitted('updated')).toBeUndefined()
  })

  it('shows the slots of a read-only lesson without Add or Edit', () => {
    const wrapper = mountPanel(
      draftLesson({ activities: [editorActivity('FLASHCARDS', 1, 'Hawker Food Words')], editable: false }),
    )

    expect(slotTexts(wrapper)).toEqual([
      '1 Hawker Food Words Flashcards',
      '2 Tracing Not added yet',
      '3 Matching Not added yet',
    ])
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('ignores an in-flight activity save that settles after a lesson change', async () => {
    const pending = deferred<LessonEditor>()
    api.addActivity.mockReturnValueOnce(pending.promise)
    const wrapper = mountPanel(draftLesson({ lessonId: 21 }))

    await wrapper.get('[data-testid="add-activity-1"]').trigger('click')
    await wrapper.get('[data-testid="activity-title"]').setValue('Old Lesson Cards')
    await wrapper.get('[data-testid="activity-drawer"] form').trigger('submit')
    expect(wrapper.emitted('saving')).toEqual([[true]])

    await wrapper.setProps({ editor: draftLesson({ lessonId: 99 }) })
    expect(wrapper.find('[data-testid="activity-drawer"]').exists()).toBe(false)
    expect(wrapper.emitted('saving')).toEqual([[true], [false]])

    pending.resolve(draftLesson({ lessonId: 21 }))
    await flushPromises()
    expect(wrapper.emitted('updated')).toBeUndefined()
    expect(wrapper.emitted('notice')).toBeUndefined()
  })

  it('releases saving state and ignores late resolution if unmounted mid-save', async () => {
    const pending = deferred<LessonEditor>()
    api.addActivity.mockReturnValueOnce(pending.promise)
    const wrapper = mountPanel(draftLesson({ lessonId: 21 }))

    await wrapper.get('[data-testid="add-activity-1"]').trigger('click')
    await wrapper.get('[data-testid="activity-title"]').setValue('Old Lesson Cards')
    await wrapper.get('[data-testid="activity-drawer"] form').trigger('submit')
    expect(wrapper.emitted('saving')).toEqual([[true]])

    wrapper.unmount()
    expect(wrapper.emitted('saving')?.at(-1)).toEqual([false])

    pending.resolve(draftLesson({ lessonId: 21 }))
    await flushPromises()
    expect(wrapper.emitted('updated')).toBeUndefined()
    expect(wrapper.emitted('notice')).toBeUndefined()
  })
})
