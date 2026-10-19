import { describe, expect, it } from 'vitest'

import { demoActivitySources, dragDropActivities, matchingActivities, storyActivities } from '@/demo/data'
import { gradeDemoActivity } from '@/demo/activityKeys'
import type { StudentActivityCompletionResponse, StudentStoryQuestionResponse } from '@/types/api'

import {
  assignMatch,
  buildDragDropCompletion,
  buildListeningCompletion,
  buildMatchingCompletion,
  buildStoryCompletion,
  buildTracingCompletion,
  cjkLang,
  completionOutcome,
  dragDropResultLabels,
  isDragDropReady,
  isListeningReady,
  isMatchingReady,
  isStoryReady,
  isTracingReady,
  listeningResultLabels,
  mapResultsToItems,
  matchingResultLabels,
  placeItem,
  storyResultLabels,
  unassignMatch,
} from './activityCompletion'

const leftItems = [
  { id: 11, text: '你好' },
  { id: 12, text: '谢谢' },
]
const rightOptions = [{ text: 'thank you' }, { text: 'hello' }]

describe('text helpers', () => {
  it('marks Chinese text for the zh-CN lang attribute only', () => {
    expect(cjkLang('你好')).toBe('zh-CN')
    expect(cjkLang('Say 谢谢')).toBe('zh-CN')
    expect(cjkLang('thank you')).toBeUndefined()
    expect(cjkLang(null)).toBeUndefined()
  })
})

describe('matching completion', () => {
  it('pairs left items with right options and moves a reused option', () => {
    let selection = assignMatch({}, 11, 0)
    expect(selection).toEqual({ 11: 0 })
    selection = assignMatch(selection, 12, 0)
    expect(selection).toEqual({ 12: 0 })
    selection = assignMatch(selection, 11, 1)
    expect(selection).toEqual({ 11: 1, 12: 0 })
    expect(unassignMatch(selection, 12)).toEqual({ 11: 1 })
  })

  it('is ready only when every left item has a valid option and sends the option text', () => {
    expect(isMatchingReady(leftItems, rightOptions, { 11: 1 })).toBe(false)
    expect(isMatchingReady(leftItems, rightOptions, { 11: 1, 12: 5 })).toBe(false)
    expect(isMatchingReady([], rightOptions, {})).toBe(false)
    expect(isMatchingReady(leftItems, rightOptions, { 11: 1, 12: 0 })).toBe(true)
    expect(buildMatchingCompletion(leftItems, rightOptions, { 12: 0, 11: 1 })).toEqual({
      matches: [
        { leftItemId: 11, rightText: 'hello' },
        { leftItemId: 12, rightText: 'thank you' },
      ],
    })
  })
})

describe('drag-and-drop completion', () => {
  const items = [
    { id: 1, dragText: '我' },
    { id: 2, dragText: '叫' },
  ]
  const zones = ['Subject', 'Verb']

  it('places and removes items and only sends known zones', () => {
    let placements = placeItem({}, 1, 'Subject')
    placements = placeItem(placements, 2, 'Nowhere')
    expect(isDragDropReady(items, zones, placements)).toBe(false)
    expect(buildDragDropCompletion(items, zones, placements)).toEqual({ placements: [{ itemId: 1, zone: 'Subject' }] })

    placements = placeItem(placements, 2, 'Verb')
    expect(isDragDropReady(items, zones, placements)).toBe(true)
    expect(buildDragDropCompletion(items, zones, placements)).toEqual({
      placements: [
        { itemId: 1, zone: 'Subject' },
        { itemId: 2, zone: 'Verb' },
      ],
    })
    expect(placeItem(placements, 1, null)).toEqual({ 2: 'Verb' })
  })
})

describe('listening completion', () => {
  const exercise = {
    freeResponse: false,
    questions: [
      { index: 0, questionText: 'Which word means "thank you"?', options: ['你好', '谢谢'] },
      { index: 1, questionText: 'Type the pinyin for 你好.', options: [] },
    ],
  }

  it('needs an answer for every question and trims typed answers', () => {
    expect(isListeningReady(exercise, { 0: '谢谢', 1: '   ' }, '')).toBe(false)
    expect(isListeningReady(exercise, { 0: '谢谢', 1: ' ni hao ' }, '')).toBe(true)
    expect(buildListeningCompletion(exercise, { 0: '谢谢', 1: ' ni hao ' }, '')).toEqual({
      answers: [
        { questionIndex: 0, answerText: '谢谢' },
        { questionIndex: 1, answerText: 'ni hao' },
      ],
    })
    expect(isListeningReady({ freeResponse: false, questions: [] }, {}, '')).toBe(false)
  })

  it('sends a free response as responseText', () => {
    const free = { freeResponse: true, questions: [] }
    expect(isListeningReady(free, {}, '  ')).toBe(false)
    expect(isListeningReady(free, {}, 'I heard ni hao')).toBe(true)
    expect(buildListeningCompletion(free, {}, ' I heard ni hao ')).toEqual({ responseText: 'I heard ni hao' })
  })
})

