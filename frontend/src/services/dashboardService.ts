import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type {
  AdminAnalyticsResponse,
  StudentAnalyticsResponse,
  TeacherAnalyticsResponse,
} from '../types/analytics'
import type { AdminDashboardSummary, StudentDashboardSummary, TeacherDashboardSummary } from '../types/dashboard'

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
  async getStudentAnalytics() {
    const response = await apiClient.get<ApiResponse<StudentAnalyticsResponse>>('/students/analytics')
    return response.data.data
  },
  async getTeacherAnalytics() {
    const response = await apiClient.get<ApiResponse<TeacherAnalyticsResponse>>('/teachers/analytics')
    return response.data.data
  },
  async getAdminAnalytics() {
    const response = await apiClient.get<ApiResponse<AdminAnalyticsResponse>>('/admin/analytics')
    return response.data.data
  },
}
