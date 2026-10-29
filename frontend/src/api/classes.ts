import { http } from '@/api/http'
import { apiDelete, apiGet, apiPost, apiPut } from '@/api/services'
import type { LearningLevel, PageResponse } from '@/types/api'
import type {
  ActivityOutcome,
  ActivityPayload,
  ActivityPlay,
  CatalogueQuery,
  CatalogueSummary,
  ClassActivityType,
  ClassCard,
  ClassDetailsPayload,
  ClassListItem,
  ClassPreview,
  ContentEvent,
  FeedbackItem,
  FeedbackPayload,
  FeedbackRow,
  FeedbackType,
  LessonContentPayload,
  LessonEditor,
  LiveSessionPayload,
  MatchHint,
  MyClasses,
  QuestionPayload,
  QuizIntro,
  QuizPayload,
  QuizResult,
  QuizResults,
  QuizSession,
  ReviewDetail,
  ReviewQueue,
  Roster,
  SavedAnswer,
  ScheduleOptions,
  StudentClass,
  StudentLesson,
  StudentSession,
  StudentSessions,
  TeacherClassPage,
  TeacherContentDetail,
  TeacherContentQuery,
  TeacherContentRow,
  TeacherSession,
  TeacherSessionDetail,
  TeacherSessions,
  TeacherStudentDetail,
  TeachingDashboard,
} from '@/types/classes'

/** An uploaded lesson video (up to 100 MB) needs more than the default request timeout. */
const UPLOAD_CONFIG = { timeout: 0 }
/** A lesson video download gets 10 minutes: a slow connection still finishes, a stalled one fails. */
const VIDEO_DOWNLOAD_TIMEOUT_MS = 10 * 60_000
const API_PREFIX = /^\/api\/v1/

/** Activity types travel lower-case in URLs (`/activities/flashcards/12`). */
export function activitySlug(type: ClassActivityType): string {
  return type.toLowerCase()
}

/** The uploaded video behind a `streamPath` the server returned, fetched with the signed-in user's token. */
export async function videoBlob(streamPath: string, signal?: AbortSignal): Promise<Blob> {
  const response = await http.get<Blob>(streamPath.replace(API_PREFIX, ''), {
    responseType: 'blob',
    timeout: VIDEO_DOWNLOAD_TIMEOUT_MS,
    signal,
  })
  return response.data
}

export const publicClassesApi = {
  search(query: CatalogueQuery = {}) {
    return apiGet<PageResponse<ClassCard>>('/public/classes', { params: query })
  },
  summary() {
    return apiGet<CatalogueSummary>('/public/classes/summary')
  },
  featured() {
    return apiGet<ClassCard[]>('/public/classes/featured')
  },
  detail(classId: number) {
    return apiGet<ClassPreview>(`/public/classes/${classId}`)
  },
  /** Every level and topic a teacher can pick for a class (not only the ones in use). */
  levels() {
    return apiGet<LearningLevel[]>('/public/levels')
  },
  topics() {
    return apiGet<LearningLevel[]>('/public/categories')
  },
}

const STUDENT = '/students/classes'

