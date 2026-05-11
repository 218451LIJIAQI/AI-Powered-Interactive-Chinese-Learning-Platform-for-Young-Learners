import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { CreateReviewPayload, ReviewItem, UpdateReviewPayload } from '../types/communication'

export const reviewService = {
  async createReview(payload: CreateReviewPayload) {
    const response = await apiClient.post<ApiResponse<ReviewItem>>('/reviews', payload)
    return response.data.data
  },

  async getMyReviews() {
    const response = await apiClient.get<ApiResponse<ReviewItem[]>>('/reviews/my')
    return response.data.data
  },

  async updateReview(id: number, payload: UpdateReviewPayload) {
    const response = await apiClient.put<ApiResponse<ReviewItem>>(`/reviews/${id}`, payload)
    return response.data.data
  },

  async hideMyReview(id: number) {
    const response = await apiClient.delete<ApiResponse<ReviewItem>>(`/reviews/${id}`)
    return response.data.data
  },

  async getTeacherReviews() {
    const response = await apiClient.get<ApiResponse<ReviewItem[]>>('/teachers/reviews')
    return response.data.data
  },

  async getAdminReviews() {
    const response = await apiClient.get<ApiResponse<ReviewItem[]>>('/admin/reviews')
    return response.data.data
  },

  async hideReview(id: number) {
    const response = await apiClient.patch<ApiResponse<ReviewItem>>(`/admin/reviews/${id}/hide`)
    return response.data.data
  },

  async showReview(id: number) {
    const response = await apiClient.patch<ApiResponse<ReviewItem>>(`/admin/reviews/${id}/show`)
    return response.data.data
  },

  async flagReview(id: number) {
    const response = await apiClient.patch<ApiResponse<ReviewItem>>(`/admin/reviews/${id}/flag`)
    return response.data.data
  },

  async getPublicReviews(targetType: string, targetId: number) {
    const response = await apiClient.get<ApiResponse<ReviewItem[]>>('/public/reviews', {
      params: { targetType, targetId },
    })
    return response.data.data
  },
}
