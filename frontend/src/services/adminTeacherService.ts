import { apiClient } from './apiClient'
import type { ApiResponse } from '../types/api'
import type { AdminTeacherDetail, AdminTeacherListItem, PageResponse } from '../types/adminManagement'

export const adminTeacherService = {
  async getPendingTeachers() {
    const response = await apiClient.get<ApiResponse<AdminTeacherListItem[]>>('/admin/teachers/pending')
    return response.data.data
  },

  async getTeachers(params: Record<string, string | number | undefined>) {
    const response = await apiClient.get<ApiResponse<PageResponse<AdminTeacherListItem>>>('/admin/teachers', { params })
    return response.data.data
  },

  async getTeacherDetail(teacherId: number) {
    const response = await apiClient.get<ApiResponse<AdminTeacherDetail>>(`/admin/teachers/${teacherId}`)
    return response.data.data
  },

  async approveTeacher(teacherId: number, note?: string) {
    const response = await apiClient.post<ApiResponse<AdminTeacherDetail>>(`/admin/teachers/${teacherId}/approve`, {
      note: note || undefined,
    })
    return response.data.data
  },

  async rejectTeacher(teacherId: number, reason: string) {
    const response = await apiClient.post<ApiResponse<AdminTeacherDetail>>(`/admin/teachers/${teacherId}/reject`, {
      reason,
    })
    return response.data.data
  },

  getTeacherDocumentUrl(documentId: number) {
    return `${apiClient.defaults.baseURL}/admin/teacher-documents/${documentId}`
  },
}
