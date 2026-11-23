import { flushPromises, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as contentCounts from '@/components/admin/adminContentCounts'
import { deferred, pageOf } from '@/test/helpers'
import { mountAtRoute } from '@/test/routes'
import type { PageResponse } from '@/types/api'
import type {
  AdminLearningPath,
  AdminUnitDetail,
  AdminUnitRow,
  LevelCheckQuestionDetail,
  LevelCheckQuestionRow,
} from '@/types/learn'

const api = vi.hoisted(() => ({
  path: vi.fn(),
  unit: vi.fn(),
  setUnitPublished: vi.fn(),
  questions: vi.fn(),
  question: vi.fn(),
  saveQuestion: vi.fn(),
  publishQuestion: vi.fn(),
  unpublishQuestion: vi.fn(),
}))

vi.mock('@/api/learn', () => ({ adminLearningApi: api }))

import AdminLearningPathPage from './AdminLearningPathPage.vue'

// The fixtures count lessons and reviews in `lessonCount`, with chests counted separately in `bonusCount`.
const greetings: AdminUnitRow = {
  id: 1,
  number: 1,
  title: 'Greetings',
  titleZh: '问候',
  hskLevel: 1,
  lessonCount: 6,
  bonusCount: 1,
  studentsStarted: 12,
  status: 'PUBLISHED',
}
const work: AdminUnitRow = {
  id: 7,
  number: 7,
  title: 'Work',
  titleZh: '工作',
  hskLevel: 2,
  lessonCount: 5,
  bonusCount: 0,
  studentsStarted: 0,
  status: 'DRAFT',
}

/** The path with each unit under the level of its HSK level. */
function pathOf(units: AdminUnitRow[]): AdminLearningPath {
  const levels = [...new Set(units.map((unit) => unit.hskLevel))].map((hskLevel) => ({
    level: hskLevel,
    hskLevel,
    units: units.filter((unit) => unit.hskLevel === hskLevel),
  }))
  return { unitCount: units.length, levels }
}

function unitDetail(status: 'PUBLISHED' | 'DRAFT'): AdminUnitDetail {
  return {
    unit: { ...greetings, status },
    lessons: [
      { id: 11, title: 'Hello and goodbye', kind: 'LESSON', minutes: 5, xpReward: 10 },
      { id: 16, title: 'Unit 1 review', kind: 'REVIEW', minutes: 6, xpReward: 15 },
    ],
  }
}

const question: LevelCheckQuestionRow = {
  id: 3,
  prompt: 'What does 你好 mean?',
  skill: 'VOCABULARY',
  difficulty: 'EASY',
  level: 'BEGINNER_1',
  status: 'PUBLISHED',
}
const draftQuestion: LevelCheckQuestionRow = {
  id: 4,
  prompt: 'Which one means “thank you”?',
  skill: 'VOCABULARY',
  difficulty: 'MEDIUM',
  level: 'BEGINNER_2',
  status: 'DRAFT',
}

/** The draft question as the server stores it. */
const storedDraft: LevelCheckQuestionDetail = {
  ...draftQuestion,
  hanzi: '谢谢',
  pinyin: 'xièxie',
  audioText: null,
  answeredCount: 0,
  correctPercent: null,
  options: [
    { text: 'Thank you', correct: true, explanation: null },
    { text: 'Goodbye', correct: false, explanation: 'That is 再见.' },
  ],
}

async function mountPage() {
  return (await mountAtRoute(AdminLearningPathPage, '/admin/content/learning-path')).wrapper
}

async function fillNewQuestion(wrapper: VueWrapper) {
  await wrapper.get('[data-testid="new-question"]').trigger('click')
  await flushPromises()
  const drawer = wrapper.get('[data-testid="question-drawer"]')
  await drawer.get('input.input').setValue('Which one means “thank you”?')
  for (const [index, text] of ['谢谢', '再见', '你好'].entries()) {
    await wrapper.get(`[data-testid="answer-${index}"]`).setValue(text)
  }
}

function toast(wrapper: VueWrapper) {
  return wrapper.get('[data-testid="toast"]')
}

function questionButtons(wrapper: VueWrapper): string[][] {
  return wrapper
    .findAll('[data-testid="question-row"]')
    .map((row) => row.findAll('button').map((button) => button.text()))
}

describe('AdminLearningPathPage', () => {
  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset())
    vi.spyOn(contentCounts, 'reloadContentCounts').mockResolvedValue()
    api.path.mockResolvedValue(pathOf([greetings, work]))
    api.questions.mockResolvedValue(pageOf([question], 0, 1, 8))
    api.unit.mockResolvedValue(unitDetail('PUBLISHED'))
  })

  it('lists the units under their HSK level, with their status, and the level check questions', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('h1').text()).toBe('Learning path')
    expect(wrapper.get('#learning-path-title').text()).toBe('Units')
    expect(wrapper.get('#learning-path-level-1').text()).toBe('HSK 1 (Basic)')
    expect(wrapper.get('#learning-path-level-2').text()).toBe('HSK 2 (Survival)')
    const greetingsRow = wrapper.get('[data-testid="unit-row-1"]')
    expect(greetingsRow.get('.admin-path-tag').text()).toBe('Unit 1')
    expect(greetingsRow.get('.row-list-title').text()).toBe('Greetings · 问候')
    expect(greetingsRow.text()).toContain('Published')
    expect(wrapper.get('[data-testid="unit-row-7"]').text()).toContain('Draft')
    expect(wrapper.text()).toContain('What does 你好 mean?')
    // Eight questions a page.
    expect(api.questions).toHaveBeenCalledWith(0, 8)
    // All of it comes with the overview: no unit is read until its drawer opens.
    expect(api.unit).not.toHaveBeenCalled()
  })

  it('names a unit’s lessons and its bonus chest, then the learners who started it once it is published', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="unit-row-1"] .row-list-meta').text()).toBe(
      '6 lessons + bonus chest · 12 learners started',
    )
    // Nobody can start a draft.
    expect(wrapper.get('[data-testid="unit-row-7"] .row-list-meta').text()).toBe('5 lessons')
  })

  it('uses the singular for one lesson and one learner, counts more than one chest and groups thousands', async () => {
    api.path.mockResolvedValue(
      pathOf([
        { ...greetings, lessonCount: 1, bonusCount: 0, studentsStarted: 1 },
        { ...work, lessonCount: 3, bonusCount: 2, studentsStarted: 1200, status: 'PUBLISHED' },
      ]),
    )
    const wrapper = await mountPage()

    expect(wrapper.get('[data-testid="unit-row-1"] .row-list-meta').text()).toBe('1 lesson · 1 learner started')
    expect(wrapper.get('[data-testid="unit-row-7"] .row-list-meta').text()).toBe(
      '3 lessons + 2 bonus chests · 1,200 learners started',
    )
  })

  it('counts the level check questions beside their title, and pages through them by page', async () => {
    api.questions.mockResolvedValue(pageOf([question], 0, 81, 8))
    const wrapper = await mountPage()

    const title = wrapper.get('#level-check-title')
    expect(title.get('.count-badge').text()).toBe('81')
    expect(title.get('.sr-only').text()).toBe('81 questions')
    const pages = wrapper.get('nav[aria-label="Level check question pages"]')
    expect(pages.get('.pagination-info').text()).toBe('Page 1 of 11')

    await pages.get('button[aria-label="Next"]').trigger('click')
    await flushPromises()
    expect(api.questions).toHaveBeenLastCalledWith(1, 8)
  })

  it('keeps level check questions available when the independent unit list fails', async () => {
    api.path.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountPage()
    expect(wrapper.get('[data-testid="question-row"]').text()).toContain(question.prompt)
    await wrapper.get('.state-panel.is-error button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="unit-row-1"]').text()).toContain('Greetings')
    expect(api.questions).toHaveBeenCalledOnce()
  })

  it('shows more than 99 questions as 99+, and one question in the singular', async () => {
    api.questions.mockResolvedValue(pageOf([question], 0, 120, 8))
    const wrapper = await mountPage()
    expect(wrapper.get('#level-check-title .count-badge').text()).toBe('99+')

    api.questions.mockResolvedValue(pageOf([question], 0, 1, 8))
    const single = await mountPage()
    expect(single.get('#level-check-title .sr-only').text()).toBe('1 question')
    expect(single.find('nav[aria-label="Level check question pages"]').exists()).toBe(false)
  })

  it('shows the number of answers and the percentage correct', async () => {
    api.question.mockResolvedValue({
      ...question,
      hanzi: null,
      pinyin: null,
      audioText: null,
      options: [
        { text: '你好', correct: true, explanation: null },
        { text: '再见', correct: false, explanation: null },
      ],
      answeredCount: 1,
      correctPercent: 100,
    })
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="question-row"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="question-drawer"]').text()).toContain('1 answer · 100% correct')
  })

  it('opens a unit in a drawer, numbering its stops and starring a bonus', async () => {
    const wrapper = await mountPage()
    expect(wrapper.find('[data-testid="unit-detail"]').exists()).toBe(false)
    expect(api.unit).not.toHaveBeenCalled()

    api.unit.mockResolvedValue({
      // The unit's own counts, as the server sends them with its stops.
      unit: { ...greetings, lessonCount: 2, bonusCount: 1 },
      lessons: [
        { id: 11, title: 'Hello and goodbye', kind: 'LESSON', minutes: 5, xpReward: 10 },
        { id: 12, title: 'Bonus chest', kind: 'BONUS', minutes: 3, xpReward: 15 },
        { id: 16, title: 'Unit 1 review', kind: 'REVIEW', minutes: 6, xpReward: 15 },
      ],
    })
    await wrapper.get('[data-testid="open-unit-1"]').trigger('click')
    await flushPromises()
    expect(api.unit).toHaveBeenCalledExactlyOnceWith(1)
    const detail = wrapper.get('[data-testid="unit-detail"]')
    expect(detail.get('h2').text()).toBe('Unit 1 · Greetings · 问候')
    expect(detail.get('.overlay-subtitle').text()).toBe('HSK 1 · 12 learners started · Published')
    expect(detail.text()).toContain('Lesson · about 5 min · +10 XP')
    expect(detail.text()).toContain('Bonus · opens after lesson 1')
    expect(detail.text()).toContain('Review · about 6 min · +15 XP')
    expect(detail.findAll('.admin-step').map((step) => step.text())).toEqual(['1', '', '2'])

    await wrapper.get('[data-testid="unit-detail"] .overlay-footer button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="unit-detail"]').exists()).toBe(false)

    api.unit.mockResolvedValue({ ...unitDetail('DRAFT'), unit: work })
    await wrapper.get('[data-testid="open-unit-7"]').trigger('click')
    await flushPromises()
    expect(api.unit).toHaveBeenLastCalledWith(7)
    expect(wrapper.get('[data-testid="unit-detail"] .overlay-subtitle').text()).toBe('HSK 2 · Draft')
    expect(wrapper.find('[data-testid="unit-publish"]').exists()).toBe(true)
  })

  it('unpublishes a unit only after confirming, and says so', async () => {
    api.setUnitPublished.mockResolvedValue(unitDetail('DRAFT'))
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="open-unit-1"]').trigger('click')
    await flushPromises()
    expect(api.unit).toHaveBeenCalledWith(1)
    expect(wrapper.get('[data-testid="unit-detail"]').text()).toContain('Unit 1 review')

    await wrapper.get('[data-testid="unit-unpublish"]').trigger('click')
    expect(api.setUnitPublished).not.toHaveBeenCalled()
    const dialog = wrapper.get('[data-testid="confirm-dialog"]')
    expect(dialog.text()).toContain('Unpublish Unit 1 · Greetings · 问候?')
    // The alert dialog is described by what unpublishing does.
    expect(dialog.attributes('aria-describedby')).toBe(dialog.get('.overlay-message').attributes('id'))

    const pathLoads = api.path.mock.calls.length
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(api.setUnitPublished).toHaveBeenCalledWith(1, false)
    expect(toast(wrapper).text()).toBe(
      'Unit 1 · Greetings · 问候 is unpublished and hidden from learners. Their progress is kept.',
    )
    // The list is read again with one request; the only unit request was the drawer's.
    expect(api.path).toHaveBeenCalledTimes(pathLoads + 1)
    expect(api.unit).toHaveBeenCalledTimes(1)
  })

  it('saves a new level check question, with exactly one right answer, as a draft to publish (A4.06)', async () => {
    api.saveQuestion.mockResolvedValue({ ...storedDraft, id: 9 })
    const wrapper = await mountPage()

    await fillNewQuestion(wrapper)
    const questionLoads = api.questions.mock.calls.length
    await wrapper.get('[data-testid="question-drawer"] form').trigger('submit')
    await flushPromises()

    expect(api.saveQuestion).toHaveBeenCalledWith(
      null,
      expect.objectContaining({
        prompt: 'Which one means “thank you”?',
        status: 'DRAFT',
        options: [
          { text: '谢谢', correct: true, explanation: null },
          { text: '再见', correct: false, explanation: null },
          { text: '你好', correct: false, explanation: null },
        ],
      }),
    )
    expect(wrapper.find('[data-testid="question-drawer"]').exists()).toBe(false)
    expect(toast(wrapper).text()).toBe('Question added as a draft. Publish it to use it in level checks.')
    expect(api.questions).toHaveBeenCalledTimes(questionLoads + 1)
  })

  it('says a new question published from its drawer is published', async () => {
    api.saveQuestion.mockResolvedValue({ ...storedDraft, id: 9, status: 'PUBLISHED' })
    const wrapper = await mountPage()

    await fillNewQuestion(wrapper)
    await wrapper.get('[data-testid="question-drawer"] select[id$="-status"]').setValue('PUBLISHED')
    await wrapper.get('[data-testid="question-drawer"] form').trigger('submit')
    await flushPromises()

    expect(api.saveQuestion).toHaveBeenCalledWith(null, expect.objectContaining({ status: 'PUBLISHED' }))
    expect(toast(wrapper).text()).toContain('Question published. It can now appear in level checks.')
    expect(toast(wrapper).get('.toast-action').text()).toBe('Undo')
  })

  it('publishes a draft question from its row, without opening it (A4.16)', async () => {
    api.questions.mockResolvedValue(pageOf([question, draftQuestion], 0, 2, 8))
    api.publishQuestion.mockResolvedValue({ ...storedDraft, status: 'PUBLISHED' })
    const wrapper = await mountPage()

    // Only a draft offers Publish, before Open; its level reads as an HSK level.
    expect(questionButtons(wrapper)).toEqual([['Open'], ['Publish', 'Open']])
    const publish = wrapper.get('[data-testid="publish-question"]')
    expect(publish.classes()).toContain('btn-secondary')
    expect(publish.attributes('aria-label')).toBe('Publish question: Which one means “thank you”?')
    expect(wrapper.findAll('[data-testid="question-row"]')[1]!.text()).toContain('HSK 2')

    const updatedRows = deferred<PageResponse<LevelCheckQuestionRow>>()
    api.questions.mockReturnValueOnce(updatedRows.promise)
    await publish.trigger('click')
    await flushPromises()
    // Undo must be actionable as soon as it appears, even when loading the new rows takes longer.
    expect(wrapper.find('.toast-action').exists()).toBe(false)
    updatedRows.resolve(pageOf([question, { ...draftQuestion, status: 'PUBLISHED' }], 0, 2, 8))
    await flushPromises()

    // The server publishes the question as it is stored: nothing is read or sent again.
    expect(api.publishQuestion).toHaveBeenCalledExactlyOnceWith(4)
    expect(api.question).not.toHaveBeenCalled()
    expect(api.saveQuestion).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="question-drawer"]').exists()).toBe(false)
    expect(toast(wrapper).text()).toContain('Question published. It can now appear in level checks.')
    expect(questionButtons(wrapper)).toEqual([['Open'], ['Open']])
    expect(wrapper.findAll('[data-testid="question-row"]')[1]!.text()).toContain('Published')

    // Undo puts it back to a draft, which offers Publish again.
    api.unpublishQuestion.mockResolvedValue(storedDraft)
    api.questions.mockResolvedValue(pageOf([question, draftQuestion], 0, 2, 8))
    await toast(wrapper).get('.toast-action').trigger('click')
    await flushPromises()
    expect(api.unpublishQuestion).toHaveBeenCalledExactlyOnceWith(4)
    expect(toast(wrapper).text()).toBe('Question is a draft again. It won’t appear in level checks.')
    expect(questionButtons(wrapper)).toEqual([['Open'], ['Publish', 'Open']])
  })

  it('says on the page when the Undo of a Publish fails', async () => {
    api.questions.mockResolvedValue(pageOf([draftQuestion], 0, 1, 8))
    api.publishQuestion.mockResolvedValue({ ...storedDraft, status: 'PUBLISHED' })
    api.unpublishQuestion.mockRejectedValue(new Error('offline'))
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="publish-question"]').trigger('click')
    await flushPromises()
    await toast(wrapper).get('.toast-action').trigger('click')
    await flushPromises()

    expect(wrapper.get('.data-notice').text()).toContain('Your changes could not be saved. Please try again.')
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
  })

  it('publishes one question at a time', async () => {
    const publication = deferred<LevelCheckQuestionDetail>()
    api.questions.mockResolvedValue(pageOf([draftQuestion, { ...draftQuestion, id: 5 }], 0, 2, 8))
    api.publishQuestion.mockReturnValue(publication.promise)
    const wrapper = await mountPage()

    const buttons = () => wrapper.findAll<HTMLButtonElement>('[data-testid="publish-question"]')
    await buttons()[0]!.trigger('click')
    expect(buttons()[0]!.attributes('aria-busy')).toBe('true')
    expect(buttons().map((button) => button.element.disabled)).toEqual([true, true])
    await buttons()[1]!.trigger('click')
    expect(api.publishQuestion).toHaveBeenCalledExactlyOnceWith(4)

    publication.resolve({ ...storedDraft, status: 'PUBLISHED' })
    await flushPromises()
    expect(buttons().map((button) => button.element.disabled)).toEqual([false, false])
  })

  it('says on the page why a question could not be published, and keeps its Publish', async () => {
    api.questions.mockResolvedValue(pageOf([draftQuestion], 0, 1, 8))
    api.publishQuestion.mockRejectedValue(new Error('offline'))
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="publish-question"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('.data-notice').text()).toContain('The question could not be published.')
    expect(wrapper.find('[data-testid="toast"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="publish-question"]').attributes('disabled')).toBeUndefined()
  })

  it('says in the open drawer, not under its scrim, when publishing a unit fails, and Publish tries again', async () => {
    api.unit.mockResolvedValue(unitDetail('DRAFT'))
    api.setUnitPublished.mockRejectedValue(new Error('offline'))
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="open-unit-1"]').trigger('click')
    await flushPromises()
    const drawer = () => wrapper.get('[data-testid="unit-detail"]')

    await wrapper.get('[data-testid="unit-publish"]').trigger('click')
    await flushPromises()
    // In the footer, by the button pressed: always in view, however long the list above it.
    expect(drawer().get('.overlay-footer [role="alert"]').text()).toBe(
      'Your changes could not be saved. Please try again.',
    )
    expect(wrapper.find('.data-notice').exists()).toBe(false)
    expect(wrapper.get('[data-testid="unit-publish"]').attributes('disabled')).toBeUndefined()

    api.setUnitPublished.mockResolvedValue(unitDetail('PUBLISHED'))
    await wrapper.get('[data-testid="unit-publish"]').trigger('click')
    await flushPromises()
    expect(api.setUnitPublished).toHaveBeenCalledTimes(2)
    expect(drawer().find('[role="alert"]').exists()).toBe(false)
    expect(drawer().get('.overlay-subtitle').text()).toContain('Published')
    expect(toast(wrapper).text()).toBe('Unit 1 · Greetings · 问候 is published. Learners see it on their path.')
  })

  it('says in the open drawer when unpublishing a unit fails, once the confirmation has closed', async () => {
    api.setUnitPublished.mockRejectedValue(new Error('offline'))
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="open-unit-1"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="unit-unpublish"]').trigger('click')
    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(api.setUnitPublished).toHaveBeenCalledWith(1, false)
    expect(wrapper.find('[data-testid="confirm-dialog"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="unit-detail"] .overlay-footer [role="alert"]').text()).toBe(
      'Your changes could not be saved. Please try again.',
    )
    expect(wrapper.find('.data-notice').exists()).toBe(false)
  })

  it('keeps the unit drawer open while publishing, including its footer close button', async () => {
    const publication = deferred<AdminUnitDetail>()
    api.unit.mockResolvedValue(unitDetail('DRAFT'))
    api.setUnitPublished.mockReturnValue(publication.promise)
    const wrapper = await mountPage()
    await wrapper.get('[data-testid="open-unit-1"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="unit-publish"]').trigger('click')
    await wrapper.get('[data-testid="unit-detail"] .overlay-footer button').trigger('click')
    expect(wrapper.find('[data-testid="unit-detail"]').exists()).toBe(true)
    publication.resolve(unitDetail('PUBLISHED'))
    await flushPromises()
    expect(wrapper.get('[data-testid="unit-detail"]').text()).toContain('Published')
    await wrapper.get('[data-testid="unit-detail"] .overlay-footer button').trigger('click')
    expect(wrapper.find('[data-testid="unit-detail"]').exists()).toBe(false)
  })

  it('prevents opening a question drawer while a row publish is in flight', async () => {
    const publication = deferred<LevelCheckQuestionDetail>()
    api.questions.mockResolvedValue(pageOf([draftQuestion], 0, 1, 8))
    api.publishQuestion.mockReturnValue(publication.promise)
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="publish-question"]').trigger('click')
    await wrapper.get('[data-testid="question-row"]').trigger('click')
    await wrapper.get('[data-testid="new-question"]').trigger('click')
    expect(wrapper.find('[data-testid="question-drawer"]').exists()).toBe(false)
    expect(api.question).not.toHaveBeenCalled()

    publication.resolve({ ...storedDraft, status: 'PUBLISHED' })
    await flushPromises()
  })
})
