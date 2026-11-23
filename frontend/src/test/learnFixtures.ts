/** Shared fixtures of the Learn module tests: API responses shaped like the backend's (dto/learn). */
import type {
  AdminPracticeSetDetail,
  AnswerCheck,
  HintSettings,
  LearnPath,
  LessonPlay,
  LessonResult,
  PathStop,
  PathUnit,
  PracticeItem,
  PracticeItemData,
  PracticeResult,
  PracticeSet,
  ProgressOverview,
  TodaySummary,
  XpResult,
} from '@/types/learn'

export function xpResult(overrides: Partial<XpResult> = {}): XpResult {
  return {
    xpEarned: 10,
    bonusXp: 0,
    todayXp: 10,
    dailyGoalXp: 20,
    goalReached: false,
    currentStreak: 2,
    newBadges: [],
    rankUp: null,
    ...overrides,
  }
}

export function today(overrides: Partial<TodaySummary> = {}): TodaySummary {
  return {
    dailyGoalXp: 30,
    todayXp: 20,
    goalReached: false,
    week: [
      { date: '2026-05-12', goalReached: true },
      { date: '2026-05-13', goalReached: true },
      { date: '2026-05-14', goalReached: true },
      { date: '2026-05-15', goalReached: true },
      { date: '2026-05-16', goalReached: true },
      { date: '2026-05-17', goalReached: true },
      { date: '2026-05-18', goalReached: false },
    ],
    mistakesWaiting: 6,
    ...overrides,
  }
}

/** A lesson on the path: locked, until `overrides` say otherwise. */
export function pathStop(id: number, position: number, title: string, overrides: Partial<PathStop> = {}): PathStop {
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
    iconKey: 'route',
    state: 'LOCKED',
    lessonCount: 3,
    completedCount: 0,
    unlockAfterUnit: number - 1,
    lessons: [],
    ...overrides,
  }
}

/**
 * Unit 1 with two lessons done and the third current, Unit 2 locked with two lessons and a bonus chest (no lesson, so
 * not counted, carrying the number of the lesson before it); Level 2 still locked.
 */
export function learnPath(): LearnPath {
  return {
    nextLesson: { unitId: 1, xpReward: 10 },
    levels: [
      {
        level: 1,
        hskLevel: 1,
        locked: false,
        units: [
          unit(1, 1, {
            title: 'Greetings',
            state: 'ACTIVE',
            completedCount: 2,
            unlockAfterUnit: null,
            lessons: [
              pathStop(11, 1, 'Hello and goodbye', { state: 'DONE' }),
              pathStop(12, 2, 'Polite phrases', { state: 'DONE' }),
              pathStop(13, 3, 'Your name', { state: 'AVAILABLE', current: true }),
            ],
          }),
          unit(2, 2, {
            title: 'Food',
            lessonCount: 2,
            lessons: [
              pathStop(21, 1, 'Fruit', { unlockAfter: 'Your name' }),
              pathStop(22, 2, 'Drinks', { unlockAfter: 'Fruit' }),
              pathStop(23, 2, 'Ordering', { kind: 'BONUS', unlockAfter: 'Drinks' }),
            ],
          }),
        ],
        checkpoint: {
          id: 90,
          questionCount: 10,
          xpReward: 50,
          passPercent: 70,
          state: 'LOCKED',
        },
      },
      {
        level: 2,
        hskLevel: 2,
        locked: true,
        units: [unit(3, 3, { title: 'Travel', lessonCount: 4 })],
        checkpoint: null,
      },
    ],
  }
}

/** A two-question lesson: a choice and a sentence to build. */
export function lessonPlay(overrides: Partial<LessonPlay> = {}): LessonPlay {
  return {
    title: 'Your name',
    kind: 'LESSON',
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
    runStartedAt: '2026-05-18T03:00:00.000001',
    runSignature: 'c2lnbmVkLWJ5LXRoZS1zZXJ2ZXI',
    ...overrides,
  }
}

export function answerCheck(overrides: Partial<AnswerCheck> = {}): AnswerCheck {
  return { correct: true, correctAnswer: 'name', pinyin: 'míngzi', meaning: 'name', explanation: null, ...overrides }
}

export function lessonResult(overrides: Partial<LessonResult> = {}): LessonResult {
  return {
    kind: 'LESSON',
    passed: true,
    passPercent: null,
    accuracy: 50,
    nextLesson: { title: 'Fruit', unlockedNow: true },
    xp: xpResult({ newBadges: ['UNIT_CHAMPION'] }),
    ...overrides,
  }
}

/** A student's hint setting: hints offered automatically, with the HSK 1 rule. */
export function hintSettings(overrides: Partial<HintSettings> = {}): HintSettings {
  return { mode: 'AUTOMATIC', tier: 'HSK1', delaySeconds: 15, perSet: 0, ...overrides }
}

/** One item of a practice set with nothing filled in; `overrides` give it what its activity shows. */
export function practiceItem(key: string, overrides: Partial<PracticeItem> = {}): PracticeItem {
  return {
    key,
    prompt: null,
    hanzi: null,
    pinyin: null,
    meaning: null,
    audioText: null,
    example: null,
    exampleMeaning: null,
    options: [],
    tiles: [],
    ...overrides,
  }
}

export function adminPracticeItem(overrides: Partial<PracticeItemData> = {}): PracticeItemData {
  return {
    hanzi: null,
    pinyin: null,
    meaning: null,
    prompt: null,
    audioText: null,
    example: null,
    exampleMeaning: null,
    options: [],
    correctIndex: null,
    explanation: null,
    distractors: null,
    ...overrides,
  }
}

export function tracingSet(overrides: Partial<AdminPracticeSetDetail> = {}): AdminPracticeSetDetail {
  return {
    id: 2,
    type: 'TRACING',
    title: 'Write 你好',
    stage: 'GUIDED',
    hskLevel: 1,
    xpReward: 10,
    status: 'DRAFT',
    createdBy: null,
    playedBy: 0,
    items: [adminPracticeItem({ hanzi: '你好', pinyin: 'nǐ hǎo', meaning: 'hello' })],
    strokeCount: 13,
    characterStrokes: [7, 6],
    history: [{ action: 'CREATED', actor: null, at: '2026-05-08T03:00:00Z' }],
    ...overrides,
  }
}

export function flashcardSet(overrides: Partial<PracticeSet> = {}): PracticeSet {
  return {
    id: 5,
    type: 'FLASHCARDS',
    stage: 'GUIDED',
    xpReward: 10,
    done: false,
    items: [
      practiceItem('1', { hanzi: '你好', meaning: 'hello', audioText: '你好' }),
      practiceItem('2', { hanzi: '谢谢', meaning: 'thank you', audioText: '谢谢' }),
    ],
    matchingChoices: [],
    strokeCount: null,
    characterStrokes: null,
    runStartedAt: '2026-05-18T03:00:00.000001',
    runSignature: 'c2lnbmVkLWJ5LXRoZS1zZXJ2ZXI',
    ...overrides,
  }
}

export function practiceResult(overrides: Partial<PracticeResult> = {}): PracticeResult {
  return {
    type: 'FLASHCARDS',
    correctCount: 2,
    itemCount: 2,
    allCorrect: true,
    nextSet: { id: 6, title: 'Food cards' },
    xp: xpResult({ todayXp: 30, dailyGoalXp: 30, goalReached: true, bonusXp: 5 }),
    ...overrides,
  }
}

export function progressOverview(overrides: Partial<ProgressOverview> = {}): ProgressOverview {
  return {
    totalXp: 240,
    level: 3,
    xpIntoLevel: 40,
    nextLevelXp: 300,
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
