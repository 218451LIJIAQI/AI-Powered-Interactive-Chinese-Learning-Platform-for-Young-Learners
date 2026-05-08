import { apiClient } from './apiClient'
import type { AdminDashboardSummary, StudentDashboardSummary, TeacherDashboardSummary } from '../types/dashboard'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

export const dashboardService = {
  async getStudentDashboard() {
    const response = await apiClient.get<ApiResponse<StudentDashboardSummary>>('/students/dashboard')
    return response.data.data
  },
  async getTeacherDashboard() {
    const response = await apiClient.get<ApiResponse<TeacherDashboardSummary>>('/teachers/dashboard')
    return response.data.data
  },
  async getAdminDashboard() {
    const response = await apiClient.get<ApiResponse<AdminDashboardSummary>>('/admin/dashboard')
    return response.data.data
  },
}
