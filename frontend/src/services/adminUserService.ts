import { apiClient } from './apiClient'
import type { AdminStudentListItem, AdminUserListItem, PageResponse } from '../types/adminManagement'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

export const adminUserService = {
  async getUsers(params: Record<string, string | number | undefined>) {
    const response = await apiClient.get<ApiResponse<PageResponse<AdminUserListItem>>>('/admin/users', { params })
    return response.data.data
  },

  async getStudents(params: Record<string, string | number | undefined>) {
    const response = await apiClient.get<ApiResponse<PageResponse<AdminStudentListItem>>>('/admin/students', { params })
    return response.data.data
  },
}
