/**
 * The Learn module's endpoints: the Learn path and lesson player, Practice, Mistakes, XP and the daily goal, the
 * Progress page, the level check and the admin Learning path and Practice library.
 */
import type { ContentStatus, LearningStage, PageResponse } from '@/types/api'
import type {
  AdminLearningPath,
  AdminPracticeSetDetail,
  AdminPracticeSetRow,
  AdminUnitDetail,
  AnswerCheck,
  DailyGoal,
  Guidebook,
  LearnPath,
  LessonPlay,
  LessonResult,
  LevelCheckQuestionDetail,
  LevelCheckQuestionPayload,
  LevelCheckQuestionRow,
  LevelCheckStep,
  MandarinLevel,
  MistakeList,
  MistakeReview,
  MistakeReviewResult,
  MistakeSource,
  PracticeItemAnswer,
  PracticeResult,
  PracticeSet,
  PracticeSetPayload,
  PracticeSetSummary,
  PracticeType,
  ProgressOverview,
  QuestionAnswer,
  TodaySummary,
} from '@/types/learn'

import { apiGet, apiPost, apiPut } from './services'

/** URL segment of each Practice type (`/students/practice/{slug}/sets`, `/admin/practice-sets/{slug}`). */
export const PRACTICE_TYPE_SLUGS = {
  FLASHCARDS: 'flashcards',
  QUIZZES: 'quizzes',
  LISTENING: 'listening',
  TRACING: 'tracing',
  DRAG_DROP: 'drag-drop',
  MATCHING: 'matching',
  STORY: 'story',
} as const satisfies Record<PracticeType, string>

export function practiceTypeOfSlug(slug: string): PracticeType | null {
  const entry = Object.entries(PRACTICE_TYPE_SLUGS).find(([, value]) => value === slug)
  return entry ? (entry[0] as PracticeType) : null
}

const practicePath = (type: PracticeType, id?: number) =>
  `/students/practice/${PRACTICE_TYPE_SLUGS[type]}/sets${id === undefined ? '' : `/${id}`}`

const adminSetPath = (type: PracticeType, id?: number) =>
  `/admin/practice-sets/${PRACTICE_TYPE_SLUGS[type]}${id === undefined ? '' : `/${id}`}`

export const learnApi = {
  path() {
    return apiGet<LearnPath>('/students/learn/path')
  },
  guidebook(unitId: number) {
    return apiGet<Guidebook>(`/students/learn/units/${unitId}/guidebook`)
  },
  lesson(lessonId: number) {
    return apiGet<LessonPlay>(`/students/learn/lessons/${lessonId}`)
  },
  checkLesson(lessonId: number, answer: QuestionAnswer) {
    return apiPost<AnswerCheck>(`/students/learn/lessons/${lessonId}/check`, answer)
  },
  completeLesson(lessonId: number, answers: readonly QuestionAnswer[], durationSeconds: number) {
    return apiPost<LessonResult>(`/students/learn/lessons/${lessonId}/complete`, { answers, durationSeconds })
  },
}

export const practiceApi = {
  sets(type: PracticeType) {
    return apiGet<{ type: PracticeType; sets: PracticeSetSummary[] }>(practicePath(type)).then((data) => data.sets)
  },
  set(type: PracticeType, id: number) {
    return apiGet<PracticeSet>(practicePath(type, id))
  },
  check(type: PracticeType, id: number, answer: PracticeItemAnswer) {
    return apiPost<AnswerCheck>(`${practicePath(type, id)}/check`, answer)
  },
  /** Flashcards send no answers; tracing sends the strokes drawn. */
  complete(type: PracticeType, id: number, answers: readonly PracticeItemAnswer[], strokeCount?: number) {
    return apiPost<PracticeResult>(`${practicePath(type, id)}/complete`, { answers, strokeCount })
  },
}

