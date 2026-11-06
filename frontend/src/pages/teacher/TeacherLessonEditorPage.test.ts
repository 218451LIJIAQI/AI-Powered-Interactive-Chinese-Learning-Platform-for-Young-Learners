import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { classesTestRouter } from '@/test/classRoutes'
import type { EditorActivity, LessonEditor } from '@/types/classes'

const api = vi.hoisted(() => ({
  get: vi.fn(),
  saveContent: vi.fn(),
  submit: vi.fn(),
  addActivity: vi.fn(),
  updateActivity: vi.fn(),
}))

vi.mock('@/api/classes', () => ({ lessonEditorApi: api }))

import TeacherLessonEditorPage from './TeacherLessonEditorPage.vue'

function activity(type: EditorActivity['type'], title: string, number: number): EditorActivity {
  return { type, id: number, title, stage: 'GUIDED', number, characters: [], pairs: [] }
}

const THREE_ACTIVITIES = [
  activity('FLASHCARDS', 'University Life Phrases', 1),
  activity('TRACING', 'Trace 学', 2),
  activity('MATCHING', 'Campus Places', 3),
]

function editor(overrides: Partial<LessonEditor> = {}): LessonEditor {
  return {
    lessonId: 11,
    classId: 7,
    classTitle: 'Mandarin for Campus Life',
    classStatus: 'PUBLISHED',
    unitId: 1,
    unitTitle: 'First Conversations',
    units: [{ id: 1, title: 'First Conversations' }],
    status: 'PUBLISHED',
    revisionStatus: 'REJECTED',
    editable: true,
    reviewNote: 'Add pinyin under the new words.',
    reviewNoteBy: 'Olivia Tan',
    reviewNoteAt: '2026-05-15T09:00:00',
    title: 'University Life',
    minutes: 14,
    summary: 'Places on campus.',
    keyPhrases: [{ chinese: '大学生', pinyin: 'dàxuéshēng', meaning: 'university student' }],
    video: null,
    activities: [],
    quizzes: [],
    checklist: { activityCount: 0, quizCount: 0, questionCount: 0, quizzesReady: false, pinyinComplete: true },
    ...overrides,
  }
}

const READY: Partial<LessonEditor> = {
  activities: THREE_ACTIVITIES,
  checklist: { activityCount: 3, quizCount: 1, questionCount: 5, quizzesReady: true, pinyinComplete: true },
}

async function mountEditor(path = '/teacher/classes/7/lessons/11') {
  const router = await classesTestRouter(path)
  const wrapper = mount(TeacherLessonEditorPage, {
    global: { plugins: [router], stubs: { teleport: true, TeacherAiDraftPanel: true } },
  })
  await flushPromises()
  return wrapper
}

describe('TeacherLessonEditorPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
  })

  it('shows that students keep the published lesson and the admin note of the update', async () => {
    api.get.mockResolvedValue(editor())
    const wrapper = await mountEditor()

    const review = wrapper.get('[data-testid="lesson-review"]').text()
    expect(review).toContain('Changes requested')
    expect(review).toContain('Students keep the published version until this is approved.')
    expect(review).toContain('Olivia Tan, May 15: Add pinyin under the new words.')
    expect(wrapper.text()).toContain('Mandarin for Campus Life · Unit 1: First Conversations')
  })

  it('has no lesson rail, no live preview and no archive', async () => {
    api.get.mockResolvedValue(editor())
    const wrapper = await mountEditor()

    expect(wrapper.find('nav.lesson-editor-rail').exists()).toBe(false)
    expect(wrapper.find('#lesson-summary').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Archive lesson')
  })

  it('saves edited content as a draft and keeps the fields it no longer shows', async () => {
    api.get.mockResolvedValue(editor())
    api.saveContent.mockResolvedValue(editor({ title: 'University Life 2' }))
    const wrapper = await mountEditor()

    const save = wrapper.get('[data-testid="save-draft"]')
    expect(save.attributes('disabled')).toBeDefined()
    await wrapper.get('#lesson-title').setValue('University Life 2')
    expect(save.attributes('disabled')).toBeUndefined()

    await save.trigger('click')
    await flushPromises()

    expect(api.saveContent).toHaveBeenCalledWith(11, {
      title: 'University Life 2',
      unitId: 1,
      minutes: 14,
      summary: 'Places on campus.',
      keyPhrases: [{ chinese: '大学生', pinyin: 'dàxuéshēng', meaning: 'university student' }],
    })
    expect(wrapper.text()).toContain('Your changes were saved.')
  })

  it('keeps Submit disabled with the reason until the checklist is complete', async () => {
    api.get.mockResolvedValue(editor())
    const wrapper = await mountEditor()

    expect(wrapper.get('[data-testid="open-submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Add practice and a quiz to submit')

    api.get.mockResolvedValue(
      editor({
        activities: [THREE_ACTIVITIES[0]!],
        checklist: { activityCount: 1, quizCount: 1, questionCount: 5, quizzesReady: true, pinyinComplete: true },
      }),
    )
    const oneActivity = await mountEditor()
    expect(oneActivity.text()).toContain('Add all 3 practice activities to submit')
  })

  it('does not offer to submit a published lesson with nothing changed', async () => {
    api.get.mockResolvedValue(editor({ ...READY, revisionStatus: null, reviewNote: null }))
    const wrapper = await mountEditor()

    expect(wrapper.get('[data-testid="open-submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Change something to submit an update')
  })

  it('submits from the checklist and turns read-only', async () => {
    api.get.mockResolvedValue(editor(READY))
    api.submit.mockResolvedValue(editor({ ...READY, revisionStatus: 'SUBMITTED', editable: false, reviewNote: null }))
    const wrapper = await mountEditor()

    await wrapper.get('[data-testid="open-submit"]').trigger('click')
    const dialog = wrapper.get('[data-testid="submit-dialog"]')
    expect(dialog.text()).toContain('Submit "University Life" for review?')
    expect(dialog.text()).toContain('All 3 practice activities')
    expect(dialog.text()).toContain('published version')

    await dialog.get('[data-testid="submit-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(api.submit).toHaveBeenCalledWith(11)
    expect(wrapper.text()).toContain('Submitted for review: University Life.')
    expect(wrapper.text()).toContain('Read-only while in review')
  })

  it('lists exactly the three activities and creates a missing one from its Edit', async () => {
    api.get.mockResolvedValue(editor({ activities: [THREE_ACTIVITIES[0]!] }))
    api.addActivity.mockResolvedValue(editor({ activities: THREE_ACTIVITIES.slice(0, 2) }))
    const wrapper = await mountEditor('/teacher/classes/7/lessons/11/activities')

    const edits = wrapper.findAll('button').filter((button) => button.text() === 'Edit')
    expect(edits).toHaveLength(3)
    expect(wrapper.text()).toContain('University Life Phrases')
    expect(wrapper.text()).toContain('Not set up yet')
    expect(wrapper.text()).not.toContain('Add activity')

    await edits[1]!.trigger('click')
    const drawer = wrapper.get('[data-testid="activity-drawer"]')
    expect(drawer.text()).toContain('Activity 2 of 3 in this lesson')
    await drawer.get('[data-testid="activity-title"]').setValue('Trace 学')
    await drawer.get('form').trigger('submit')
    await flushPromises()

    expect(api.addActivity).toHaveBeenCalledWith(
      11,
      expect.objectContaining({ type: 'TRACING', title: 'Trace 学', stage: 'GUIDED' }),
    )
    expect(api.updateActivity).not.toHaveBeenCalled()
  })
})
