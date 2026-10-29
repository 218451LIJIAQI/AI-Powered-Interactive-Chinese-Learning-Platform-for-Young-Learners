/**
 * Shared fixtures of the learning plan tests: a memory router with the routes Home, the setup and the plan page link
 * to, and API responses shaped like the backend's (dto/plan).
 */
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import type { LevelCheckStep } from '@/types/learn'
import type { HomeView, LearningPlan, PlanOptions, PlanStep, PlanSummary } from '@/types/plan'

import { today } from './learnFixtures'

const Empty = { template: '<div />' }

export async function planRouter(path: string): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/student/home', name: 'student-home', component: Empty },
      { path: '/student/setup', name: 'student-setup', component: Empty },
      { path: '/student/learn', name: 'student-learn', component: Empty },
      { path: '/student/learn/lessons/:lessonId', name: 'student-learn-lesson', component: Empty },
      { path: '/student/practice/review', name: 'student-practice-review', component: Empty },
      { path: '/student/practice/:type?', name: 'student-practice', component: Empty },
      { path: '/student/ai/:tab?', name: 'student-ai', component: Empty },
      { path: '/student/classes/find', name: 'student-classes-find', component: Empty },
      { path: '/student/classes/live', name: 'student-live-sessions', component: Empty },
      { path: '/student/classes/:classId', name: 'student-class', component: Empty },
      { path: '/student/progress', name: 'student-progress', component: Empty },
      { path: '/student/progress/plan', name: 'student-plan', component: Empty },
      { path: '/student/progress/plan/level-check', name: 'student-level-check', component: Empty },
    ],
  })
  await router.push(path)
  await router.isReady()
  return router
}

export function planOptions(): PlanOptions {
  return {
    reasons: [
      { key: 'TRAVEL', goals: ['INTRODUCE_MYSELF', 'ORDER_FOOD', 'ASK_DIRECTIONS', 'SHORT_CHATS'] },
      { key: 'WORK', goals: ['GREET_CUSTOMERS', 'SMALL_TALK', 'TALK_ABOUT_WORK'] },
      { key: 'OTHER', goals: ['INTRODUCE_MYSELF', 'SHORT_CHATS'] },
    ],
    paces: [
      { key: 'LIGHT', minutesPerDay: 10, daysPerWeek: 3, dailyGoalXp: 10 },
      { key: 'REGULAR', minutesPerDay: 15, daysPerWeek: 5, dailyGoalXp: 20 },
      { key: 'SERIOUS', minutesPerDay: 20, daysPerWeek: 7, dailyGoalXp: 30 },
      { key: 'INTENSE', minutesPerDay: 30, daysPerWeek: 7, dailyGoalXp: 30 },
    ],
    focuses: ['SPEAKING', 'LISTENING', 'READING', 'WRITING', 'VOCABULARY', 'CONVERSATION', 'BALANCED'],
    goalTextMax: 200,
  }
}

