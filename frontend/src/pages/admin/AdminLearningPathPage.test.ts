import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { pageOf } from '@/test/helpers'
import { learnRouter } from '@/test/learnFixtures'
import type { AdminLearningPath, AdminUnitDetail, LevelCheckQuestionRow } from '@/types/learn'

const api = vi.hoisted(() => ({
  path: vi.fn(),
  unit: vi.fn(),
  setUnitPublished: vi.fn(),
  questions: vi.fn(),
  question: vi.fn(),
  saveQuestion: vi.fn(),
}))

vi.mock('@/api/learn', () => ({ adminLearningApi: api }))
vi.mock('@/api/shell', () => ({ shellApi: { navCounts: () => Promise.resolve({ reviewQueue: 3 }) } }))

import AdminLearningPathPage from './AdminLearningPathPage.vue'

const learningPath: AdminLearningPath = {
  unitCount: 2,
  lessonCount: 11,
  levels: [
    {
      level: 1,
      hskLevel: 1,
      lessonCount: 11,
      units: [
        {
          id: 1,
          number: 1,
          title: 'Greetings',
          titleZh: '问候',
          hskLevel: 1,
          lessonCount: 6,
          studentsStarted: 12,
          status: 'PUBLISHED',
        },
        {
          id: 7,
          number: 7,
          title: 'Work',
          titleZh: '工作',
          hskLevel: 2,
          lessonCount: 5,
          studentsStarted: 0,
          status: 'DRAFT',
        },
      ],
    },
  ],
}

function unitDetail(status: 'PUBLISHED' | 'DRAFT'): AdminUnitDetail {
  return {
    unit: { ...learningPath.levels[0]!.units[0]!, status },
    lessons: [
      { id: 11, position: 1, title: 'Hello and goodbye', kind: 'LESSON', minutes: 5, xpReward: 10, questionCount: 6 },
      { id: 16, position: 6, title: 'Unit 1 review', kind: 'REVIEW', minutes: 6, xpReward: 15, questionCount: 8 },
    ],
  }
}

const question: LevelCheckQuestionRow = {
  id: 3,
  prompt: 'What does 你好 mean?',
  hanzi: '你好',
  skill: 'VOCABULARY',
  difficulty: 'EASY',
  level: 'BEGINNER_1',
  status: 'PUBLISHED',
  answeredCount: 20,
  correctPercent: 85,
}

async function mountPage() {
  const router = await learnRouter('/admin/content/learning-path')
  const wrapper = mount(AdminLearningPathPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return wrapper
}

describe('AdminLearningPathPage', () => {
  beforeEach(() => {
    api.path.mockResolvedValue(learningPath)
    api.questions.mockResolvedValue(pageOf([question]))
    api.unit.mockResolvedValue(unitDetail('PUBLISHED'))
  })

  it('lists the units by level with their status and the level check questions', async () => {
    const wrapper = await mountPage()

    expect(wrapper.get('h1').text()).toBe('Learning path')
    expect(wrapper.text()).toContain('2 units · 11 lessons')
    expect(wrapper.get('[data-testid="unit-row-1"]').text()).toContain('Published')
    expect(wrapper.get('[data-testid="unit-row-1"]').text()).toContain('12 students started')
    expect(wrapper.get('[data-testid="unit-row-7"]').text()).toContain('Draft')
    expect(wrapper.text()).toContain('What does 你好 mean?')
    expect(api.questions).toHaveBeenCalledWith(0, 10)
  })

  it('shows the first unit beside the path until another one is chosen', async () => {
    const wrapper = await mountPage()

    expect(api.unit).toHaveBeenCalledWith(1)
    const detail = wrapper.get('[data-testid="unit-detail"]')
    expect(detail.get('h2').text()).toBe('Unit 1 · Greetings · 问候')
    expect(detail.text()).toContain('Students see this unit on their path.')
    expect(wrapper.get('[data-testid="open-unit-1"]').attributes('aria-current')).toBe('true')

    api.unit.mockResolvedValue({
      ...unitDetail('DRAFT'),
      unit: { ...learningPath.levels[0]!.units[1]!, status: 'DRAFT' },
    })
    await wrapper.get('[data-testid="open-unit-7"]').trigger('click')
    await flushPromises()
    expect(api.unit).toHaveBeenLastCalledWith(7)
    expect(wrapper.get('[data-testid="unit-detail"]').text()).toContain('Hidden from students until you publish it.')
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
    expect(wrapper.get('[data-testid="confirm-dialog"]').text()).toContain('Unpublish Unit 1 · Greetings · 问候?')

    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(api.setUnitPublished).toHaveBeenCalledWith(1, false)
    expect(wrapper.text()).toContain('Unit 1 · Greetings · 问候 is unpublished and hidden from students.')
    expect(api.path).toHaveBeenCalledTimes(2)
  })

  it('saves a new level check question with exactly one right answer', async () => {
    api.saveQuestion.mockResolvedValue({ ...question, id: 9, pinyin: null, audioText: null, options: [] })
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="new-question"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="question-drawer"] input.input').setValue('Which one means “thank you”?')
    await wrapper.get('[data-testid="answer-0"]').setValue('谢谢')
    await wrapper.get('[data-testid="answer-1"]').setValue('再见')
    await wrapper.get('[data-testid="question-drawer"] form').trigger('submit')
    await flushPromises()

    expect(api.saveQuestion).toHaveBeenCalledWith(
      null,
      expect.objectContaining({
        prompt: 'Which one means “thank you”?',
        options: [
          { text: '谢谢', correct: true, explanation: null },
          { text: '再见', correct: false, explanation: null },
        ],
      }),
    )
    expect(wrapper.text()).toContain('Question saved. New students get it in their level check.')
  })
})
