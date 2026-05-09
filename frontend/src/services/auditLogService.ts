import { apiClient } from './apiClient'
import type { AuditLogItem } from '../types/communication'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

export const auditLogService = {
  async getAuditLogs(params?: Record<string, string | number | undefined>) {
    const response = await apiClient.get<ApiResponse<AuditLogItem[]>>('/admin/audit-logs', { params })
    return response.data.data
  },

  async getAuditLog(id: number) {
    const response = await apiClient.get<ApiResponse<AuditLogItem>>(`/admin/audit-logs/${id}`)
    return response.data.data
  },
}