describe('story and tracing completion', () => {
  const questions: StudentStoryQuestionResponse[] = [
    {
      id: 1,
      questionText: 'Who says hello first?',
      options: [
        { index: 0, text: 'Xiao Ming' },
        { index: 1, text: 'Xiao Hong' },
      ],
    },
    {
      id: 2,
      questionText: 'Where do they go?',
      options: [
        { index: 0, text: 'Class' },
        { index: 1, text: 'Park' },
      ],
    },
  ]

  it('sends option indexes by question id once every question is answered', () => {
    expect(isStoryReady(questions, { 1: 0 })).toBe(false)
    expect(isStoryReady(questions, { 1: 0, 2: 7 })).toBe(false)
    expect(isStoryReady(questions, { 1: 0, 2: 1 })).toBe(true)
    expect(buildStoryCompletion(questions, { 2: 1, 1: 0 })).toEqual({
      answers: [
        { questionId: 1, optionIndex: 0 },
        { questionId: 2, optionIndex: 1 },
      ],
    })
  })

  it('requires at least one stroke for tracing', () => {
    expect(isTracingReady(0)).toBe(false)
    expect(isTracingReady(2)).toBe(true)
    expect(buildTracingCompletion(3)).toEqual({ strokeCount: 3 })
  })
})

describe('mapping results back to items', () => {
  const result: StudentActivityCompletionResponse = {
    activityId: 6,
    type: 'STORY_ACTIVITY',
    passed: false,
    correctCount: 1,
    totalCount: 3,
    earnedPoints: 0,
    totalPoints: 120,
    firstCompletion: false,
    results: [
      { key: '2', correct: false, explanation: 'hidden' },
      { key: '1', correct: true, explanation: null },
      { key: 'extra', correct: false },
    ],
  }

  it('keeps the item order, labels unknown keys as null and hides explanations unless passed', () => {
    const rows = mapResultsToItems(result.results, storyResultLabels(questions()), false)
    expect(rows).toEqual([
      { key: '1', label: 'Q1', correct: true, explanation: null },
      { key: '2', label: 'Q2', correct: false, explanation: null },
      { key: 'extra', label: null, correct: false, explanation: null },
    ])
    expect(mapResultsToItems(result.results, storyResultLabels(questions()), true)[1]?.explanation).toBe('hidden')
  })

  it('builds labels for each activity type from the keys the server uses', () => {
    expect(matchingResultLabels([{ id: 5, text: '你好' }])).toEqual([{ key: '5', label: '你好' }])
    expect(dragDropResultLabels([{ id: 9, dragText: '我' }])).toEqual([{ key: '9', label: '我' }])
    expect(listeningResultLabels([{ index: 0, questionText: 'Q', options: [] }])).toEqual([{ key: '0', label: 'Q' }])
  })

  it('describes the XP outcome', () => {
    expect(completionOutcome(result, true)).toBe('preview')
    expect(completionOutcome(result, false)).toBe('notYet')
    expect(completionOutcome({ ...result, passed: true, earnedPoints: 30, firstCompletion: true }, false)).toBe(
      'earned',
    )
    expect(completionOutcome({ ...result, passed: true, firstCompletion: true }, false)).toBe('completed')
    expect(completionOutcome({ ...result, passed: true }, false)).toBe('alreadyCompleted')
  })

  function questions(): StudentStoryQuestionResponse[] {
    return [
      { id: 1, questionText: 'Q1', options: [] },
      { id: 2, questionText: 'Q2', options: [] },
    ]
  }
})

describe('preview grading of built requests', () => {
  it('grades a demo matching attempt built from the student shape without exposing answers', () => {
    const game = matchingActivities[0]!
    const selection = Object.fromEntries(game.leftItems.map((item, index) => [item.id, index]))
    const graded = gradeDemoActivity(demoActivitySources, game.id, {
      type: 'MATCHING_GAME',
      body: buildMatchingCompletion(game.leftItems, game.rightOptions, selection),
    })
    expect(graded?.totalCount).toBe(game.leftItems.length)
    expect(graded?.earnedPoints).toBe(0)
    const rows = mapResultsToItems(graded!.results, matchingResultLabels(game.leftItems), graded!.passed)
    expect(rows.map((row) => row.label)).toEqual(game.leftItems.map((item) => item.text))
  })

  it('passes a demo story when the authored options are chosen and only then returns explanations', () => {
    const story = storyActivities[0]!
    const source = demoActivitySources.story.find((item) => item.id === story.id)!
    const correct = Object.fromEntries(source.questions.map((q) => [q.id, q.correctOptionIndex ?? 0]))
    const passed = gradeDemoActivity(demoActivitySources, story.id, {
      type: 'STORY_ACTIVITY',
      body: buildStoryCompletion(story.questions, correct),
    })
    expect(passed?.passed).toBe(true)
    expect(passed?.results.every((entry) => entry.explanation)).toBe(true)

    const wrong = Object.fromEntries(source.questions.map((q) => [q.id, (q.correctOptionIndex ?? 0) === 0 ? 1 : 0]))
    const failed = gradeDemoActivity(demoActivitySources, story.id, {
      type: 'STORY_ACTIVITY',
      body: buildStoryCompletion(story.questions, wrong),
    })
    expect(failed?.passed).toBe(false)
    expect(failed?.results.every((entry) => !entry.explanation)).toBe(true)
  })

  it('completes demo drag-and-drop and tracing attempts', () => {
    const activity = dragDropActivities[0]!
    const source = demoActivitySources.dragDrop.find((item) => item.id === activity.id)!
    const placements = Object.fromEntries(source.items.map((item) => [item.id, item.targetZone]))
    const graded = gradeDemoActivity(demoActivitySources, activity.id, {
      type: 'DRAG_DROP_ACTIVITY',
      body: buildDragDropCompletion(activity.items, activity.zones, placements),
    })
    expect(graded?.passed).toBe(true)
    expect(
      gradeDemoActivity(demoActivitySources, 4, { type: 'TRACING_EXERCISE', body: buildTracingCompletion(2) })?.passed,
    ).toBe(true)
  })
})
