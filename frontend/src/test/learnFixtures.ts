/**
 * Shared fixtures of the Learn module tests: a memory router with the routes the Learn, Practice and Progress pages
 * link to, and API responses shaped like the backend's (dto/learn).
 */
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import type {
  AnswerCheck,
  LearnPath,
  LessonPlay,
  LessonResult,
  PathStop,
  PathUnit,
  PracticeResult,
  PracticeSet,
  ProgressOverview,
  TodaySummary,
  XpResult,
} from '@/types/learn'

const Empty = { template: '<div />' }

/** The routes the module's pages link to; the page under test is mounted directly. */
export async function learnRouter(path: string): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/student/learn', name: 'student-learn', component: Empty },
      { path: '/student/learn/lessons/:lessonId', name: 'student-learn-lesson', component: Empty },
      { path: '/student/practice/review', name: 'student-practice-review', component: Empty },
      { path: '/student/practice/:type?', name: 'student-practice', component: Empty },
      { path: '/student/ai/:tab?', name: 'student-ai', component: Empty },
      { path: '/student/classes', name: 'student-classes', component: Empty },
      { path: '/student/progress', name: 'student-progress', component: Empty },
      { path: '/student/progress/plan', name: 'student-plan', component: Empty },
      { path: '/student/setup', name: 'student-setup', component: Empty },
      { path: '/admin/content', name: 'admin-content-review', component: Empty },
      { path: '/admin/content/classes', name: 'admin-teacher-classes', component: Empty },
      { path: '/admin/content/learning-path', name: 'admin-learning-path', component: Empty },
      { path: '/admin/content/practice', name: 'admin-practice-library', component: Empty },
    ],
  })
  await router.push(path)
  await router.isReady()
  return router
}

export function xpResult(overrides: Partial<XpResult> = {}): XpResult {
  return {
    xpEarned: 10,
    bonusXp: 0,
    totalXp: 60,
    todayXp: 10,
    dailyGoalXp: 20,
    goalReached: false,
    goalJustReached: false,
    currentStreak: 2,
    newBadges: [],
    ...overrides,
  }
}

export function today(overrides: Partial<TodaySummary> = {}): TodaySummary {
  return {
    date: '2026-05-18',
    dailyGoalXp: 30,
    todayXp: 20,
    goalReached: false,
    currentStreak: 7,
    week: [
      { date: '2026-05-12', goalReached: true },
      { date: '2026-05-13', goalReached: true },
      { date: '2026-05-14', goalReached: true },
      { date: '2026-05-15', goalReached: true },
      { date: '2026-05-16', goalReached: true },
      { date: '2026-05-17', goalReached: true },
      { date: '2026-05-18', goalReached: false },
    ],
    setsDoneToday: 2,
    mistakesWaiting: 6,
    ...overrides,
  }
}

function stop(id: number, position: number, title: string, overrides: Partial<PathStop> = {}): PathStop {
  return {
    id,
    position,
    title,
    kind: 'LESSON',
    minutes: 5,
    xpReward: 10,
    state: 'LOCKED',
    current: false,
    unlockAfter: null,
    ...overrides,
  }
}

function unit(id: number, number: number, overrides: Partial<PathUnit> = {}): PathUnit {
  return {
    id,
    number,
    title: `Unit title ${number}`,
    titleZh: '你好',
    state: 'LOCKED',
    lessonCount: 3,
    completedCount: 0,
    unlockAfterUnit: number - 1,
    lessons: [],
    ...overrides,
  }
}

/** Unit 1 with two lessons done and the third current, Unit 2 locked; Level 2 still locked. */
export function learnPath(overrides: Partial<LearnPath> = {}): LearnPath {
  return {
    totalLessons: 6,
    completedLessons: 2,
    learnMistakesWaiting: 4,
    nextLesson: {
      id: 13,
      title: 'Your name',
      kind: 'LESSON',
      unitId: 1,
      unitNumber: 1,
      unitTitle: 'Greetings',
      unitTitleZh: '你好',
      position: 3,
      unitLessonCount: 3,
      minutes: 5,
      xpReward: 10,
    },
    levels: [
      {
        level: 1,
        hskLevel: 1,
        lessonCount: 6,
        locked: false,
        units: [
          unit(1, 1, {
            title: 'Greetings',
            state: 'ACTIVE',
            completedCount: 2,
            unlockAfterUnit: null,
            lessons: [
              stop(11, 1, 'Hello and goodbye', { state: 'DONE' }),
              stop(12, 2, 'Polite phrases', { state: 'DONE' }),
              stop(13, 3, 'Your name', { state: 'AVAILABLE', current: true }),
            ],
          }),
          unit(2, 2, {
            title: 'Food',
            lessons: [
              stop(21, 1, 'Fruit', { unlockAfter: 'Your name' }),
              stop(22, 2, 'Drinks', { unlockAfter: 'Fruit' }),
              stop(23, 3, 'Ordering', { kind: 'BONUS', unlockAfter: 'Drinks' }),
            ],
          }),
        ],
        checkpoint: {
          id: 90,
          title: 'Level 1 checkpoint',
          questionCount: 10,
          xpReward: 50,
          passPercent: 70,
          state: 'LOCKED',
          unlockAfter: 'Ordering',
        },
      },
      {
        level: 2,
        hskLevel: 2,
        lessonCount: 4,
        locked: true,
        units: [unit(3, 3, { title: 'Travel', lessonCount: 4 })],
        checkpoint: null,
      },
    ],
    ...overrides,
  }
}

