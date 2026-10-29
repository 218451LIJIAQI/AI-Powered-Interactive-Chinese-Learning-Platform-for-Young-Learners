import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { classesTestRouter } from '@/test/classRoutes'
import type { LessonEditor } from '@/types/classes'

const api = vi.hoisted(() => ({
  get: vi.fn(),
  saveContent: vi.fn(),
  submit: vi.fn(),
}))

const classesApi = vi.hoisted(() => ({ classPage: vi.fn() }))

vi.mock('@/api/classes', () => ({ lessonEditorApi: api, teacherClassesApi: classesApi }))

import TeacherLessonEditorPage from './TeacherLessonEditorPage.vue'

function editor(overrides: Partial<LessonEditor> = {}): LessonEditor {
  return {
    lessonId: 11,
    classId: 7,
    classTitle: 'Mandarin for Beginners',
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

async function mountEditor() {
  const router = await classesTestRouter('/teacher/classes/7/lessons/11')
  const wrapper = mount(TeacherLessonEditorPage, {
    global: { plugins: [router], stubs: { teleport: true, TeacherAiDraftPanel: true } },
  })
  await flushPromises()
  return wrapper
}

describe('TeacherLessonEditorPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    classesApi.classPage.mockResolvedValue({
      id: 7,
      units: [
        {
          id: 1,
          title: 'First Conversations',
          archived: false,
          lessons: [
            { id: 11, title: 'University Life', status: 'PUBLISHED', revisionStatus: 'REJECTED' },
            { id: 12, title: 'Finding Places', status: 'DRAFT', revisionStatus: null },
          ],
        },
      ],
    })
  })

  it('shows that students keep the published lesson and the admin note of the revision', async () => {
    api.get.mockResolvedValue(editor())
    const wrapper = await mountEditor()

    expect(wrapper.text()).toContain('Published · Revision: changes requested')
    expect(wrapper.text()).toContain('Students see the published version while you fix this revision.')
    expect(wrapper.text()).toContain('Olivia Tan, May 15: Add pinyin under the new words.')
  })

  it('lists the class’s lessons beside the editor with the open lesson marked', async () => {
    api.get.mockResolvedValue(editor())
    const wrapper = await mountEditor()

    const rail = wrapper.get('nav.lesson-editor-rail')
    const links = rail.findAll('a')
    expect(links.map((link) => link.text())).toEqual([
      expect.stringContaining('University Life'),
      expect.stringContaining('Finding Places'),
    ])
    expect(links[0]!.attributes('aria-current')).toBe('page')
    expect(links[1]!.attributes('href')).toBe('/teacher/classes/7/lessons/12')
  })

  it('previews the edits as they are typed', async () => {
    api.get.mockResolvedValue(editor())
    const wrapper = await mountEditor()

    await wrapper.get('#lesson-summary').setValue('Places and routines on campus.')
    expect(wrapper.get('.lesson-editor-live').text()).toContain('Places and routines on campus.')
  })

  it('saves edited content as a draft', async () => {
    api.get.mockResolvedValue(editor())
    api.saveContent.mockResolvedValue(editor({ summary: 'Places and routines on campus.' }))
    const wrapper = await mountEditor()

    expect(wrapper.text()).toContain('All changes saved')
    await wrapper.get('#lesson-summary').setValue('Places and routines on campus.')
    expect(wrapper.text()).toContain('Unsaved changes')

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Save draft'))!
      .trigger('click')
    await flushPromises()

    expect(api.saveContent).toHaveBeenCalledWith(11, {
      title: 'University Life',
      unitId: 1,
      minutes: 14,
      summary: 'Places and routines on campus.',
      keyPhrases: [{ chinese: '大学生', pinyin: 'dàxuéshēng', meaning: 'university student' }],
    })
    expect(wrapper.text()).toContain('Your changes were saved.')
  })

  it('only submits when the checklist is complete', async () => {
    api.get.mockResolvedValue(editor())
    const wrapper = await mountEditor()

    await wrapper.get('[data-testid="open-submit"]').trigger('click')
    const dialog = wrapper.get('[data-testid="submit-dialog"]')
    expect(dialog.text()).toContain('At least one practice activity')
    expect(dialog.get('[data-testid="submit-dialog-confirm"]').attributes('disabled')).toBeDefined()

    api.get.mockResolvedValue(
      editor({
        checklist: { activityCount: 3, quizCount: 1, questionCount: 5, quizzesReady: true, pinyinComplete: true },
      }),
    )
    api.submit.mockResolvedValue(editor({ revisionStatus: 'SUBMITTED', editable: false, reviewNote: null }))
    const ready = await mountEditor()
    await ready.get('[data-testid="open-submit"]').trigger('click')
    await ready.get('[data-testid="submit-dialog-confirm"]').trigger('click')
    await flushPromises()

    expect(api.submit).toHaveBeenCalledWith(11)
    expect(ready.text()).toContain('The lesson was sent for review.')
    expect(ready.text()).toContain('Read only while the lesson is in review')
  })
})