export const mistakesApi = {
  list(source: MistakeSource | null, page = 0, size = 20) {
    return apiGet<MistakeList>('/students/mistakes', { params: { source: source ?? 'all', page, size } })
  },
  review() {
    return apiGet<MistakeReview>('/students/mistakes/review')
  },
  check(answer: QuestionAnswer) {
    return apiPost<AnswerCheck>('/students/mistakes/review/check', answer)
  },
  complete(answers: readonly QuestionAnswer[]) {
    return apiPost<MistakeReviewResult>('/students/mistakes/review/complete', { answers })
  },
}

export const xpApi = {
  today() {
    return apiGet<TodaySummary>('/students/today')
  },
  dailyGoal() {
    return apiGet<DailyGoal>('/students/daily-goal')
  },
  /** 10, 20 or 30 XP a day (the Account page's daily goal). */
  setDailyGoal(dailyGoalXp: number) {
    return apiPut<DailyGoal>('/students/daily-goal', { dailyGoalXp })
  },
  /** `month` is `yyyy-MM`; omitted for this month. */
  progress(month?: string | null) {
    return apiGet<ProgressOverview>('/students/progress/overview', { params: month ? { month } : {} })
  },
}

export const levelCheckApi = {
  /** Stateless: send every answer so far (`optionId` null for "I don't know yet"). */
  next(startLevel: MandarinLevel | null, answers: readonly { questionId: number; optionId: number | null }[]) {
    return apiPost<LevelCheckStep>('/students/level-check/next', { startLevel, answers })
  },
}

interface PracticeLibraryQuery {
  q?: string
  type?: PracticeType | null
  status?: ContentStatus | null
  stage?: LearningStage | null
  page: number
  size: number
}

export const adminLearningApi = {
  path() {
    return apiGet<AdminLearningPath>('/admin/learning-path')
  },
  unit(unitId: number) {
    return apiGet<AdminUnitDetail>(`/admin/learning-path/units/${unitId}`)
  },
  setUnitPublished(unitId: number, published: boolean) {
    return apiPost<AdminUnitDetail>(`/admin/learning-path/units/${unitId}/${published ? 'publish' : 'unpublish'}`)
  },
  questions(page: number, size: number) {
    return apiGet<PageResponse<LevelCheckQuestionRow>>('/admin/learning-path/level-check-questions', {
      params: { page, size },
    })
  },
  question(id: number) {
    return apiGet<LevelCheckQuestionDetail>(`/admin/learning-path/level-check-questions/${id}`)
  },
  saveQuestion(id: number | null, payload: LevelCheckQuestionPayload) {
    return id === null
      ? apiPost<LevelCheckQuestionDetail>('/admin/learning-path/level-check-questions', payload)
      : apiPut<LevelCheckQuestionDetail>(`/admin/learning-path/level-check-questions/${id}`, payload)
  },
  practiceSets(query: PracticeLibraryQuery) {
    const params = {
      q: query.q || undefined,
      type: query.type ? PRACTICE_TYPE_SLUGS[query.type] : undefined,
      status: query.status ?? undefined,
      stage: query.stage ?? undefined,
      page: query.page,
      size: query.size,
    }
    return apiGet<PageResponse<AdminPracticeSetRow>>('/admin/practice-sets', { params })
  },
  practiceSet(type: PracticeType, id: number) {
    return apiGet<AdminPracticeSetDetail>(adminSetPath(type, id))
  },
  savePracticeSet(type: PracticeType, id: number | null, payload: PracticeSetPayload) {
    return id === null
      ? apiPost<AdminPracticeSetDetail>(adminSetPath(type), payload)
      : apiPut<AdminPracticeSetDetail>(adminSetPath(type, id), payload)
  },
  changePracticeSet(type: PracticeType, id: number, action: 'publish' | 'unpublish' | 'archive' | 'restore') {
    return apiPost<AdminPracticeSetDetail>(`${adminSetPath(type, id)}/${action}`)
  },
}
