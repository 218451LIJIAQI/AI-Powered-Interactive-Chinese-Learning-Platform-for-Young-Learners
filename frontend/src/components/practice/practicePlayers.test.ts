import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  dragDropActivities,
  listeningActivities,
  matchingActivities,
  storyActivities,
  tracingActivities,
} from '@/demo/data'
import { i18n } from '@/plugins/i18n'
import type { StudentActivityCompletionResponse } from '@/types/api'

import ActivityResult from './ActivityResult.vue'
import DragDropPlayer from './DragDropPlayer.vue'
import ListeningPlayer from './ListeningPlayer.vue'
import MatchingPlayer from './MatchingPlayer.vue'
import StoryPlayer from './StoryPlayer.vue'
import TracingPad from './TracingPad.vue'

const global = { plugins: [i18n] }

beforeEach(() => {
  i18n.global.locale.value = 'en'
})

describe('practice activity players', () => {
  it('matching: pairs a left item with a right option and submits every match', async () => {
    const game = matchingActivities[0]!
    const wrapper = mount(MatchingPlayer, { props: { game }, global })
    const submit = wrapper.get('[data-testid="activity-submit"]')
    expect(submit.attributes('disabled')).toBeDefined()

    const left = wrapper.findAll('[data-testid="matching-left-item"]')
    for (const [index, item] of left.entries()) {
      await item.trigger('click')
      expect(item.attributes('aria-pressed')).toBe('true')
      await wrapper.findAll('[data-testid="matching-right-option"]')[index]!.trigger('click')
    }

    expect(submit.attributes('disabled')).toBeUndefined()
    await submit.trigger('click')
    const [[submission]] = wrapper.emitted('submit') as [[{ type: string; body: { matches: unknown[] } }]]
    expect(submission.type).toBe('MATCHING_GAME')
    expect(submission.body.matches).toEqual(
      game.leftItems.map((item, index) => ({ leftItemId: item.id, rightText: game.rightOptions[index]!.text })),
    )
  })

  it('drag-and-drop: the select fallback places every item into a zone', async () => {
    const activity = dragDropActivities[0]!
    const wrapper = mount(DragDropPlayer, { props: { activity }, global, attachTo: document.body })
    expect(wrapper.findAll('[data-testid="dragdrop-zone"]')).toHaveLength(activity.zones.length)
    expect(wrapper.findAll('[data-testid="dragdrop-item"]')[0]!.attributes('draggable')).toBe('true')

    for (const item of activity.items) {
      const select = wrapper.get(`select[id$="-item-${item.id}"]`)
      await select.setValue(activity.zones[0]!)
    }

    expect(wrapper.get('[data-testid="dragdrop-pool"]').findAll('[data-testid="dragdrop-item"]')).toHaveLength(0)
    await wrapper.get('[data-testid="activity-submit"]').trigger('click')
    const [[submission]] = wrapper.emitted('submit') as [[{ body: { placements: unknown[] } }]]
    expect(submission.body.placements).toHaveLength(activity.items.length)
    wrapper.unmount()
  })

  it('listening: answers option and typed questions by index', async () => {
    const exercise = listeningActivities.find((item) => item.questions.some((q) => !q.options.length))!
    const wrapper = mount(ListeningPlayer, { props: { exercise }, global })
    expect(wrapper.find('audio').exists()).toBe(false)

    await wrapper.findAll('button[data-testid="listening-answer"]')[0]!.trigger('click')
    await wrapper.get('input[data-testid="listening-answer"]').setValue(' ni hao ')
    await wrapper.get('[data-testid="activity-submit"]').trigger('click')

    const [[submission]] = wrapper.emitted('submit') as [[{ body: { answers: unknown[] } }]]
    expect(submission.body.answers).toEqual([
      { questionIndex: 0, answerText: exercise.questions[0]!.options[0] },
      { questionIndex: 1, answerText: 'ni hao' },
    ])
  })

  it('story: shows the story in Chinese, no explanations up front, and submits option indexes', async () => {
    const story = storyActivities[0]!
    const wrapper = mount(StoryPlayer, { props: { story }, global })
    expect(wrapper.get('.story-text').attributes('lang')).toBe('zh-CN')
    expect(wrapper.text()).not.toContain('Xiao Ming says hello first.')

    for (const question of story.questions) {
      const group = wrapper.get(`[aria-labelledby$="-q-${question.id}"]`)
      await group.findAll('[data-testid="story-option"]')[1]!.trigger('click')
    }
    await wrapper.get('[data-testid="activity-submit"]').trigger('click')
    const [[submission]] = wrapper.emitted('submit') as [[{ body: { answers: unknown[] } }]]
    expect(submission.body.answers).toEqual(story.questions.map((q) => ({ questionId: q.id, optionIndex: 1 })))
  })

  it('tracing: counts strokes from pointer events, clears, and states it is not graded', async () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
    const exercise = tracingActivities[0]!
    const wrapper = mount(TracingPad, { props: { exercise }, global })
    const canvas = wrapper.get('[data-testid="tracing-canvas"]')
    const submit = wrapper.get('[data-testid="activity-submit"]')

    expect(wrapper.text()).toContain('not graded')
    expect(submit.attributes('disabled')).toBeDefined()

    const pointer = async (type: string, clientX = 0, clientY = 0) => {
      // jsdom has no PointerEvent: use a MouseEvent carrying a pointerId.
      const event = new MouseEvent(type, { bubbles: true, cancelable: true, clientX, clientY, button: 0 })
      Object.defineProperty(event, 'pointerId', { value: 1 })
      canvas.element.dispatchEvent(event)
      await nextTick()
    }
    await pointer('pointerdown', 10, 10)
    await pointer('pointermove', 40, 60)
    await pointer('pointerup')
    expect(wrapper.get('[data-testid="tracing-stroke-count"]').text()).toBe('Strokes: 1')
    expect(submit.attributes('disabled')).toBeUndefined()

    await wrapper.get('[data-testid="tracing-clear"]').trigger('click')
    expect(wrapper.get('[data-testid="tracing-stroke-count"]').text()).toBe('Strokes: 0')

    await canvas.trigger('keydown', { key: ' ' })
    await canvas.trigger('keydown', { key: 'ArrowRight' })
    await canvas.trigger('keydown', { key: 'Enter' })
    await submit.trigger('click')
    expect(wrapper.emitted('submit')).toEqual([[{ type: 'TRACING_EXERCISE', body: { strokeCount: 1 } }]])
    vi.restoreAllMocks()
  })
})

