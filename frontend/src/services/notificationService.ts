import { apiClient } from './apiClient'
import type { BulkOperationResult, NotificationItem, NotificationUnreadCount } from '../types/communication'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

export const notificationService = {
  async getNotifications() {
    const response = await apiClient.get<ApiResponse<NotificationItem[]>>('/notifications')
    return response.data.data
  },

  async getUnreadCount() {
    const response = await apiClient.get<ApiResponse<NotificationUnreadCount>>('/notifications/unread-count')
    return response.data.data
  },

  async markAsRead(id: number) {
    const response = await apiClient.patch<ApiResponse<NotificationItem>>(`/notifications/${id}/read`)
    return response.data.data
  },

  async markAllAsRead() {
    const response = await apiClient.patch<ApiResponse<BulkOperationResult>>('/notifications/read-all')
    return response.data.data
  },

  async getAdminNotifications() {
    const response = await apiClient.get<ApiResponse<NotificationItem[]>>('/admin/notifications')
    return response.data.data
  },
}
