import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, shallowRef } from 'vue'

import { deferred } from '@/test/helpers'
import { draftLesson } from '@/test/lessonEditorFixtures'
import type { EditorQuestion, EditorQuiz, LessonEditor } from '@/types/adminClasses'
import type { AiQuizGeneratorResponse } from '@/types/api'

const api = vi.hoisted(() => ({
  get: vi.fn(),
  addQuestion: vi.fn(),
  addQuiz: vi.fn(),
  updateQuiz: vi.fn(),
  updateQuestion: vi.fn(),
  deleteQuestion: vi.fn(),
  keepQuestion: vi.fn(),
}))

vi.mock('@/api/adminClasses', () => ({ lessonEditorApi: api, adminQuizDraftApi: {} }))

import LessonQuizPanel from './LessonQuizPanel.vue'

function question(id: number, number: number, text = `Question ${number}?`, aiDraft = false): EditorQuestion {
  return {
    id,
    number,
    type: 'MULTIPLE_CHOICE',
    text,
    points: 2,
    correctAnswerText: null,
    explanation: `Explanation ${number}`,
    hint: null,
    audioUrl: null,
    imageUrl: null,
    options: [
      { id: id * 10, text: 'yes', correct: true, feedback: null },
      { id: id * 10 + 1, text: 'no', correct: false, feedback: null },
    ],
    aiDraft,
  }
}

function questions(count: number, firstId = 100): EditorQuestion[] {
  return Array.from({ length: count }, (_, index) => question(firstId + index, index + 1))
}

function lesson(quiz: Partial<EditorQuiz> | null = {}, overrides: Partial<LessonEditor> = {}): LessonEditor {
  return draftLesson({
    lessonId: 11,
    classId: 1,
    classTitle: 'Mandarin for Campus Life',
    classStatus: 'PUBLISHED',
    unitId: 1,
    unitTitle: 'Unit 1: First Conversations',
    units: [{ id: 1, title: 'Unit 1: First Conversations' }],
    title: 'University Life',
    quizzes: quiz === null ? [] : [{ id: 3, hintsAllowed: false, questionsLocked: false, questions: [], ...quiz }],
    ...overrides,
  })
}

function draft(count: number): AiQuizGeneratorResponse {
  return {
    title: 'Campus',
    status: 'SUCCESS',
    questions: Array.from({ length: count }, (_, index) => ({
      questionText: `What does word ${index + 1} mean?`,
      questionType: 'MULTIPLE_CHOICE',
      options: ['Library', 'Canteen'],
      correctAnswer: 'Library',
    })),
  }
}

function mountPanel(current: LessonEditor = lesson()) {
  return mount(LessonQuizPanel, {
    props: { editor: current },
    global: { stubs: { teleport: true, AdminQuizDraftPanel: true } },
  })
}

type Panel = ReturnType<typeof mountPanel>

const draftPanel = (wrapper: Panel) => wrapper.findComponent({ name: 'AdminQuizDraftPanel' })
const addQuestionButton = (wrapper: Panel) => wrapper.get<HTMLButtonElement>('[data-testid="add-question"]')

