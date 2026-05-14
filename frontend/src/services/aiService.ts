import { apiClient } from './apiClient'
import type {
  AdminAiUsageLogPage,
  AiAvailability,
  AiChatRequest,
  AiChatResponse,
  AiLearningRecommendationRequest,
  AiLearningRecommendationResponse,
  AiProgressInsightRequest,
  AiProgressInsightResponse,
  AiPronunciationFeedbackResponse,
  AiQuizGeneratorRequest,
  AiQuizGeneratorResponse,
  AiSentencePracticeRequest,
  AiSentencePracticeResponse,
  AiStoryGeneratorRequest,
  AiStoryGeneratorResponse,
  AiTeacherAssistantRequest,
  AiTeacherAssistantResponse,
  AiVocabularyHelperRequest,
  AiVocabularyHelperResponse,
  AiWritingFeedbackResponse,
} from '../types/ai'

import type { ApiResponse } from '../types/api'

// AI generation calls (chat / quiz / story / etc.) routinely take longer
// than the global 15-second axios timeout in apiClient because the backend
// itself waits up to AI_TIMEOUT_SECONDS (default 30s) for the upstream
// provider. Without this per-request override, axios would abort the
// request before the backend's own friendly-fallback path could return,
// causing the student to see a generic timeout instead of the
// availability-aware fallback content. Quick non-generative calls
// (availability probe, admin usage-log listing) keep the default
// timeout because they hit DB/config only.
const AI_REQUEST_TIMEOUT_MS = 45000

export const aiService = {
  async getAvailability() {
    const response = await apiClient.get<ApiResponse<AiAvailability>>('/ai/availability')
    return response.data.data
  },

  async chat(payload: AiChatRequest) {
    const response = await apiClient.post<ApiResponse<AiChatResponse>>('/ai/chat', payload, {
      timeout: AI_REQUEST_TIMEOUT_MS,
    })
    return response.data.data
  },

  async vocabularyHelper(payload: AiVocabularyHelperRequest) {
    const response = await apiClient.post<ApiResponse<AiVocabularyHelperResponse>>('/ai/vocabulary-helper', payload, {
      timeout: AI_REQUEST_TIMEOUT_MS,
    })
    return response.data.data
  },

  async sentencePractice(payload: AiSentencePracticeRequest) {
    const response = await apiClient.post<ApiResponse<AiSentencePracticeResponse>>('/ai/sentence-practice', payload, {
      timeout: AI_REQUEST_TIMEOUT_MS,
    })
    return response.data.data
  },

  async quizGenerator(payload: AiQuizGeneratorRequest) {
    const response = await apiClient.post<ApiResponse<AiQuizGeneratorResponse>>('/ai/quiz-generator', payload, {
      timeout: AI_REQUEST_TIMEOUT_MS,
    })
    return response.data.data
  },

  async storyGenerator(payload: AiStoryGeneratorRequest) {
    const response = await apiClient.post<ApiResponse<AiStoryGeneratorResponse>>('/ai/story-generator', payload, {
      timeout: AI_REQUEST_TIMEOUT_MS,
    })
    return response.data.data
  },

  async learningRecommendation(payload: AiLearningRecommendationRequest) {
    const response = await apiClient.post<ApiResponse<AiLearningRecommendationResponse>>('/ai/learning-recommendation', payload, {
      timeout: AI_REQUEST_TIMEOUT_MS,
    })
    return response.data.data
  },

  async progressInsight(payload: AiProgressInsightRequest) {
    const response = await apiClient.post<ApiResponse<AiProgressInsightResponse>>('/ai/progress-insight', payload, {
      timeout: AI_REQUEST_TIMEOUT_MS,
    })
    return response.data.data
  },

  async teacherAssistant(payload: AiTeacherAssistantRequest) {
    const response = await apiClient.post<ApiResponse<AiTeacherAssistantResponse>>('/ai/teacher-assistant', payload, {
      timeout: AI_REQUEST_TIMEOUT_MS,
    })
    return response.data.data
  },

  async pronunciationFeedback(formData: FormData) {
    const response = await apiClient.post<ApiResponse<AiPronunciationFeedbackResponse>>('/ai/pronunciation-feedback', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    return response.data.data
  },

  async writingFeedback(formData: FormData) {
    const response = await apiClient.post<ApiResponse<AiWritingFeedbackResponse>>('/ai/writing-feedback', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    return response.data.data
  },

  async getAdminUsageLogs(params: Record<string, string | number | undefined>) {
    const response = await apiClient.get<ApiResponse<AdminAiUsageLogPage>>('/admin/ai/usage-logs', { params })
    return response.data.data
  },
}
