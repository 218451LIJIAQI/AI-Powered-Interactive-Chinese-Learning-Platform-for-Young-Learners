import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { TeacherProfilePublicResponse } from '../types/teacherManagement'

export const publicTeacherService = {
  async getTeacherProfile(teacherId: number) {
    const response = await apiClient.get<ApiResponse<TeacherProfilePublicResponse>>(`/public/teachers/${teacherId}`)
    return response.data.data
  },
}