export const studentClassesApi = {
  myClasses() {
    return apiGet<MyClasses>(STUDENT)
  },
  /** Published classes the student has not joined (Find classes). */
  catalogue(query: CatalogueQuery = {}) {
    return apiGet<PageResponse<ClassCard>>(`${STUDENT}/catalogue`, { params: query })
  },
  classPage(classId: number) {
    return apiGet<StudentClass>(`${STUDENT}/${classId}`)
  },
  join(classId: number) {
    return apiPost<StudentClass>(`${STUDENT}/${classId}/join`)
  },
  quizResults(classId: number) {
    return apiGet<QuizResults>(`${STUDENT}/${classId}/quiz-results`)
  },
  feedback(classId: number) {
    return apiGet<FeedbackItem[]>(`${STUDENT}/${classId}/feedback`)
  },
  lesson(lessonId: number) {
    return apiGet<StudentLesson>(`${STUDENT}/lessons/${lessonId}`)
  },
  markVideoWatched(lessonId: number) {
    return apiPost<StudentLesson>(`${STUDENT}/lessons/${lessonId}/video/watched`)
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
  quiz(quizId: number) {
    return apiGet<QuizIntro>(`${STUDENT}/quizzes/${quizId}`)
  },
  /** Starts an attempt, or resumes the open one. */
  startQuiz(quizId: number) {
    return apiPost<QuizSession>(`${STUDENT}/quizzes/${quizId}/attempts`)
  },
  saveAnswer(attemptId: number, answer: { questionId: number; optionId?: number; answerText?: string }) {
    return apiPut<SavedAnswer>(`${STUDENT}/quiz-attempts/${attemptId}/answers`, answer)
  },
  hint(attemptId: number, questionId: number) {
    return apiPost<{ questionId: number; hint: string }>(`${STUDENT}/quiz-attempts/${attemptId}/hint`, { questionId })
  },
  submitQuiz(attemptId: number) {
    return apiPost<QuizResult>(`${STUDENT}/quiz-attempts/${attemptId}/submit`)
  },
  quizResult(attemptId: number) {
    return apiGet<QuizResult>(`${STUDENT}/quiz-attempts/${attemptId}`)
  },
  liveSessions() {
    return apiGet<StudentSessions>(`${STUDENT}/live-sessions`)
  },
  book(sessionId: number) {
    return apiPost<StudentSession>(`${STUDENT}/live-sessions/${sessionId}/book`)
  },
  cancelBooking(sessionId: number) {
    return apiPost<StudentSession>(`${STUDENT}/live-sessions/${sessionId}/cancel-booking`)
  },
  /** The meeting link inside the join window; the server records attendance. */
  joinSession(sessionId: number) {
    return apiPost<{ meetingLink: string }>(`${STUDENT}/live-sessions/${sessionId}/join`)
  },
}

const TEACHER = '/teachers'

export const teacherClassesApi = {
  dashboard() {
    return apiGet<TeachingDashboard>(`${TEACHER}/teaching-dashboard`)
  },
  classes() {
    return apiGet<ClassListItem[]>(`${TEACHER}/classes`)
  },
  create(payload: ClassDetailsPayload) {
    return apiPost<TeacherClassPage>(`${TEACHER}/classes`, payload)
  },
  classPage(classId: number) {
    return apiGet<TeacherClassPage>(`${TEACHER}/classes/${classId}`)
  },
  /** A draft is changed directly; a published class keeps its details until the change is approved. */
  saveDetails(classId: number, payload: ClassDetailsPayload) {
    return apiPut<TeacherClassPage>(`${TEACHER}/classes/${classId}`, payload)
  },
  submitDetails(classId: number) {
    return apiPost<TeacherClassPage>(`${TEACHER}/classes/${classId}/submit`)
  },
  discardDetails(classId: number) {
    return apiDelete<TeacherClassPage>(`${TEACHER}/classes/${classId}/pending-details`)
  },
  saveNote(classId: number, note: string) {
    return apiPut<TeacherClassPage>(`${TEACHER}/classes/${classId}/note`, { note })
  },
  archive(classId: number) {
    return apiPost<TeacherClassPage>(`${TEACHER}/classes/${classId}/archive`)
  },
  restore(classId: number) {
    return apiPost<TeacherClassPage>(`${TEACHER}/classes/${classId}/restore`)
  },
  remove(classId: number) {
    return apiDelete<null>(`${TEACHER}/classes/${classId}`)
  },
  history(classId: number) {
    return apiGet<ContentEvent[]>(`${TEACHER}/classes/${classId}/history`)
  },
  addUnit(classId: number, title: string) {
    return apiPost<TeacherClassPage>(`${TEACHER}/classes/${classId}/units`, { title })
  },
  reorder(classId: number, units: { unitId: number; lessonIds: number[] }[]) {
    return apiPut<TeacherClassPage>(`${TEACHER}/classes/${classId}/structure`, { units })
  },
  renameUnit(unitId: number, title: string) {
    return apiPut<TeacherClassPage>(`${TEACHER}/units/${unitId}`, { title })
  },
  archiveUnit(unitId: number) {
    return apiPost<TeacherClassPage>(`${TEACHER}/units/${unitId}/archive`)
  },
  addLesson(unitId: number, title: string) {
    return apiPost<LessonEditor>(`${TEACHER}/units/${unitId}/lessons`, { title })
  },
}

const lessonPath = (lessonId: number) => `${TEACHER}/lessons/${lessonId}`

export const lessonEditorApi = {
  get(lessonId: number) {
    return apiGet<LessonEditor>(lessonPath(lessonId))
  },
  saveContent(lessonId: number, payload: LessonContentPayload) {
    return apiPut<LessonEditor>(`${lessonPath(lessonId)}/content`, payload)
  },
  /** `form` holds title, sourceType (UPLOAD / EXTERNAL_LINK), externalVideoUrl or file, durationSeconds. */
  saveVideo(lessonId: number, form: FormData) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/video`, form, UPLOAD_CONFIG)
  },
  removeVideo(lessonId: number) {
    return apiDelete<LessonEditor>(`${lessonPath(lessonId)}/video`)
  },
  addActivity(lessonId: number, payload: ActivityPayload) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/activities`, payload)
  },
  updateActivity(lessonId: number, type: ClassActivityType, id: number, payload: ActivityPayload) {
    return apiPut<LessonEditor>(`${lessonPath(lessonId)}/activities/${activitySlug(type)}/${id}`, payload)
  },
  deleteActivity(lessonId: number, type: ClassActivityType, id: number) {
    return apiDelete<LessonEditor>(`${lessonPath(lessonId)}/activities/${activitySlug(type)}/${id}`)
  },
  duplicateActivity(lessonId: number, type: ClassActivityType, id: number) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/activities/${activitySlug(type)}/${id}/duplicate`)
  },
  reorderActivities(lessonId: number, activities: { type: ClassActivityType; id: number }[]) {
    return apiPut<LessonEditor>(`${lessonPath(lessonId)}/activity-order`, { activities })
  },
  addQuiz(lessonId: number, payload: QuizPayload) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/quizzes`, payload)
  },
  updateQuiz(lessonId: number, quizId: number, payload: QuizPayload) {
    return apiPut<LessonEditor>(`${lessonPath(lessonId)}/quizzes/${quizId}`, payload)
  },
  archiveQuiz(lessonId: number, quizId: number) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/quizzes/${quizId}/archive`)
  },
  duplicateQuiz(lessonId: number, quizId: number) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/quizzes/${quizId}/duplicate`)
  },
  addQuestion(lessonId: number, quizId: number, payload: QuestionPayload) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/quizzes/${quizId}/questions`, payload)
  },
  reorderQuestions(lessonId: number, quizId: number, questionIds: number[]) {
    return apiPut<LessonEditor>(`${lessonPath(lessonId)}/quizzes/${quizId}/question-order`, { questionIds })
  },
  updateQuestion(lessonId: number, questionId: number, payload: QuestionPayload) {
    return apiPut<LessonEditor>(`${lessonPath(lessonId)}/questions/${questionId}`, payload)
  },
  deleteQuestion(lessonId: number, questionId: number) {
    return apiDelete<LessonEditor>(`${lessonPath(lessonId)}/questions/${questionId}`)
  },
  duplicateQuestion(lessonId: number, questionId: number) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/questions/${questionId}/duplicate`)
  },
  submit(lessonId: number) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/submit`)
  },
  archive(lessonId: number) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/archive`)
  },
  restore(lessonId: number) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/restore`)
  },
  discardRevision(lessonId: number) {
    return apiDelete<LessonEditor>(`${lessonPath(lessonId)}/revision`)
  },
}

const TEACHER_SESSIONS = `${TEACHER}/live-sessions`

export const teacherSessionsApi = {
  list() {
    return apiGet<TeacherSessions>(TEACHER_SESSIONS)
  },
  options() {
    return apiGet<ScheduleOptions>(`${TEACHER_SESSIONS}/options`)
  },
  detail(sessionId: number) {
    return apiGet<TeacherSessionDetail>(`${TEACHER_SESSIONS}/${sessionId}`)
  },
  schedule(payload: LiveSessionPayload) {
    return apiPost<TeacherSession>(TEACHER_SESSIONS, payload)
  },
  /** Booked students are told when the time or the details change. */
  update(sessionId: number, payload: LiveSessionPayload) {
    return apiPut<TeacherSession>(`${TEACHER_SESSIONS}/${sessionId}`, payload)
  },
  cancel(sessionId: number) {
    return apiPost<TeacherSession>(`${TEACHER_SESSIONS}/${sessionId}/cancel`)
  },
  saveRecording(sessionId: number, recordingUrl: string) {
    return apiPut<TeacherSession>(`${TEACHER_SESSIONS}/${sessionId}/recording`, { recordingUrl })
  },
}

export const teacherStudentsApi = {
  roster(params: { search?: string; filter?: 'ALL' | 'SUPPORT' } = {}) {
    return apiGet<Roster>(`${TEACHER}/students`, { params })
  },
  detail(studentId: number, classId?: number | null) {
    return apiGet<TeacherStudentDetail>(`${TEACHER}/students/${studentId}`, {
      params: classId ? { classId } : undefined,
    })
  },
  feedback(params: { studentId?: number; type?: FeedbackType } = {}) {
    return apiGet<FeedbackRow[]>(`${TEACHER}/feedback`, { params })
  },
  sendFeedback(payload: FeedbackPayload) {
    return apiPost<FeedbackRow>(`${TEACHER}/feedback`, payload)
  },
  updateFeedback(feedbackId: number, payload: { type: FeedbackType; message: string }) {
    return apiPut<FeedbackRow>(`${TEACHER}/feedback/${feedbackId}`, payload)
  },
}

const REVIEWS = '/admin/class-reviews'
const TEACHER_CONTENT = '/admin/teacher-classes'

export const adminClassesApi = {
  /** Waiting items, oldest first; `type` is ALL, CLASS, LESSON or QUIZ. */
  queue(type = 'ALL') {
    return apiGet<ReviewQueue>(REVIEWS, { params: { type } })
  },
  classReview(classId: number) {
    return apiGet<ReviewDetail>(`${REVIEWS}/classes/${classId}`)
  },
  lessonReview(lessonId: number) {
    return apiGet<ReviewDetail>(`${REVIEWS}/lessons/${lessonId}`)
  },
  approve(type: 'CLASS' | 'LESSON', id: number) {
    return apiPost<ReviewQueue>(`${REVIEWS}/${type === 'CLASS' ? 'classes' : 'lessons'}/${id}/approve`)
  },
  requestChanges(type: 'CLASS' | 'LESSON', id: number, reason: string) {
    return apiPost<ReviewQueue>(`${REVIEWS}/${type === 'CLASS' ? 'classes' : 'lessons'}/${id}/request-changes`, {
      reason,
    })
  },
  content(query: TeacherContentQuery = {}) {
    return apiGet<PageResponse<TeacherContentRow>>(TEACHER_CONTENT, { params: query })
  },
  teachers() {
    return apiGet<{ id: number; name: string }[]>(`${TEACHER_CONTENT}/teachers`)
  },
  contentDetail(type: 'CLASS' | 'LESSON', id: number) {
    return apiGet<TeacherContentDetail>(`${TEACHER_CONTENT}/${type === 'CLASS' ? 'classes' : 'lessons'}/${id}`)
  },
  archive(type: 'CLASS' | 'LESSON', id: number) {
    return apiPost<TeacherContentDetail>(`${TEACHER_CONTENT}/${type === 'CLASS' ? 'classes' : 'lessons'}/${id}/archive`)
  },
  restore(type: 'CLASS' | 'LESSON', id: number) {
    return apiPost<TeacherContentDetail>(`${TEACHER_CONTENT}/${type === 'CLASS' ? 'classes' : 'lessons'}/${id}/restore`)
  },
}
