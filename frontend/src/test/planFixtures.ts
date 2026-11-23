/** Shared fixtures of the learning plan tests: API responses shaped like the backend's (dto/plan). */
import type { LevelCheckStep } from '@/types/learn'
import type { HomeView, LearningPlan, PlanOptions, PlanStep, PlanSummary } from '@/types/plan'

import { today } from './learnFixtures'

export function planOptions(): PlanOptions {
  return {
    reasons: [
      { key: 'TRAVEL', goals: ['INTRODUCE_MYSELF', 'ORDER_FOOD', 'ASK_DIRECTIONS', 'SHORT_CHATS'] },
      { key: 'WORK', goals: ['GREET_CUSTOMERS', 'SMALL_TALK', 'TALK_ABOUT_WORK'] },
      { key: 'OTHER', goals: ['INTRODUCE_MYSELF', 'SHORT_CHATS'] },
    ],
    paces: [
      { key: 'UNDER_30_MIN', minMinutesPerDay: 0, maxMinutesPerDay: 30 },
      { key: 'MINUTES_30_TO_60', minMinutesPerDay: 30, maxMinutesPerDay: 60 },
      { key: 'HOURS_1_TO_2', minMinutesPerDay: 60, maxMinutesPerDay: 120 },
      { key: 'HOURS_2_PLUS', minMinutesPerDay: 120, maxMinutesPerDay: null },
    ],
    checkStarts: {
      NEW_TO_MANDARIN: 'NEW_TO_MANDARIN',
      A_LITTLE: 'BEGINNER_1',
      THE_BASICS: 'BEGINNER_2',
      NOT_SURE: 'BEGINNER_1',
    },
    goalTextMax: 200,
    today: '2026-05-18',
  }
}

export function planStep(overrides: Partial<PlanStep> = {}): PlanStep {
  return {
    position: 1,
    kind: 'LEARN_UNIT',
    refId: 11,
    title: 'Say hello',
    titleZh: '你好',
    unitNumber: 1,
    lessonCount: 7,
    completedCount: 2,
    percent: 29,
    status: 'IN_PROGRESS',
    joined: false,
    quickReview: true,
    weekFrom: 1,
    weekTo: 1,
    bestAfterUnit: null,
    reason: 'You know the greetings already, so finish numbers and polite phrases.',
    goals: ['INTRODUCE_MYSELF'],
    nextLesson: { id: 103, title: 'Introduce yourself', kind: 'LESSON' },
    ...overrides,
  }
}

export function learningPlan(overrides: Partial<LearningPlan> = {}): LearningPlan {
  return {
    aiAssisted: true,
    goal: {
      title: 'Travel to Shanghai',
      summary: 'You want enough Mandarin for your trip: order food and ask for directions.',
      reason: 'TRAVEL',
      reasonText: null,
      picks: ['ORDER_FOOD', 'ASK_DIRECTIONS', 'SHORT_CHATS'],
      goals: ['ORDER_FOOD', 'ASK_DIRECTIONS', 'SHORT_CHATS'],
      ownWords: 'I am going to Shanghai with friends.',
      deadline: '2026-07-18',
      focus: ['LISTENING', 'SPEAKING'],
      workOn: ['Listening', 'Food words'],
    },
    level: {
      placed: 'BEGINNER_1',
      target: 'BEGINNER_2',
      knownSkills: ['VOCABULARY'],
      newSkills: ['LISTENING'],
    },
    weeks: { current: 2, total: 10, content: 8 },
    progress: { percent: 18, status: 'ON_TRACK', daysThisWeek: 2, daysBehind: 0 },
    pace: { key: 'HOURS_1_TO_2', minMinutesPerDay: 60, maxMinutesPerDay: 120 },
    steps: [
      planStep(),
      planStep({
        position: 2,
        kind: 'CLASS',
        refId: 21,
        title: 'Mandarin for Campus Life',
        titleZh: null,
        unitNumber: null,
        lessonCount: 5,
        completedCount: 2,
        percent: 40,
        status: 'IN_MY_CLASSES',
        joined: true,
        quickReview: false,
        weekFrom: 1,
        weekTo: 5,
        reason: 'You already have this class.',
        goals: [],
        nextLesson: { id: 501, title: 'Meeting Classmates', kind: 'LESSON' },
      }),
      planStep({
        position: 3,
        refId: 12,
        title: 'Food and drink',
        titleZh: '吃喝',
        unitNumber: 2,
        lessonCount: 6,
        completedCount: 0,
        percent: 0,
        status: 'NEXT',
        quickReview: false,
        weekFrom: 2,
        weekTo: 5,
        reason: null,
        goals: ['ORDER_FOOD'],
        nextLesson: null,
      }),
      planStep({
        position: 4,
        kind: 'CLASS',
        refId: 22,
        title: 'Travel Mandarin',
        titleZh: null,
        unitNumber: null,
        lessonCount: 0,
        completedCount: 0,
        percent: 0,
        status: 'OPTIONAL',
        quickReview: false,
        weekFrom: 6,
        weekTo: 8,
        reason: 'Practice for short chats on a trip.',
        goals: ['SHORT_CHATS'],
        nextLesson: null,
      }),
    ],
    outcomes: ['Order food and drinks', 'Ask for directions'],
    milestone: null,
    thisWeek: [
      { kind: 'LEARN_DONE', title: 'Polite phrases', doneOn: '2026-05-16' },
      { kind: 'LEARN_NEXT', title: 'Introduce yourself', minutes: 4, lessonId: 103 },
      { kind: 'PRACTICE', title: 'At the Café', practiceType: 'LISTENING', practiceSetId: 7 },
      { kind: 'XIAOMEI', done: true },
    ],
    kept: { lessons: 4, practiceSets: 4 },
    today: '2026-05-18',
    pickedClassId: null,
    ...overrides,
  }
}

