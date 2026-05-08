import { apiClient } from './apiClient'
import type { TeacherProfilePublicResponse } from '../types/teacherManagement'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

export const publicTeacherService = {
  async getTeacherProfile(teacherId: number) {
    const response = await apiClient.get<ApiResponse<TeacherProfilePublicResponse>>(`/public/teachers/${teacherId}`)
    return response.data.data
  },
}
