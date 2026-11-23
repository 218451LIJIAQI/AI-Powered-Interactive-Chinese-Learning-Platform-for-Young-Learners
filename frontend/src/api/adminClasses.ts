/**
 * Admin class authoring (`/admin/classes`, `/admin/units`, `/admin/lessons`, `/admin/ai/quiz-draft`): the classes and
 * lessons the MandarinSpark team makes. Every admin can edit every class; publishing needs no review.
 */
import { AI_REQUEST_CONFIG } from '@/api/ai'
import { apiDelete, apiGet, apiPost, apiPut } from '@/api/request'
import type { AiAvailabilityResponse, AiQuizGeneratorResponse, AppLanguage, PageResponse } from '@/types/api'
import type {
  ActivityPayload,
  AdminClassPage,
  AdminContentQuery,
  AdminContentRow,
  ClassDetailsPayload,
  LessonContentPayload,
  LessonEditor,
  QuestionPayload,
  QuizPayload,
  QuizSettingsPayload,
} from '@/types/adminClasses'
import type { ClassActivityType } from '@/types/classes'
import { activitySlug } from '@/utils/classes'

const CLASSES = '/admin/classes'
const classPath = (classId: number) => `${CLASSES}/${classId}`
const lessonPath = (lessonId: number) => `/admin/lessons/${lessonId}`

export const adminClassesApi = {
  /** Classes and lessons, newest change first (Content › Classes). */
  list(query: AdminContentQuery) {
    return apiGet<PageResponse<AdminContentRow>>(CLASSES, { params: query })
  },
  /** A new class starts as a draft. */
  create(payload: ClassDetailsPayload) {
    return apiPost<AdminClassPage>(CLASSES, payload)
  },
  classPage(classId: number) {
    return apiGet<AdminClassPage>(classPath(classId))
  },
  /** Saved at once, also on a published class: learners see the new details right away. */
  saveDetails(classId: number, payload: ClassDetailsPayload) {
    return apiPut<AdminClassPage>(classPath(classId), payload)
  },
  /** Only when the class can be published (`canPublish`: 2 units, 4–6 published lessons). */
  publish(classId: number) {
    return apiPost<AdminClassPage>(`${classPath(classId)}/publish`)
  },
  archive(classId: number) {
    return apiPost<AdminClassPage>(`${classPath(classId)}/archive`)
  },
  restore(classId: number) {
    return apiPost<AdminClassPage>(`${classPath(classId)}/restore`)
  },
  /** A draft class that was never published, with its units and lessons. */
  remove(classId: number) {
    return apiDelete<null>(classPath(classId))
  },
  addUnit(classId: number, title: string) {
    return apiPost<AdminClassPage>(`${classPath(classId)}/units`, { title })
  },
  renameUnit(unitId: number, title: string) {
    return apiPut<AdminClassPage>(`/admin/units/${unitId}`, { title })
  },
  /** A new lesson starts as a draft; the answer is its editor. */
  addLesson(unitId: number, title: string) {
    return apiPost<LessonEditor>(`/admin/units/${unitId}/lessons`, { title })
  },
}

export const lessonEditorApi = {
  get(lessonId: number) {
    return apiGet<LessonEditor>(lessonPath(lessonId))
  },
  /** Saves the working copy: learners keep a published lesson as it was until it is published again. */
  saveContent(lessonId: number, payload: LessonContentPayload) {
    return apiPut<LessonEditor>(`${lessonPath(lessonId)}/content`, payload)
  },
  /** Upload duration depends on file size and connection speed; callers pass an AbortSignal to cancel on leave. */
  saveVideo(
    lessonId: number,
    payload: { title: string; file: File; durationSeconds?: number | null },
    signal?: AbortSignal,
  ) {
    const form = new FormData()
    form.append('title', payload.title)
    form.append('file', payload.file)
    if (payload.durationSeconds !== null && payload.durationSeconds !== undefined) {
      form.append('durationSeconds', String(payload.durationSeconds))
    }
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/video`, form, { timeout: 30 * 60_000, signal })
  },
  removeVideo(lessonId: number) {
    return apiDelete<LessonEditor>(`${lessonPath(lessonId)}/video`)
  },
  addActivity(lessonId: number, payload: ActivityPayload) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/activities`, payload)
  },
  updateActivity(lessonId: number, type: ClassActivityType, activityId: number, payload: ActivityPayload) {
    return apiPut<LessonEditor>(`${lessonPath(lessonId)}/activities/${activitySlug(type)}/${activityId}`, payload)
  },
  addQuiz(lessonId: number, payload: QuizPayload) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/quizzes`, payload)
  },
  /** The quiz's "Allow SparkBot hints" switch. */
  updateQuiz(lessonId: number, quizId: number, payload: QuizSettingsPayload) {
    return apiPut<LessonEditor>(`${lessonPath(lessonId)}/quizzes/${quizId}`, payload)
  },
  /** A lesson quiz has at most 5 questions; `position` puts the question at that place (the Undo of a delete). */
  addQuestion(lessonId: number, quizId: number, payload: QuestionPayload) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/quizzes/${quizId}/questions`, payload)
  },
  updateQuestion(lessonId: number, questionId: number, payload: QuestionPayload) {
    return apiPut<LessonEditor>(`${lessonPath(lessonId)}/questions/${questionId}`, payload)
  },
  deleteQuestion(lessonId: number, questionId: number) {
    return apiDelete<LessonEditor>(`${lessonPath(lessonId)}/questions/${questionId}`)
  },
  /** Keep: the admin checked an AI-drafted question as it is (saving an edit checks it too). */
  keepQuestion(lessonId: number, questionId: number) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/questions/${questionId}/keep`)
  },
  /** Publishes a draft lesson, or the saved changes of a published one: learners see this version from now on. */
  publish(lessonId: number) {
    return apiPost<LessonEditor>(`${lessonPath(lessonId)}/publish`)
  },
  /** Drops the unpublished changes of a published lesson: the editor shows the published version again. */
  discardChanges(lessonId: number) {
    return apiDelete<LessonEditor>(`${lessonPath(lessonId)}/revision`)
  },
}

/**
 * AI-assisted quiz drafts: nothing is saved; the admin checks each question, which is added through the editor. The
 * draft is for one lesson's quiz and writes at most the places left in it.
 */
export const adminQuizDraftApi = {
  availability() {
    return apiGet<AiAvailabilityResponse>('/admin/ai/quiz-draft/availability')
  },
  draft(payload: {
    lessonId: number
    topic: string
    level: string
    numberOfQuestions: number
    languagePreference: AppLanguage
  }) {
    return apiPost<AiQuizGeneratorResponse>('/admin/ai/quiz-draft', payload, AI_REQUEST_CONFIG)
  },
}