export function planWithClassChoice(): LearningPlan {
  const plan = learningPlan()
  const secondClass = { ...plan.steps[3]!, position: 5, refId: 23, title: 'Shopping and Prices' }
  return { ...plan, steps: [...plan.steps, secondClass] }
}

export function planSummary(overrides: Partial<PlanSummary> = {}): PlanSummary {
  return {
    goalTitle: 'Travel to Shanghai',
    reason: 'TRAVEL',
    deadline: '2026-07-18',
    milestoneDate: null,
    week: 2,
    totalWeeks: 10,
    percent: 18,
    status: 'ON_TRACK',
    daysBehind: 0,
    level: 'BEGINNER_1',
    targetLevel: 'BEGINNER_2',
    optionalClass: { title: 'Travel Mandarin', weekFrom: 6, bestAfterUnit: 3 },
    practice: { type: 'LISTENING', setId: 7, title: 'At the Café' },
    today: '2026-05-18',
    ...overrides,
  }
}

export function homeView(overrides: Partial<HomeView> = {}): HomeView {
  return {
    firstName: 'Amira',
    today: today(),
    firstTime: false,
    email: { email: 'student@fyp.local', confirmed: true },
    setupState: 'COMPLETED',
    next: {
      lessonId: 103,
      title: 'Introduce yourself',
      kind: 'LESSON',
      level: 1,
      unitNumber: 1,
      unitTitle: 'Say hello',
      unitTitleZh: '你好',
      minutes: 4,
      xpReward: 10,
      character: '我',
      phrase: { hanzi: '我叫小美。', meaning: 'My name is Xiaomei.' },
      stops: [
        { id: 101, position: 1, state: 'DONE', current: false },
        { id: 102, position: 2, state: 'DONE', current: false },
        { id: 103, position: 3, state: 'AVAILABLE', current: true },
        { id: 104, position: 4, state: 'LOCKED', current: false },
      ],
      quickReview: true,
      unitMinutesLeft: 20,
      inPlan: true,
    },
    plan: planSummary(),
    ...overrides,
  }
}

/** One step of the level check: a question to answer, or the placement when done. */
export function checkStep(overrides: Partial<LevelCheckStep> = {}): LevelCheckStep {
  return {
    feedback: null,
    next: {
      id: 1,
      prompt: 'What does this mean?',
      hanzi: '你好',
      pinyin: 'nǐ hǎo',
      audioText: null,
      skill: 'VOCABULARY',
      options: [
        { id: 11, text: 'Thank you' },
        { id: 12, text: 'Hello' },
      ],
    },
    done: false,
    result: null,
    answeredCount: 0,
    maxQuestions: 8,
    ...overrides,
  }
}

export function checkDone(overrides: Partial<LevelCheckStep> = {}): LevelCheckStep {
  return checkStep({
    feedback: {
      correct: true,
      skipped: false,
      correctAnswer: 'Hello',
      explanation: '你好 (nǐ hǎo) means “Hello”.',
    },
    next: null,
    done: true,
    result: {
      level: 'BEGINNER_1',
      knownSkills: ['VOCABULARY'],
      newSkills: ['LISTENING'],
    },
    answeredCount: 3,
    ...overrides,
  })
}
