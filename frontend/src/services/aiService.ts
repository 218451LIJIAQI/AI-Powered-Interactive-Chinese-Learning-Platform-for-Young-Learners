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

export const aiService = {
  async getAvailability() {
    const response = await apiClient.post<ApiResponse<AiAvailability>>('/ai/availability')
    return response.data.data
  },

  async chat(payload: AiChatRequest) {
    const response = await apiClient.post<ApiResponse<AiChatResponse>>('/ai/chat', payload)
    return response.data.data
  },

  async vocabularyHelper(payload: AiVocabularyHelperRequest) {
    const response = await apiClient.post<ApiResponse<AiVocabularyHelperResponse>>('/ai/vocabulary-helper', payload)
    return response.data.data
  },

  async sentencePractice(payload: AiSentencePracticeRequest) {
    const response = await apiClient.post<ApiResponse<AiSentencePracticeResponse>>('/ai/sentence-practice', payload)
    return response.data.data
  },

  async quizGenerator(payload: AiQuizGeneratorRequest) {
    const response = await apiClient.post<ApiResponse<AiQuizGeneratorResponse>>('/ai/quiz-generator', payload)
    return response.data.data
  },

  async storyGenerator(payload: AiStoryGeneratorRequest) {
    const response = await apiClient.post<ApiResponse<AiStoryGeneratorResponse>>('/ai/story-generator', payload)
    return response.data.data
  },

  async learningRecommendation(payload: AiLearningRecommendationRequest) {
    const response = await apiClient.post<ApiResponse<AiLearningRecommendationResponse>>('/ai/learning-recommendation', payload)
    return response.data.data
  },

  async progressInsight(payload: AiProgressInsightRequest) {
    const response = await apiClient.post<ApiResponse<AiProgressInsightResponse>>('/ai/progress-insight', payload)
    return response.data.data
  },

  async teacherAssistant(payload: AiTeacherAssistantRequest) {
    const response = await apiClient.post<ApiResponse<AiTeacherAssistantResponse>>('/ai/teacher-assistant', payload)
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
