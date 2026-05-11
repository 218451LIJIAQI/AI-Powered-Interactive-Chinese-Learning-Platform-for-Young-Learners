import { apiClient } from './apiClient'
import type {
  TeacherProfileResponse,
  TeacherVerificationStatusResponse,
  UpdateTeacherProfilePayload,
} from '../types/teacherManagement'

import type { ApiResponse } from '../types/api'

export const teacherService = {
  async getMyProfile() {
    const response = await apiClient.get<ApiResponse<TeacherProfileResponse>>('/teachers/me')
    return response.data.data
  },

  async updateMyProfile(payload: UpdateTeacherProfilePayload) {
    const response = await apiClient.put<ApiResponse<TeacherProfileResponse>>('/teachers/me/profile', payload)
    return response.data.data
  },

  async getVerificationStatus() {
    const response = await apiClient.get<ApiResponse<TeacherVerificationStatusResponse>>('/teachers/verification-status')
    return response.data.data
  },

  async uploadVerificationDocument(formData: FormData) {
    const response = await apiClient.post<ApiResponse<TeacherVerificationStatusResponse>>(
      '/teachers/verification-documents',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      },
    )
    return response.data.data
  },

  async resubmitVerification() {
    const response = await apiClient.post<ApiResponse<TeacherVerificationStatusResponse>>('/teachers/resubmit-verification')
    return response.data.data
  },

  getVerificationDocumentUrl(documentId: number) {
    return `${apiClient.defaults.baseURL}/teachers/verification-documents/${documentId}`
  },
}