describe('LessonQuizPanel', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
  })

  it('starts empty with a primary Add question, and offers AI drafts for all 5 questions', async () => {
    const wrapper = mountPanel()

    const empty = wrapper.get('.state-panel')
    expect(empty.text()).toContain('Add your first question')
    expect(empty.get('[data-testid="add-first-question"]').classes()).toContain('btn-primary')
    expect(draftPanel(wrapper).props('remaining')).toBe(5)
    expect(draftPanel(wrapper).props('lessonId')).toBe(11)

    await empty.get('[data-testid="add-first-question"]').trigger('click')
    expect(wrapper.get('[role="dialog"]').text()).toContain('New question')
  })

  it('stops adding at exactly 5 questions: Add question and AI drafts wait until one is deleted', () => {
    const wrapper = mountPanel(lesson({ questions: questions(5) }))

    const add = addQuestionButton(wrapper)
    expect(add.element.disabled).toBe(true)
    // The note under the AI draft's count says why, and the disabled button points to it.
    expect(add.attributes('aria-describedby')).toBe(draftPanel(wrapper).props('countHintId'))
    expect(draftPanel(wrapper).props('remaining')).toBe(0)
  })

  it('has room for the questions a quiz still needs below 5', async () => {
    const wrapper = mountPanel(lesson({ questions: questions(4) }))

    expect(addQuestionButton(wrapper).element.disabled).toBe(false)
    expect(addQuestionButton(wrapper).attributes('aria-describedby')).toBeUndefined()
    expect(draftPanel(wrapper).props('remaining')).toBe(1)
    await addQuestionButton(wrapper).trigger('click')
    expect(wrapper.get('[role="dialog"]').text()).toContain('New question')
  })

  it('adds no more AI questions than the quiz has room for, each saved as an AI draft', async () => {
    const before = lesson({ questions: questions(4) })
    const after = lesson({ questions: [...questions(4), question(200, 5, 'What does word 1 mean?', true)] })
    api.addQuestion.mockResolvedValue(after)
    const wrapper = mountPanel(before)

    draftPanel(wrapper).vm.$emit('quiz-draft', draft(3))
    await flushPromises()

    expect(api.addQuestion).toHaveBeenCalledOnce()
    expect(api.addQuestion).toHaveBeenCalledWith(11, 3, {
      questionType: 'MULTIPLE_CHOICE',
      questionText: 'What does word 1 mean?',
      points: 2,
      correctAnswerText: 'Library',
      explanation: null,
      options: [
        { optionText: 'Library', correctOption: true },
        { optionText: 'Canteen', correctOption: false },
      ],
      aiDraft: true,
    })
    expect(wrapper.emitted('notice')?.at(-1)).toEqual(['AI questions added. Check each one before publishing.'])
    await wrapper.setProps({ editor: wrapper.emitted<[LessonEditor]>('updated')!.at(-1)![0] })
    const rows = wrapper.findAll('[data-testid="quiz-question"]')
    expect(rows.map((row) => row.find('[data-testid="ai-draft-tag"]').exists())).toEqual([
      false,
      false,
      false,
      false,
      true,
    ])
    // Only the AI draft has Keep, before its Edit (A3.28).
    expect(rows.map((row) => row.find('[data-testid="keep-question"]').exists())).toEqual([
      false,
      false,
      false,
      false,
      true,
    ])
    expect(rows[4]!.findAll('.row-list-actions button').map((button) => button.text())).toEqual(['', 'Keep', 'Edit'])
  })

  it('keeps an AI draft as it is: the tag and Keep go, and focus moves to its Edit', async () => {
    const current = lesson({ questions: [...questions(4), question(200, 5, 'What does 教室 mean?', true)] })
    // A published lesson's first change gives the quiz new ids: the row is found by its number.
    const kept = lesson({ questions: [...questions(4, 300), question(304, 5, 'What does 教室 mean?')] })
    api.keepQuestion.mockResolvedValue(kept)
    // The lesson editor takes the new lesson at once, as the page does.
    const Editor = defineComponent(() => {
      const editor = shallowRef(current)
      return () =>
        h(LessonQuizPanel, { editor: editor.value, onUpdated: (next: LessonEditor) => (editor.value = next) })
    })
    const wrapper = mount(Editor, {
      global: { stubs: { teleport: true, AdminQuizDraftPanel: true } },
      attachTo: document.body,
    })

    await wrapper.get('[data-testid="keep-question"]').trigger('click')
    await flushPromises()

    expect(api.keepQuestion).toHaveBeenCalledWith(11, 200)
    expect(wrapper.find('[data-testid="ai-draft-tag"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="keep-question"]').exists()).toBe(false)
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Edit question 5')
    wrapper.unmount()
  })

  it('says when Keep fails and leaves the AI draft as it was', async () => {
    api.keepQuestion.mockRejectedValue(new Error('offline'))
    const wrapper = mountPanel(lesson({ questions: [...questions(4), question(200, 5, 'Question 5?', true)] }))

    await wrapper.get('[data-testid="keep-question"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')?.[0]).toEqual(['The quiz could not be saved.'])
    expect(wrapper.emitted('updated')).toBeUndefined()
    expect(wrapper.get<HTMLButtonElement>('[data-testid="keep-question"]').element.disabled).toBe(false)
  })

  it('saves an edited AI draft, which the server then counts as checked', async () => {
    const current = lesson({ questions: [...questions(4), question(200, 5, 'Question 5?', true)] })
    const after = lesson({ questions: questions(5) })
    api.updateQuestion.mockResolvedValue(after)
    const wrapper = mountPanel(current)

    await wrapper.get('button[aria-label="Edit question 5"]').trigger('click')
    await wrapper.get('[role="dialog"] form').trigger('submit')
    await flushPromises()

    expect(api.updateQuestion).toHaveBeenCalledWith(11, 200, expect.objectContaining({ questionText: 'Question 5?' }))
    expect(wrapper.emitted('updated')?.at(-1)).toEqual([after])
    expect(wrapper.emitted('notice')?.at(-1)).toEqual(['Question saved.'])
  })

  it('gives a new lesson its quiz, hints off, with its first AI questions', async () => {
    const withQuiz = lesson({ questions: [] })
    api.addQuiz.mockResolvedValue(withQuiz)
    api.addQuestion.mockResolvedValue(lesson({ questions: questions(1) }))
    const wrapper = mountPanel(lesson(null))

    draftPanel(wrapper).vm.$emit('quiz-draft', draft(1))
    await flushPromises()

    expect(api.addQuiz).toHaveBeenCalledWith(11, { title: 'University Life', hintsAllowed: false })
    expect(api.addQuestion).toHaveBeenCalledWith(
      11,
      3,
      expect.objectContaining({ questionText: 'What does word 1 mean?' }),
    )
  })

  it('saves "Allow SparkBot hints" on the quiz, or makes the quiz with it, and says so', async () => {
    api.updateQuiz.mockResolvedValue(lesson({ hintsAllowed: true }))
    const wrapper = mountPanel()
    const toggle = wrapper.get<HTMLInputElement>('[data-testid="quiz-hints"]')
    expect(toggle.element.checked).toBe(false)
    expect(wrapper.text()).toContain('Off for this lesson. New lessons start with hints off.')

    await toggle.setValue(true)
    await flushPromises()
    expect(api.updateQuiz).toHaveBeenCalledWith(11, 3, { hintsAllowed: true })
    expect(wrapper.emitted('notice')?.at(-1)).toEqual(['Hint setting saved.'])

    api.addQuiz.mockResolvedValue(lesson({ hintsAllowed: true }))
    const noQuiz = mountPanel(lesson(null))
    await noQuiz.get('[data-testid="quiz-hints"]').setValue(true)
    await flushPromises()
    expect(api.addQuiz).toHaveBeenCalledWith(11, { title: 'University Life', hintsAllowed: true })
  })

  it('shows the switch as it is being saved, and the saved setting again when the save fails', async () => {
    const save = deferred<LessonEditor>()
    api.updateQuiz.mockReturnValue(save.promise)
    const wrapper = mountPanel()
    const toggle = wrapper.get<HTMLInputElement>('[data-testid="quiz-hints"]')

    await toggle.setValue(true)
    expect(toggle.element.checked).toBe(true)
    expect(toggle.element.disabled).toBe(true)
    save.reject(new Error('offline'))
    await flushPromises()

    expect(toggle.element.checked).toBe(false)
    expect(wrapper.emitted('error')?.[0]).toEqual(['The quiz could not be saved.'])
  })

  it('deletes a question at once and hands it to the editor for Undo', async () => {
    const current = lesson({ questions: questions(5) })
    const after = lesson({ questions: questions(4) })
    api.deleteQuestion.mockResolvedValue(after)
    const wrapper = mountPanel(current)

    await wrapper.get('button[aria-label="Delete question 5"]').trigger('click')
    await flushPromises()

    expect(api.deleteQuestion).toHaveBeenCalledWith(11, 104)
    expect(wrapper.emitted('updated')?.[0]).toEqual([after])
    expect(wrapper.emitted('deleted')?.[0]).toEqual([current.quizzes[0]!.questions[4], 3])
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
  })

  it('keeps the questions of a quiz learners have taken, but still lets the hint setting change', () => {
    const wrapper = mountPanel(lesson({ questions: questions(5), questionsLocked: true }))

    expect(wrapper.text()).toContain('Learners have taken this quiz')
    expect(wrapper.find('[data-testid="add-question"]').exists()).toBe(false)
    expect(wrapper.find('button[aria-label="Edit question 1"]').exists()).toBe(false)
    expect(wrapper.find('button[aria-label="Delete question 1"]').exists()).toBe(false)
    expect(draftPanel(wrapper).exists()).toBe(false)
    expect(wrapper.get<HTMLInputElement>('[data-testid="quiz-hints"]').element.disabled).toBe(false)
  })

  it('shows an archived lesson read-only', () => {
    const wrapper = mountPanel(lesson({ questions: questions(5), hintsAllowed: true }, { editable: false }))

    expect(wrapper.find('[data-testid="add-question"]').exists()).toBe(false)
    expect(draftPanel(wrapper).exists()).toBe(false)
    const toggle = wrapper.get<HTMLInputElement>('[data-testid="quiz-hints"]')
    expect(toggle.element.checked).toBe(true)
    expect(toggle.element.disabled).toBe(true)
  })

  it('waits for an AI draft before another change starts', async () => {
    const wrapper = mountPanel(lesson({ questions: questions(2) }))

    draftPanel(wrapper).vm.$emit('generating', true)
    await flushPromises()
    expect(addQuestionButton(wrapper).element.disabled).toBe(true)
    expect(wrapper.get<HTMLButtonElement>('button[aria-label="Delete question 1"]').element.disabled).toBe(true)
    expect(wrapper.emitted('saving')?.at(-1)).toEqual([true])

    draftPanel(wrapper).vm.$emit('generating', false)
    await flushPromises()
    expect(addQuestionButton(wrapper).element.disabled).toBe(false)
    expect(wrapper.emitted('saving')?.at(-1)).toEqual([false])
  })

  it('resets state and suppresses late emissions when switching to another lesson mid-mutation', async () => {
    const pendingDelete = deferred<LessonEditor>()
    api.deleteQuestion.mockReturnValueOnce(pendingDelete.promise)
    const wrapper = mountPanel(lesson({ questions: questions(2) }))

    await wrapper.get('button[aria-label="Delete question 1"]').trigger('click')
    expect(wrapper.emitted('saving')?.at(-1)).toEqual([true])

    await wrapper.setProps({ editor: lesson({ questions: questions(1) }, { lessonId: 99 }) })
    expect(wrapper.emitted('saving')?.at(-1)).toEqual([false])

    pendingDelete.resolve(lesson({ questions: questions(1) }))
    await flushPromises()
    expect(wrapper.emitted('deleted')).toBeUndefined()
    expect(wrapper.emitted('updated')).toBeUndefined()
  })

  it('emits saving false on unmount while a quiz mutation or AI draft is in flight', async () => {
    const wrapper = mountPanel(lesson({ questions: questions(2) }))
    draftPanel(wrapper).vm.$emit('generating', true)
    await flushPromises()
    expect(wrapper.emitted('saving')?.at(-1)).toEqual([true])

    wrapper.unmount()
    expect(wrapper.emitted('saving')?.at(-1)).toEqual([false])
  })
})
