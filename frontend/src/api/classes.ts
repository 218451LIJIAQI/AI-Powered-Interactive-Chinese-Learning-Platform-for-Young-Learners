import { apiDelete, apiDownload, apiGet, apiPost, apiPut } from './request'
import type { ClassTopic, PageResponse } from '@/types/api'
import type {
  ActivityOutcome,
  ActivityPlay,
  CatalogueMembership,
  CatalogueQuery,
  ClassActivityType,
  ClassCard,
  ClassPreview,
  MatchHint,
  MyClasses,
  QuizResult,
  QuizSession,
  SavedAnswer,
  StudentClass,
  StudentLesson,
} from '@/types/classes'
import { activitySlug } from '@/utils/classes'

/** A lesson video download gets 10 minutes: a slow connection still finishes, a stalled one fails. */
const VIDEO_DOWNLOAD_TIMEOUT_MS = 10 * 60_000
const API_PREFIX = /^\/api\/v1/

/** The uploaded video behind a `streamPath` the server returned, fetched with the signed-in user's token. */
export async function videoBlob(streamPath: string, signal?: AbortSignal): Promise<Blob> {
  return (await apiDownload(streamPath.replace(API_PREFIX, ''), { timeout: VIDEO_DOWNLOAD_TIMEOUT_MS, signal })).blob
}

export const publicClassesApi = {
  search(query: CatalogueQuery) {
    return apiGet<PageResponse<ClassCard>>('/public/classes', { params: query })
  },
  detail(classId: number) {
    return apiGet<ClassPreview>(`/public/classes/${classId}`)
  },
  /** Every topic in its display order, in use by a class or not. */
  topics() {
    return apiGet<ClassTopic[]>('/public/categories')
  },
}

const STUDENT = '/students/classes'

export const studentClassesApi = {
  myClasses() {
    return apiGet<MyClasses>(STUDENT)
  },
  /** Published classes the learner has not joined (Find classes). */
  catalogue(query: CatalogueQuery) {
    return apiGet<PageResponse<ClassCard>>(`${STUDENT}/catalogue`, { params: query })
  },
  catalogueMembership() {
    return apiGet<CatalogueMembership>(`${STUDENT}/catalogue/membership`)
  },
  classPage(classId: number) {
    return apiGet<StudentClass>(`${STUDENT}/${classId}`)
  },
  join(classId: number) {
    return apiPost<void>(`${STUDENT}/${classId}/join`)
  },
  /** Leaves the class (also the Undo of joining it); the learner's progress is kept for joining again. */
  leave(classId: number) {
    return apiPost<void>(`${STUDENT}/${classId}/leave`)
  },
  lesson(lessonId: number) {
    return apiGet<StudentLesson>(`${STUDENT}/lessons/${lessonId}`)
  },
  markVideoWatched(lessonId: number) {
    return apiPost<void>(`${STUDENT}/lessons/${lessonId}/video/watched`)
  },
  activity(type: ClassActivityType, activityId: number) {
    return apiGet<ActivityPlay>(`${STUDENT}/activities/${activitySlug(type)}/${activityId}`)
  },
  completeActivity(type: ClassActivityType, activityId: number) {
    return apiPost<ActivityOutcome>(`${STUDENT}/activities/${activitySlug(type)}/${activityId}/complete`)
  },
  checkMatching(activityId: number, pairs: { wordId: number; meaning: string }[]) {
    return apiPost<ActivityOutcome>(`${STUDENT}/activities/matching/${activityId}/check`, { pairs })
  },
  matchingHint(activityId: number, wordId: number) {
    return apiPost<MatchHint>(`${STUDENT}/activities/matching/${activityId}/hint`, { wordId })
  },
  /** Starts an attempt, or resumes the open one. */
  startQuiz(quizId: number) {
    return apiPost<QuizSession>(`${STUDENT}/quizzes/${quizId}/attempts`)
  },
  saveAnswer(attemptId: number, answer: { questionId: number; optionId?: number; answerText?: string }) {
    return apiPut<SavedAnswer>(`${STUDENT}/quiz-attempts/${attemptId}/answers`, answer)
  },
  clearAnswer(attemptId: number, questionId: number) {
    return apiDelete<void>(`${STUDENT}/quiz-attempts/${attemptId}/answers/${questionId}`)
  },
  /** Throws an open attempt away with its saved answers (leaving the quiz midway); the best score stays. */
  discardAttempt(attemptId: number) {
    return apiDelete<void>(`${STUDENT}/quiz-attempts/${attemptId}`)
  },
  hint(attemptId: number, questionId: number) {
    return apiPost<{ hint: string }>(`${STUDENT}/quiz-attempts/${attemptId}/hint`, { questionId })
  },
  submitQuiz(attemptId: number) {
    return apiPost<QuizResult>(`${STUDENT}/quiz-attempts/${attemptId}/submit`)
  },
  quizResult(attemptId: number) {
    return apiGet<QuizResult>(`${STUDENT}/quiz-attempts/${attemptId}`)
  },
}
