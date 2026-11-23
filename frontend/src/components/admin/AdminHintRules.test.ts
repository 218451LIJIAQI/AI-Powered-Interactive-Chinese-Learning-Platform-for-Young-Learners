import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { adminAiMessages } from '@/plugins/adminAiMessages'
import { i18n } from '@/plugins/i18n'
import type { HintPolicyLevel } from '@/types/learn'

const adminAiApi = vi.hoisted(() => ({ hintPolicy: vi.fn(), saveHintPolicy: vi.fn() }))

vi.mock('@/api/ai', () => ({ adminAiApi }))

import AdminHintRules from './AdminHintRules.vue'

const POLICY: HintPolicyLevel[] = [
  { tier: 'HSK1', delaySeconds: 15, perSet: 0 },
  { tier: 'HSK2', delaySeconds: 30, perSet: 3 },
  { tier: 'HSK3', delaySeconds: 60, perSet: 1 },
]

async function mountRules() {
  const wrapper = mount(AdminHintRules)
  await flushPromises()
  return wrapper
}

describe('AdminHintRules', () => {
  beforeEach(() => {
    Object.values(adminAiApi).forEach((fn) => fn.mockReset())
    adminAiApi.hintPolicy.mockResolvedValue(POLICY)
  })

  it('lists a rule per level, then where the rules do not reach', async () => {
    const wrapper = await mountRules()

    expect(wrapper.findAll('thead th').map((cell) => cell.text())).toEqual([
      'Level',
      'Offered after (seconds)',
      'Hints per set',
      'What a hint gives',
    ])
    const rows = wrapper.findAll('tbody tr')
    expect(rows.map((row) => row.get('th').text())).toEqual([
      'HSK 1 (Basic)',
      'HSK 2 (Survival)',
      'HSK 3 (Conversational)',
      adminAiMessages.en.hintsLevelCheckRow,
      'AI Helper stories',
      'Class lesson quizzes',
    ])
    // A hint is one step: what it gives follows the level and is not a setting (A5.05).
    expect(rows.slice(0, 3).map((row) => row.findAll('td').at(-1)!.text())).toEqual([
      'Rules out options and explains the key word.',
      'One short clue, no explanation.',
      'A nudge: where to look.',
    ])
    expect(rows[0]!.findAll('select')).toHaveLength(2)
    expect(rows[3]!.get('td').text()).toBe(adminAiMessages.en.hintsNever)
    expect(wrapper.get('[data-testid="hint-rule-stories"] td').text()).toBe('Never')
    // A class lesson quiz has no hints until an admin allows SparkBot hints for that lesson (A5.05).
    const classQuizzes = wrapper.get('[data-testid="hint-rule-class-quizzes"]')
    expect(classQuizzes.get('td').text()).toBe('Off until an admin turns them on for a lesson')
    expect(classQuizzes.get('td').attributes('colspan')).toBe('3')
    expect(classQuizzes.find('select').exists()).toBe(false)
    expect(wrapper.text()).not.toMatch(/teacher|steps per question/i)
  })

  it.each([
    ['ms', 'Kuiz pelajaran kelas', 'Mati sehingga pentadbir menghidupkannya untuk sesuatu pelajaran'],
    ['zh-CN', '课堂课时测验', '默认关闭，管理员可为单个课时开启'],
  ] as const)('says the same about class lesson quizzes in %s', async (locale, label, note) => {
    i18n.global.locale.value = locale
    const wrapper = await mountRules()

    const classQuizzes = wrapper.get('[data-testid="hint-rule-class-quizzes"]')
    expect(classQuizzes.get('th').text()).toBe(label)
    expect(classQuizzes.get('td').text()).toBe(note)
  })

  it('activates Save on change, submits the full policy and reports it', async () => {
    const saved = POLICY.map((level) => (level.tier === 'HSK1' ? { ...level, delaySeconds: 20 } : level))
    adminAiApi.saveHintPolicy.mockResolvedValue(saved)
    const wrapper = await mountRules()
    const save = wrapper.get<HTMLButtonElement>('[data-testid="save-hint-rules"]')
    expect(save.element.disabled).toBe(true)

    await wrapper.get('[data-testid="hint-rule-HSK1"] select').setValue(20)
    expect(save.element.disabled).toBe(false)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(adminAiApi.saveHintPolicy).toHaveBeenCalledWith(saved)
    expect(wrapper.emitted('saved')).toEqual([[adminAiMessages.en.hintsSaved]])
    expect(save.element.disabled).toBe(true)
  })

  it('shows an error alert and keeps edits enabled when saving fails', async () => {
    adminAiApi.saveHintPolicy.mockRejectedValue(new Error('Save failed'))
    const wrapper = await mountRules()
    const save = wrapper.get<HTMLButtonElement>('[data-testid="save-hint-rules"]')

    await wrapper.get('[data-testid="hint-rule-HSK1"] select').setValue(20)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe(adminAiMessages.en.hintsSaveFailed)
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(save.element.disabled).toBe(false)
  })

  it('says so and offers a retry when the rules could not be loaded', async () => {
    adminAiApi.hintPolicy.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(POLICY)
    const wrapper = await mountRules()

    expect(wrapper.get('[role="alert"]').text()).toContain(adminAiMessages.en.hintsLoadFailed)
    expect(wrapper.find('table').exists()).toBe(false)

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()
    expect(adminAiApi.hintPolicy).toHaveBeenCalledTimes(2)
    expect(wrapper.find('table').exists()).toBe(true)
  })

  it('shows saved values outside the suggestions and keeps them when another level changes', async () => {
    const policy = POLICY.map((level) => (level.tier === 'HSK2' ? { ...level, delaySeconds: 17, perSet: 4 } : level))
    adminAiApi.hintPolicy.mockResolvedValue(policy)
    const updated = policy.map((level) => (level.tier === 'HSK1' ? { ...level, delaySeconds: 20 } : level))
    adminAiApi.saveHintPolicy.mockResolvedValue(updated)
    const wrapper = await mountRules()
    const selects = wrapper.get('[data-testid="hint-rule-HSK2"]').findAll<HTMLSelectElement>('select')
    expect(selects.map((select) => select.element.value)).toEqual(['17', '4'])

    await wrapper.get('[data-testid="hint-rule-HSK1"] select').setValue(20)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(adminAiApi.saveHintPolicy).toHaveBeenCalledWith(updated)
  })
})
