import { apiClient } from './apiClient'
import type { ModerationAction, ModerationActionPayload, ModerationRecordItem } from '../types/communication'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

const actionPath: Record<ModerationAction, string> = {
  HIDE: 'hide',
  UNPUBLISH: 'unpublish',
  ARCHIVE: 'archive',
  REQUEST_REVISION: 'request-revision',
  RESTORE: 'restore',
}

export const moderationService = {
  async getTeacherModerationRecords() {
    const response = await apiClient.get<ApiResponse<ModerationRecordItem[]>>('/teachers/moderation-records')
    return response.data.data
  },

  async getAdminModerationRecords() {
    const response = await apiClient.get<ApiResponse<ModerationRecordItem[]>>('/admin/moderation-records')
    return response.data.data
  },

  async getAdminModerationRecord(id: number) {
    const response = await apiClient.get<ApiResponse<ModerationRecordItem>>(`/admin/moderation-records/${id}`)
    return response.data.data
  },

  async applyAction(action: ModerationAction, payload: ModerationActionPayload) {
    const response = await apiClient.post<ApiResponse<ModerationRecordItem>>(`/admin/moderation/${actionPath[action]}`, payload)
    return response.data.data
  },
}