describe('ActivityResult', () => {
  const failed: StudentActivityCompletionResponse = {
    activityId: 6,
    type: 'STORY_ACTIVITY',
    passed: false,
    correctCount: 1,
    totalCount: 2,
    earnedPoints: 0,
    totalPoints: 100,
    firstCompletion: false,
    results: [],
  }

  it('shows the score, which items were wrong, no answers, and offers a retry', async () => {
    const wrapper = mount(ActivityResult, {
      props: {
        result: failed,
        type: 'STORY_ACTIVITY',
        rows: [
          { key: '1', label: 'Who says hello first?', correct: true, explanation: null },
          { key: '2', label: 'Where do they go?', correct: false, explanation: null },
        ],
      },
      global,
    })

    expect(wrapper.text()).toContain('1 of 2 correct')
    expect(wrapper.findAll('[data-testid="activity-result-item"]').map((row) => row.text())).toEqual([
      'Who says hello first? Correct',
      'Where do they go? Not yet',
    ])
    expect(wrapper.text()).toContain('XP is awarded the first time every item is correct.')
    await wrapper.get('[data-testid="activity-retry"]').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('reports earned XP and first completion, or a preview that is not saved', () => {
    const passed = { ...failed, passed: true, correctCount: 2, earnedPoints: 30, firstCompletion: true }
    const live = mount(ActivityResult, { props: { result: passed, type: 'MATCHING_GAME' }, global })
    expect(live.text()).toContain('+30 XP earned')
    expect(live.text()).toContain('First completion')

    const preview = mount(ActivityResult, { props: { result: passed, type: 'MATCHING_GAME', preview: true }, global })
    expect(preview.text()).toContain('Nothing is saved')
    expect(preview.text()).not.toContain('+30 XP')

    const tracing = mount(ActivityResult, { props: { result: passed, type: 'TRACING_EXERCISE' }, global })
    expect(tracing.text()).toContain('not graded')
    expect(tracing.text()).not.toContain('2 of 2')
  })
})
