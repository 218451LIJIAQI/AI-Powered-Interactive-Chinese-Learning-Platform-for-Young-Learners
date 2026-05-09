import { apiClient } from './apiClient'
import type { AdminReportActionPayload, CreateReportPayload, ReportItem } from '../types/communication'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

export const reportService = {
  async createReport(payload: CreateReportPayload) {
    const response = await apiClient.post<ApiResponse<ReportItem>>('/reports', payload)
    return response.data.data
  },

  async getMyReports() {
    const response = await apiClient.get<ApiResponse<ReportItem[]>>('/reports/my')
    return response.data.data
  },

  async getMyReport(id: number) {
    const response = await apiClient.get<ApiResponse<ReportItem>>(`/reports/my/${id}`)
    return response.data.data
  },

  async getTeacherReports() {
    const response = await apiClient.get<ApiResponse<ReportItem[]>>('/teachers/reports')
    return response.data.data
  },

  async getTeacherReport(id: number) {
    const response = await apiClient.get<ApiResponse<ReportItem>>(`/teachers/reports/${id}`)
    return response.data.data
  },

  async getAdminReports() {
    const response = await apiClient.get<ApiResponse<ReportItem[]>>('/admin/reports')
    return response.data.data
  },

  async getAdminReport(id: number) {
    const response = await apiClient.get<ApiResponse<ReportItem>>(`/admin/reports/${id}`)
    return response.data.data
  },

  async reviewReport(id: number, payload?: AdminReportActionPayload) {
    const response = await apiClient.patch<ApiResponse<ReportItem>>(`/admin/reports/${id}/review`, payload ?? {})
    return response.data.data
  },

  async dismissReport(id: number, payload?: AdminReportActionPayload) {
    const response = await apiClient.patch<ApiResponse<ReportItem>>(`/admin/reports/${id}/dismiss`, payload ?? {})
    return response.data.data
  },

  async markActionTaken(id: number, payload?: AdminReportActionPayload) {
    const response = await apiClient.patch<ApiResponse<ReportItem>>(`/admin/reports/${id}/mark-action-taken`, payload ?? {})
    return response.data.data
  },

  async requestRevision(id: number, payload?: AdminReportActionPayload) {
    const response = await apiClient.patch<ApiResponse<ReportItem>>(`/admin/reports/${id}/request-revision`, payload ?? {})
    return response.data.data
  },
}
