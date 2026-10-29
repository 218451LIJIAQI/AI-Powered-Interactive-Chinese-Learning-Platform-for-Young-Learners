import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { pageOf } from '@/test/helpers'
import { learnRouter } from '@/test/learnFixtures'
import type { AdminPracticeSetDetail, AdminPracticeSetRow } from '@/types/learn'

const api = vi.hoisted(() => ({
  practiceSets: vi.fn(),
  practiceSet: vi.fn(),
  savePracticeSet: vi.fn(),
  changePracticeSet: vi.fn(),
}))

vi.mock('@/api/learn', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/learn')>()),
  adminLearningApi: api,
}))
vi.mock('@/api/shell', () => ({ shellApi: { navCounts: () => Promise.resolve({ reviewQueue: 3 }) } }))

import AdminPracticeLibraryPage from './AdminPracticeLibraryPage.vue'

const row: AdminPracticeSetRow = {
  id: 4,
  type: 'QUIZZES',
  title: 'Food Quiz',
  stage: 'SCAFFOLDED',
  hskLevel: 1,
  itemCount: 5,
  status: 'PUBLISHED',
  updatedAt: '2026-05-10T03:00:00Z',
}

function detail(overrides: Partial<AdminPracticeSetDetail> = {}): AdminPracticeSetDetail {
  return {
    id: 4,
    type: 'QUIZZES',
    title: 'Food Quiz',
    stage: 'SCAFFOLDED',
    hskLevel: 1,
    xpReward: 15,
    status: 'PUBLISHED',
    createdBy: null,
    createdAt: '2026-05-01T03:00:00Z',
    updatedAt: '2026-05-10T03:00:00Z',
    playedBy: 12,
    items: [
      {
        hanzi: '米饭',
        pinyin: 'mǐfàn',
        meaning: null,
        prompt: 'Which one is rice?',
        audioText: null,
        example: null,
        exampleMeaning: null,
        options: [
          { text: '米饭', pinyin: null },
          { text: '面条', pinyin: null },
        ],
        correctIndex: 0,
        explanation: null,
        distractors: null,
      },
    ],
    storyLines: [],
    strokeCount: null,
    history: [{ action: 'PUBLISHED', actor: null, at: '2026-05-10T03:00:00Z' }],
    ...overrides,
  }
}

async function mountPage() {
  const router = await learnRouter('/admin/content/practice')
  const wrapper = mount(AdminPracticeLibraryPage, { global: { plugins: [router], stubs: { teleport: true } } })
  await flushPromises()
  return wrapper
}

describe('AdminPracticeLibraryPage', () => {
  beforeEach(() => {
    api.practiceSets.mockResolvedValue(pageOf([row], 0, 1, 10))
    api.practiceSet.mockResolvedValue(detail())
  })

  it('lists the platform sets and filters them by type on the server', async () => {
    const wrapper = await mountPage()

    expect(api.practiceSets).toHaveBeenCalledWith({
      q: '',
      type: null,
      status: null,
      stage: null,
      page: 0,
      size: 10,
    })
    const rows = wrapper.findAll('[data-testid="library-row"]')
    expect(rows).toHaveLength(1)
    expect(rows[0]!.text()).toContain('Food Quiz')
    expect(rows[0]!.text()).toContain('Published')

    await wrapper.get('#library-type').setValue('STORY')
    await flushPromises()
    expect(api.practiceSets).toHaveBeenLastCalledWith(expect.objectContaining({ type: 'STORY', page: 0 }))
  })

  it('archives a published set only after the confirm dialog, then offers Restore', async () => {
    api.changePracticeSet.mockResolvedValue(detail({ status: 'ARCHIVED' }))
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="library-row"] button').trigger('click')
    await flushPromises()
    expect(api.practiceSet).toHaveBeenCalledWith('QUIZZES', 4)
    expect(wrapper.get('[data-testid="set-details"]').text()).toContain('Which one is rice?')

    await wrapper.get('[data-testid="set-archive"]').trigger('click')
    expect(api.changePracticeSet).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="confirm-dialog"]').text()).toContain('Archive “Food Quiz”?')

    await wrapper.get('[data-testid="confirm-dialog-confirm"]').trigger('click')
    await flushPromises()
    expect(api.changePracticeSet).toHaveBeenCalledWith('QUIZZES', 4, 'archive')
    expect(wrapper.text()).toContain('Archived: “Food Quiz” is hidden from students.')

    api.changePracticeSet.mockResolvedValue(detail({ status: 'PUBLISHED' }))
    const restore = wrapper.findAll('button').find((button) => button.text() === 'Restore')!
    await restore.trigger('click')
    await flushPromises()
    expect(api.changePracticeSet).toHaveBeenLastCalledWith('QUIZZES', 4, 'restore')
  })

  it('creates a flashcard set from the editor as a draft', async () => {
    api.savePracticeSet.mockResolvedValue(detail({ type: 'FLASHCARDS', title: 'Colours', status: 'DRAFT' }))
    const wrapper = await mountPage()

    await wrapper.get('[data-testid="new-activity"]').trigger('click')
    await wrapper.get('[data-testid="editor-type"]').setValue('FLASHCARDS')
    await wrapper.get('[data-testid="set-editor"] input.input').setValue('Colours')
    // The stubbed Teleport re-creates its content on every update, so each field is looked up again.
    const itemInputs = () => wrapper.get('[data-testid="editor-item"]').findAll('input')
    await itemInputs()[0]!.setValue('红色')
    await itemInputs()[2]!.setValue('red')
    await wrapper.get('[data-testid="set-editor"] form').trigger('submit')
    await flushPromises()

    expect(api.savePracticeSet).toHaveBeenCalledWith(
      'FLASHCARDS',
      null,
      expect.objectContaining({
        title: 'Colours',
        items: [expect.objectContaining({ hanzi: '红色', meaning: 'red' })],
      }),
    )
    expect(wrapper.text()).toContain('Activity saved.')
  })
})
