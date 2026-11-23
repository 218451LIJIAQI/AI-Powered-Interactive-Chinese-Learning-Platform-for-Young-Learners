/**
 * The Learn module's endpoints: the Learn path and lesson player, Practice, Mistakes, XP and the daily goal, the
 * Progress page, the level check and the admin Learning path and Practice library.
 */
import { PRACTICE_ACTIVITIES } from '@/config/practiceActivities'
import type { ContentStatus, PageResponse } from '@/types/api'
import type {
  AdminLearningPath,
  AdminPracticeSetDetail,
  AdminPracticeSetRow,
  AdminUnitDetail,
  AnswerCheck,
  AnswerInsights,
  DailyGoal,
  Guidebook,
  HintMode,
  HintReport,
  HintRun,
  HintSettings,
  HintStep,
  LearnPath,
  LessonPlay,
  LessonResult,
  LevelCheckAnswer,
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
  PracticeSetAction,
  PracticeSetPayload,
  PracticeSetSummary,
  PracticeType,
  ProgressOverview,
  QuestionAnswer,
  SkillProgress,
  TodaySummary,
} from '@/types/learn'

import { aiHelperApi } from './ai'
import { apiGet, apiPost, apiPut } from './request'

const practicePath = (type: PracticeType, id?: number) =>
  `/students/practice/${PRACTICE_ACTIVITIES[type].slug}/sets${id === undefined ? '' : `/${id}`}`

const adminSetPath = (type: PracticeType, id?: number) =>
  `/admin/practice-sets/${PRACTICE_ACTIVITIES[type].slug}${id === undefined ? '' : `/${id}`}`

/** What a hint request sends of its run: its start and the server's signature of it (both null without a run). */
const runOf = (run: HintRun | null) => ({
  runStartedAt: run?.runStartedAt ?? null,
  runSignature: run?.runSignature ?? null,
})

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
  completeLesson(lessonId: number, answers: readonly QuestionAnswer[]) {
    return apiPost<LessonResult>(`/students/learn/lessons/${lessonId}/complete`, { answers })
  },
  /**
   * The hint for a question of an open lesson (checkpoints have none); a hint is one step. `run` is the played copy's
   * own (`LessonPlay`): the server counts the run's hints against the level's limit.
   */
  hint(lessonId: number, questionId: number, run: HintRun | null) {
    return apiPost<HintStep>(`/students/learn/lessons/${lessonId}/hint`, { questionId, ...runOf(run) })
  },
}

export const practiceApi = {
  sets(type: PracticeType) {
    return apiGet<{ sets: PracticeSetSummary[] }>(practicePath(type)).then((data) => data.sets)
  },
  set(type: PracticeType, id: number) {
    return apiGet<PracticeSet>(practicePath(type, id))
  },
  check(type: PracticeType, id: number, answer: PracticeItemAnswer) {
    return apiPost<AnswerCheck>(`${practicePath(type, id)}/check`, answer)
  },
  /** Flashcards send one rating per card. Tracing sends the number of strokes drawn. */
  complete(type: PracticeType, id: number, answers: readonly PracticeItemAnswer[], strokeCount?: number) {
    return apiPost<PracticeResult>(`${practicePath(type, id)}/complete`, { answers, strokeCount })
  },
  /**
   * The one hint for an item; Matching names the word the student is stuck on (a board has one hint). `run` is the
   * played copy's own (`PracticeSet`): the server counts the run's hints against the level's limit.
   */
  hint(type: PracticeType, id: number, itemKey: string, run: HintRun) {
    return apiPost<HintStep>(`${practicePath(type, id)}/hint`, { itemKey, ...runOf(run) })
  },
  /** Typing: the words the typed pinyin spells (none until the pinyin of a word is complete). */
  candidates(id: number, itemKey: string, pinyin: string) {
    return apiPost<{ words: string[] }>(`${practicePath('TYPING', id)}/candidates`, { itemKey, pinyin }).then(
      (data) => data.words,
    )
  },
}

export const hintApi = {
  settings() {
    return apiGet<HintSettings>('/students/hint-settings')
  },
  setMode(mode: HintMode) {
    return apiPut<HintSettings>('/students/hint-settings', { mode })
  },
  /** Reports a hint as an AI answer (feature `HINT`): an admin replies in Ask SparkBot › My reports. */
  report(report: HintReport) {
    return aiHelperApi.report({ featureKey: 'HINT', ...report })
  },
}

export const mistakesApi = {
  /** The first `size` waiting mistakes; the counts cover all of them. */
  list(source: MistakeSource | null, size: number) {
    return apiGet<MistakeList>('/students/mistakes', { params: { source: source ?? 'all', size } })
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
  /** A waiting mistake's one hint. `run` is the review's own (`MistakeReview`): its hints count against the level's limit. */
  hint(questionId: number, run: HintRun | null) {
    return apiPost<HintStep>('/students/mistakes/review/hint', { questionId, ...runOf(run) })
  },
}

export const xpApi = {
  today() {
    return apiGet<TodaySummary>('/students/today')
  },
  dailyGoal() {
    return apiGet<DailyGoal>('/students/daily-goal')
  },
  /** 10, 20 or 30 XP a day (Home and the Account page change it). */
  setDailyGoal(dailyGoalXp: number) {
    return apiPut<DailyGoal>('/students/daily-goal', { dailyGoalXp })
  },
  /** `month` is `yyyy-MM`; omitted for this month. */
  progress(month?: string | null) {
    return apiGet<ProgressOverview>('/students/progress/overview', { params: month ? { month } : {} })
  },
  /** The Skills card: percent and weekly change per skill, and the weakest one. */
  progressSkills() {
    return apiGet<SkillProgress>('/students/progress/skills')
  },
  /** How sure the last 7 days' Practice answers were, and how many came after a hint. */
  progressAnswers() {
    return apiGet<AnswerInsights>('/students/progress/answers')
  },
}

export const levelCheckApi = {
  /** Stateless: send every answer so far (`optionId` null for "I don't know yet"). */
  next(startLevel: MandarinLevel | null, answers: readonly LevelCheckAnswer[]) {
    return apiPost<LevelCheckStep>('/students/level-check/next', { startLevel, answers })
  },
}

interface PracticeLibraryQuery {
  q?: string
  type?: PracticeType | null
  status?: ContentStatus | null
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
  /** Publishes a question as it is stored, without sending its content. */
  publishQuestion(id: number) {
    return apiPost<LevelCheckQuestionDetail>(`/admin/learning-path/level-check-questions/${id}/publish`)
  },
  /** The Undo of Publish: the question is a draft again, as it is stored. */
  unpublishQuestion(id: number) {
    return apiPost<LevelCheckQuestionDetail>(`/admin/learning-path/level-check-questions/${id}/unpublish`)
  },
  practiceSets(query: PracticeLibraryQuery) {
    const params = {
      q: query.q || undefined,
      type: query.type ? PRACTICE_ACTIVITIES[query.type].slug : undefined,
      status: query.status ?? undefined,
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
  changePracticeSet(type: PracticeType, id: number, action: PracticeSetAction) {
    return apiPost<AdminPracticeSetDetail>(`${adminSetPath(type, id)}/${action}`)
  },
}