/** A two-question lesson: a choice and a sentence to build. */
export function lessonPlay(overrides: Partial<LessonPlay> = {}): LessonPlay {
  return {
    id: 13,
    title: 'Your name',
    kind: 'LESSON',
    unitId: 1,
    unitNumber: 1,
    unitTitle: 'Greetings',
    position: 3,
    unitLessonCount: 3,
    minutes: 5,
    xpReward: 10,
    passPercent: null,
    questions: [
      {
        id: 101,
        answerKind: 'CHOICE',
        type: 'MEANING',
        prompt: null,
        hanzi: '名字',
        pinyin: 'míngzi',
        meaning: null,
        audioText: '名字',
        options: [
          { text: 'name', pinyin: null },
          { text: 'teacher', pinyin: null },
        ],
        tiles: [],
      },
      {
        id: 102,
        answerKind: 'BUILD',
        type: 'TRANSLATE_BUILD',
        prompt: null,
        hanzi: null,
        pinyin: null,
        meaning: 'My name is Amira.',
        audioText: null,
        options: [],
        tiles: ['叫', '我', 'Amira', '你'],
      },
    ],
    ...overrides,
  }
}

export function answerCheck(overrides: Partial<AnswerCheck> = {}): AnswerCheck {
  return { correct: true, correctAnswer: 'name', pinyin: 'míngzi', meaning: 'name', explanation: null, ...overrides }
}

export function lessonResult(overrides: Partial<LessonResult> = {}): LessonResult {
  return {
    lessonId: 13,
    title: 'Your name',
    kind: 'LESSON',
    unitNumber: 1,
    position: 3,
    passed: true,
    passPercent: null,
    correctCount: 1,
    questionCount: 2,
    accuracy: 50,
    durationSeconds: 75,
    nextLesson: { id: 21, title: 'Fruit', unlockedNow: true },
    xp: xpResult({ newBadges: ['UNIT_CHAMPION'] }),
    ...overrides,
  }
}

export function flashcardSet(overrides: Partial<PracticeSet> = {}): PracticeSet {
  const card = (key: string, hanzi: string, meaning: string) => ({
    key,
    prompt: null,
    hanzi,
    pinyin: null,
    meaning,
    audioText: hanzi,
    example: null,
    exampleMeaning: null,
    options: [],
    tiles: [],
  })
  return {
    id: 5,
    type: 'FLASHCARDS',
    title: 'Greetings cards',
    stage: 'GUIDED',
    hskLevel: 1,
    xpReward: 10,
    done: false,
    items: [card('1', '你好', 'hello'), card('2', '谢谢', 'thank you')],
    storyLines: [],
    matchingChoices: [],
    strokeCount: null,
    ...overrides,
  }
}

export function practiceResult(overrides: Partial<PracticeResult> = {}): PracticeResult {
  return {
    type: 'FLASHCARDS',
    setId: 5,
    title: 'Greetings cards',
    correctCount: 2,
    itemCount: 2,
    allCorrect: true,
    results: [],
    nextSet: { id: 6, title: 'Food cards' },
    xp: xpResult({ todayXp: 30, dailyGoalXp: 30, goalReached: true, goalJustReached: true, bonusXp: 5 }),
    ...overrides,
  }
}

export function progressOverview(overrides: Partial<ProgressOverview> = {}): ProgressOverview {
  return {
    totalXp: 240,
    level: 3,
    xpIntoLevel: 40,
    nextLevelXp: 300,
    dailyGoalXp: 30,
    todayXp: 20,
    goalReachedToday: false,
    currentStreak: 7,
    bestStreak: 9,
    calendar: {
      month: '2026-05',
      today: '2026-05-18',
      joinedOn: '2026-05-01',
      goalDays: ['2026-05-11', '2026-05-12', '2026-05-13', '2026-05-14', '2026-05-15', '2026-05-16', '2026-05-17'],
      previousMonth: null,
      nextMonth: null,
    },
    sources: { learn: 120, practice: 90, reviewsAndGoals: 30, earlier: 0 },
    recent: [
      { kind: 'LEARN_LESSON', title: 'Polite phrases', xp: 10, at: '2026-05-18T03:00:00Z' },
      { kind: 'DAILY_GOAL', title: '', xp: 5, at: '2026-05-17T03:00:00Z' },
    ],
    badges: [
      { key: 'FIRST_STEPS', earned: true, earnedAt: '2026-05-11T03:00:00Z' },
      { key: 'STREAK_EXPLORER', earned: true, earnedAt: '2026-05-13T03:00:00Z' },
      { key: 'XP_STARTER', earned: false, earnedAt: null },
      { key: 'QUIZ_STAR', earned: false, earnedAt: null },
      { key: 'LESSON_FINISHER', earned: false, earnedAt: null },
      { key: 'TEN_DAY_STREAK', earned: false, earnedAt: null },
      { key: 'UNIT_CHAMPION', earned: false, earnedAt: null },
      { key: 'XP_300', earned: false, earnedAt: null },
    ],
    ...overrides,
  }
}
