import { apiClient } from './apiClient'
import type {
  BadgeSummary,
  DailyStreakSummary,
  FlashcardItem,
  FlashcardPracticeResponse,
  ListeningExercisePlay,
  ListeningExerciseSubmissionPayload,
  ListeningExerciseSubmissionResult,
  MatchingGamePlay,
  MatchingGameSubmissionPayload,
  MatchingGameSubmissionResult,
  PointsTransactionSummary,
  QuizAttemptResult,
  QuizSubmissionPayload,
  RewardSummary,
  StudentLessonActivitySummary,
  StudentLessonPlayer,
  StudentProgressSummary,
  StudentQuiz,
  TracingExerciseCompletionPayload,
  TracingExerciseCompletionResult,
  TracingExercisePlay,
  WrongAnswerItem,
} from '../types/learning'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

export const studentLearningService = {
  async getLessonPlayer(lessonId: number) {
    const response = await apiClient.get<ApiResponse<StudentLessonPlayer>>(`/students/lessons/${lessonId}`)
    return response.data.data
  },

  async getLessonActivities(lessonId: number) {
    const response = await apiClient.get<ApiResponse<StudentLessonActivitySummary[]>>(`/students/lessons/${lessonId}/activities`)
    return response.data.data
  },

  async getFlashcards(lessonId: number) {
    const response = await apiClient.get<ApiResponse<FlashcardItem[]>>(`/students/lessons/${lessonId}/flashcards`)
    return response.data.data
  },

  async practiceFlashcard(flashcardId: number) {
    const response = await apiClient.post<ApiResponse<FlashcardPracticeResponse>>(`/students/flashcards/${flashcardId}/practice`)
    return response.data.data
  },

  async getQuiz(quizId: number) {
    const response = await apiClient.get<ApiResponse<StudentQuiz>>(`/students/quizzes/${quizId}`)
    return response.data.data
  },

  async submitQuizAttempt(quizId: number, payload: QuizSubmissionPayload) {
    const response = await apiClient.post<ApiResponse<QuizAttemptResult>>(`/students/quizzes/${quizId}/attempts`, payload)
    return response.data.data
  },

  async getQuizAttempt(attemptId: number) {
    const response = await apiClient.get<ApiResponse<QuizAttemptResult>>(`/students/quiz-attempts/${attemptId}`)
    return response.data.data
  },

  async getWrongAnswers() {
    const response = await apiClient.get<ApiResponse<WrongAnswerItem[]>>('/students/wrong-answers')
    return response.data.data
  },

  async getAttemptWrongAnswers(attemptId: number) {
    const response = await apiClient.get<ApiResponse<WrongAnswerItem[]>>(`/students/quiz-attempts/${attemptId}/wrong-answers`)
    return response.data.data
  },

  async getMatchingGame(gameId: number) {
    const response = await apiClient.get<ApiResponse<MatchingGamePlay>>(`/students/matching-games/${gameId}`)
    return response.data.data
  },

  async submitMatchingGame(gameId: number, payload: MatchingGameSubmissionPayload) {
    const response = await apiClient.post<ApiResponse<MatchingGameSubmissionResult>>(`/students/matching-games/${gameId}/submit`, payload)
    return response.data.data
  },

  async getListeningExercise(exerciseId: number) {
    const response = await apiClient.get<ApiResponse<ListeningExercisePlay>>(`/students/listening-exercises/${exerciseId}`)
    return response.data.data
  },

  async submitListeningExercise(exerciseId: number, payload: ListeningExerciseSubmissionPayload) {
    const response = await apiClient.post<ApiResponse<ListeningExerciseSubmissionResult>>(`/students/listening-exercises/${exerciseId}/submit`, payload)
    return response.data.data
  },

  async getTracingExercise(exerciseId: number) {
    const response = await apiClient.get<ApiResponse<TracingExercisePlay>>(`/students/tracing-exercises/${exerciseId}`)
    return response.data.data
  },

  async completeTracingExercise(exerciseId: number, payload: TracingExerciseCompletionPayload = {}) {
    const response = await apiClient.post<ApiResponse<TracingExerciseCompletionResult>>(`/students/tracing-exercises/${exerciseId}/complete`, payload)
    return response.data.data
  },

  async getProgress() {
    const response = await apiClient.get<ApiResponse<StudentProgressSummary>>('/students/progress')
    return response.data.data
  },

  async getRewards() {
    const response = await apiClient.get<ApiResponse<RewardSummary>>('/students/rewards')
    return response.data.data
  },

  async getBadges() {
    const response = await apiClient.get<ApiResponse<BadgeSummary[]>>('/students/rewards/badges')
    return response.data.data
  },

  async getPointsHistory() {
    const response = await apiClient.get<ApiResponse<PointsTransactionSummary[]>>('/students/rewards/points-history')
    return response.data.data
  },

  async getStreak() {
    const response = await apiClient.get<ApiResponse<DailyStreakSummary>>('/students/rewards/streak')
    return response.data.data
  },
}
