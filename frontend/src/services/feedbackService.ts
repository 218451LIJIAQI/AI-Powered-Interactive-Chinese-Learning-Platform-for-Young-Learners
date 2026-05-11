import { apiClient } from './apiClient'
import type {
  CreateTeacherFeedbackPayload,
  FeedbackCandidate,
  TeacherFeedbackItem,
  UpdateTeacherFeedbackPayload,
} from '../types/communication'

import type { ApiResponse } from '../types/api'

export const feedbackService = {
  async getStudentFeedback() {
    const response = await apiClient.get<ApiResponse<TeacherFeedbackItem[]>>('/students/feedback')
    return response.data.data
  },

  async getStudentFeedbackById(id: number) {
    const response = await apiClient.get<ApiResponse<TeacherFeedbackItem>>(`/students/feedback/${id}`)
    return response.data.data
  },

  async createTeacherFeedback(payload: CreateTeacherFeedbackPayload) {
    const response = await apiClient.post<ApiResponse<TeacherFeedbackItem>>('/teachers/feedback', payload)
    return response.data.data
  },

  async getTeacherFeedback() {
    const response = await apiClient.get<ApiResponse<TeacherFeedbackItem[]>>('/teachers/feedback')
    return response.data.data
  },

  async updateTeacherFeedback(id: number, payload: UpdateTeacherFeedbackPayload) {
    const response = await apiClient.put<ApiResponse<TeacherFeedbackItem>>(`/teachers/feedback/${id}`, payload)
    return response.data.data
  },

  async getTeacherFeedbackCandidates() {
    const response = await apiClient.get<ApiResponse<FeedbackCandidate[]>>('/teachers/feedback/candidates')
    return response.data.data
  },

  async getAdminFeedback() {
    const response = await apiClient.get<ApiResponse<TeacherFeedbackItem[]>>('/admin/feedback')
    return response.data.data
  },
}
