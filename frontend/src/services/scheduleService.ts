import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { AdminSchedule, StudentSchedule, TeacherSchedule } from '../types/liveLearning'

export const scheduleService = {
  async getStudentSchedule() {
    const response = await apiClient.get<ApiResponse<StudentSchedule>>('/students/schedule')
    return response.data.data
  },

  async getTeacherSchedule() {
    const response = await apiClient.get<ApiResponse<TeacherSchedule>>('/teachers/schedule')
    return response.data.data
  },

  async getAdminSchedule() {
    const response = await apiClient.get<ApiResponse<AdminSchedule>>('/admin/schedule')
    return response.data.data
  },
}
