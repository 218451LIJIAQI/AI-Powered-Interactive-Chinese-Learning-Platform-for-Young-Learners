import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AiReportPayload } from '@/types/ai'

const api = vi.hoisted(() => ({ report: vi.fn() }))
vi.mock('@/api/ai', () => ({ aiHelperApi: api }))

import AiReportDialog from './AiReportDialog.vue'

const answer: Omit<AiReportPayload, 'reasonCategory' | 'description'> = {
  featureKey: 'CHAT_TUTOR',
  chatMessageId: 12,
}

function mountDialog(props: {
  payload: Omit<AiReportPayload, 'reasonCategory' | 'description'>
  defaultReason?: 'OTHER' | 'SCORE_SEEMS_WRONG'
}) {
  return mount(AiReportDialog, {
    props: { ...props, title: 'Report this answer', attached: 'Your question and answer are attached.' },
    global: { stubs: { teleport: true } },
  })
}

function chosenReason(wrapper: ReturnType<typeof mountDialog>): string {
  return wrapper.get<HTMLSelectElement>('select').element.value
}

describe('AiReportDialog', () => {
  beforeEach(() => {
    api.report.mockReset()
    api.report.mockResolvedValue({})
  })

  it('starts on the first reason of the feature when the screen names none', () => {
    expect(chosenReason(mountDialog({ payload: answer }))).toBe('INCORRECT_CONTENT')
  })

  it('preselects the reason the screen names, and sends it unless the learner changes it', async () => {
    const wrapper = mountDialog({ payload: answer, defaultReason: 'OTHER' })
    expect(chosenReason(wrapper)).toBe('OTHER')

    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.report).toHaveBeenCalledWith({ featureKey: 'CHAT_TUTOR', chatMessageId: 12, reasonCategory: 'OTHER' })
    expect(wrapper.emitted('sent')).toHaveLength(1)
  })

  it('leads the list with a named reason the feature does not list', () => {
    const wrapper = mountDialog({ payload: answer, defaultReason: 'SCORE_SEEMS_WRONG' })

    expect(wrapper.findAll('option').map((option) => option.attributes('value'))).toEqual([
      'SCORE_SEEMS_WRONG',
      'INCORRECT_CONTENT',
      'UNCLEAR_EXPLANATION',
      'INAPPROPRIATE_CONTENT',
      'OTHER',
    ])
    expect(chosenReason(wrapper)).toBe('SCORE_SEEMS_WRONG')
  })
})
