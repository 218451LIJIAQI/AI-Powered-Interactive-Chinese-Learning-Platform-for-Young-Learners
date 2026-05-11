import { apiClient } from './apiClient'
import type {
  FlashcardFormPayload,
  FlashcardItem,
  ListeningExercise,
  ListeningExerciseFormPayload,
  MatchingGame,
  MatchingGameFormPayload,
  TeacherQuiz,
  TeacherQuizAttemptSummary,
  QuizFormPayload,
  QuizQuestionFormPayload,
  TracingExercise,
  TracingExerciseFormPayload,
} from '../types/learning'

import type { ApiResponse } from '../types/api'

export const teacherActivityService = {
  async getFlashcards(lessonId: number) {
    const response = await apiClient.get<ApiResponse<FlashcardItem[]>>(`/teachers/lessons/${lessonId}/flashcards`)
    return response.data.data
  },

  async createFlashcard(lessonId: number, payload: FlashcardFormPayload) {
    const response = await apiClient.post<ApiResponse<FlashcardItem>>(`/teachers/lessons/${lessonId}/flashcards`, payload)
    return response.data.data
  },

  async updateFlashcard(flashcardId: number, payload: FlashcardFormPayload) {
    const response = await apiClient.put<ApiResponse<FlashcardItem>>(`/teachers/flashcards/${flashcardId}`, payload)
    return response.data.data
  },

  async deleteFlashcard(flashcardId: number) {
    const response = await apiClient.delete<ApiResponse<null>>(`/teachers/flashcards/${flashcardId}`)
    return response.data.success
  },

  async getQuizzes(lessonId: number) {
    const response = await apiClient.get<ApiResponse<TeacherQuiz[]>>(`/teachers/lessons/${lessonId}/quizzes`)
    return response.data.data
  },

  async createQuiz(lessonId: number, payload: QuizFormPayload) {
    const response = await apiClient.post<ApiResponse<TeacherQuiz>>(`/teachers/lessons/${lessonId}/quizzes`, payload)
    return response.data.data
  },

  async updateQuiz(quizId: number, payload: QuizFormPayload) {
    const response = await apiClient.put<ApiResponse<TeacherQuiz>>(`/teachers/quizzes/${quizId}`, payload)
    return response.data.data
  },

  async deleteQuiz(quizId: number) {
    const response = await apiClient.delete<ApiResponse<null>>(`/teachers/quizzes/${quizId}`)
    return response.data.success
  },

  async addQuizQuestion(quizId: number, payload: QuizQuestionFormPayload) {
    const response = await apiClient.post<ApiResponse<TeacherQuiz>>(`/teachers/quizzes/${quizId}/questions`, payload)
    return response.data.data
  },

  async updateQuizQuestion(questionId: number, payload: QuizQuestionFormPayload) {
    const response = await apiClient.put<ApiResponse<TeacherQuiz>>(`/teachers/quiz-questions/${questionId}`, payload)
    return response.data.data
  },

  async deleteQuizQuestion(questionId: number) {
    const response = await apiClient.delete<ApiResponse<TeacherQuiz>>(`/teachers/quiz-questions/${questionId}`)
    return response.data.data
  },

  async getQuizAttempts(quizId: number) {
    const response = await apiClient.get<ApiResponse<TeacherQuizAttemptSummary[]>>(`/teachers/quizzes/${quizId}/attempts`)
    return response.data.data
  },

  async getMatchingGames(lessonId: number) {
    const response = await apiClient.get<ApiResponse<MatchingGame[]>>(`/teachers/lessons/${lessonId}/matching-games`)
    return response.data.data
  },

  async createMatchingGame(lessonId: number, payload: MatchingGameFormPayload) {
    const response = await apiClient.post<ApiResponse<MatchingGame>>(`/teachers/lessons/${lessonId}/matching-games`, payload)
    return response.data.data
  },

  async updateMatchingGame(gameId: number, payload: MatchingGameFormPayload) {
    const response = await apiClient.put<ApiResponse<MatchingGame>>(`/teachers/matching-games/${gameId}`, payload)
    return response.data.data
  },

  async deleteMatchingGame(gameId: number) {
    const response = await apiClient.delete<ApiResponse<null>>(`/teachers/matching-games/${gameId}`)
    return response.data.success
  },

  async getListeningExercises(lessonId: number) {
    const response = await apiClient.get<ApiResponse<ListeningExercise[]>>(`/teachers/lessons/${lessonId}/listening-exercises`)
    return response.data.data
  },

  async createListeningExercise(lessonId: number, payload: ListeningExerciseFormPayload) {
    const response = await apiClient.post<ApiResponse<ListeningExercise>>(`/teachers/lessons/${lessonId}/listening-exercises`, payload)
    return response.data.data
  },

  async updateListeningExercise(exerciseId: number, payload: ListeningExerciseFormPayload) {
    const response = await apiClient.put<ApiResponse<ListeningExercise>>(`/teachers/listening-exercises/${exerciseId}`, payload)
    return response.data.data
  },

  async deleteListeningExercise(exerciseId: number) {
    const response = await apiClient.delete<ApiResponse<null>>(`/teachers/listening-exercises/${exerciseId}`)
    return response.data.success
  },

  async getTracingExercises(lessonId: number) {
    const response = await apiClient.get<ApiResponse<TracingExercise[]>>(`/teachers/lessons/${lessonId}/tracing-exercises`)
    return response.data.data
  },

  async createTracingExercise(lessonId: number, payload: TracingExerciseFormPayload) {
    const response = await apiClient.post<ApiResponse<TracingExercise>>(`/teachers/lessons/${lessonId}/tracing-exercises`, payload)
    return response.data.data
  },

  async updateTracingExercise(exerciseId: number, payload: TracingExerciseFormPayload) {
    const response = await apiClient.put<ApiResponse<TracingExercise>>(`/teachers/tracing-exercises/${exerciseId}`, payload)
    return response.data.data
  },

  async deleteTracingExercise(exerciseId: number) {
    const response = await apiClient.delete<ApiResponse<null>>(`/teachers/tracing-exercises/${exerciseId}`)
    return response.data.success
  },
}