function planStep(overrides: Partial<PlanStep> = {}): PlanStep {
  return {
    position: 1,
    kind: 'LEARN_UNIT',
    refId: 11,
    title: 'Say hello',
    titleZh: '你好',
    unitNumber: 1,
    hskLevel: 1,
    iconKey: 'hand',
    lessonCount: 7,
    completedCount: 2,
    percent: 29,
    status: 'IN_PROGRESS',
    joined: false,
    quickReview: true,
    weekFrom: 1,
    weekTo: 1,
    reason: 'You know the greetings already, so finish numbers and polite phrases.',
    goals: ['INTRODUCE_MYSELF'],
    teacherName: null,
    nextLesson: { id: 103, title: 'Introduce yourself' },
    nextSessionAt: null,
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
      deadline: '2026-07-05',
      focus: ['LISTENING', 'CONVERSATION'],
      workOn: ['Listening', 'Food words'],
    },
    level: {
      placed: 'BEGINNER_1',
      target: 'BEGINNER_1',
      headStart: true,
      knownSkills: ['VOCABULARY'],
      newSkills: ['LISTENING'],
      checkedAt: '2026-05-11T20:10:00',
    },
    weeks: { current: 2, total: 8, content: 5 },
    progress: { percent: 18, status: 'ON_TRACK', daysThisWeek: 3, paceDays: 5 },
    pace: { key: 'REGULAR', minutesPerDay: 15, daysPerWeek: 5, dailyGoalXp: 30 },
    steps: [
      planStep(),
      planStep({
        position: 2,
        kind: 'CLASS',
        refId: 21,
        title: 'Mandarin for Beginners',
        titleZh: null,
        unitNumber: null,
        hskLevel: null,
        iconKey: null,
        lessonCount: 4,
        completedCount: 1,
        percent: 22,
        status: 'IN_MY_CLASSES',
        joined: true,
        quickReview: false,
        weekFrom: 1,
        weekTo: 5,
        reason: 'You already have this class.',
        goals: [],
        teacherName: 'Li Wei',
        nextLesson: null,
      }),
      planStep({
        position: 3,
        refId: 12,
        title: 'Food and drink',
        titleZh: '吃喝',
        unitNumber: 2,
        iconKey: 'utensils',
        lessonCount: 6,
        completedCount: 0,
        percent: 0,
        status: 'NEXT',
        quickReview: false,
        weekFrom: 2,
        weekTo: 3,
        reason: null,
        goals: ['ORDER_FOOD'],
        nextLesson: null,
      }),
      planStep({
        position: 4,
        kind: 'CLASS',
        refId: 22,
        title: 'Everyday Conversations',
        titleZh: null,
        unitNumber: null,
        hskLevel: null,
        iconKey: null,
        lessonCount: 0,
        completedCount: 0,
        percent: 0,
        status: 'OPTIONAL',
        quickReview: false,
        weekFrom: 2,
        weekTo: 5,
        reason: 'Live practice for short chats.',
        goals: ['SHORT_CHATS'],
        teacherName: 'Wong Siew Ling',
        nextLesson: null,
      }),
    ],
    outcomes: ['Order food and drinks', 'Ask for directions'],
    coverage: [
      { goal: 'ORDER_FOOD', learn: true, classId: 22, classTitle: 'Everyday Conversations' },
      { goal: 'ASK_DIRECTIONS', learn: true, classId: null, classTitle: null },
      { goal: 'SHORT_CHATS', learn: false, classId: 22, classTitle: 'Everyday Conversations' },
    ],
    milestone: null,
    thisWeek: [
      {
        kind: 'LEARN_DONE',
        title: 'Polite phrases',
        done: true,
        doneOn: '2026-05-16',
        minutes: 4,
        lessonId: 102,
        practiceType: null,
        practiceSetId: null,
      },
      {
        kind: 'LEARN_NEXT',
        title: 'Introduce yourself',
        done: false,
        doneOn: null,
        minutes: 4,
        lessonId: 103,
        practiceType: null,
        practiceSetId: null,
      },
      {
        kind: 'PRACTICE',
        title: 'At the Café',
        done: false,
        doneOn: null,
        minutes: null,
        lessonId: null,
        practiceType: 'LISTENING',
        practiceSetId: 7,
      },
      {
        kind: 'XIAOMEI',
        title: null,
        done: true,
        doneOn: null,
        minutes: null,
        lessonId: null,
        practiceType: null,
        practiceSetId: null,
      },
    ],
    kept: { lessons: 4, practiceSets: 4, classes: 1 },
    ...overrides,
  }
}

export function planSummary(overrides: Partial<PlanSummary> = {}): PlanSummary {
  return {
    goalTitle: 'Travel to Shanghai',
    reason: 'TRAVEL',
    deadline: '2026-07-05',
    week: 2,
    totalWeeks: 8,
    percent: 18,
    status: 'ON_TRACK',
    level: 'BEGINNER_1',
    targetLevel: 'BEGINNER_2',
    goalUnit: { number: 3, title: 'Getting around', titleZh: '出行' },
    classSteps: [
      { classId: 21, position: 2 },
      { classId: 22, position: 4 },
    ],
    optionalClass: { classId: 22, title: 'Everyday Conversations', weekFrom: 2 },
    firstStep: {
      title: 'Say hello',
      lessonsLeft: 7,
      minutesLeft: 28,
      quickReview: true,
    },
    practice: { type: 'LISTENING', setId: 7, title: 'At the Café' },
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
      unitNumber: 1,
      unitTitle: 'Say hello',
      unitTitleZh: '你好',
      position: 3,
      unitLessonCount: 7,
      completedInUnit: 2,
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
    },
    liveToday: {
      id: 5,
      title: 'Greetings 问候',
      classId: 21,
      classTitle: 'Mandarin for Beginners',
      lessonId: null,
      lessonTitle: null,
      teacherName: 'Li Wei',
      startsAt: '2026-05-18T16:00:00+08:00',
      endsAt: '2026-05-18T17:00:00+08:00',
      platform: 'ZOOM',
      status: 'SCHEDULED',
      booked: true,
      bookedCount: 6,
      capacity: 20,
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
      options: [
        { id: 11, text: 'Thank you' },
        { id: 12, text: 'Hello' },
      ],
    },
    done: false,
    result: null,
    answeredCount: 0,
    maxQuestions: 6,
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
      knownSkills: ['VOCABULARY'],
      newSkills: ['LISTENING'],
      answeredCount: 3,
    },
    answeredCount: 3,
    ...overrides,
  })
}
