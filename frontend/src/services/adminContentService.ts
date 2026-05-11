import { apiClient } from './apiClient'
import type {
  ContentSubmissionDetail,
  ContentSubmissionItem,
  ManagedContentType,
  PageResponse,
} from '../types/contentManagement'

import type { ApiResponse } from '../types/api'

export const adminContentService = {
  async getSubmissions(params: Record<string, string | number | undefined>) {
    const response = await apiClient.get<ApiResponse<PageResponse<ContentSubmissionItem>>>('/admin/content/submissions', {
      params,
    })
    return response.data.data
  },

  async getSubmissionDetail(contentType: ManagedContentType, id: number) {
    const response = await apiClient.get<ApiResponse<ContentSubmissionDetail>>(`/admin/content/submissions/${contentType}/${id}`)
    return response.data.data
  },

  async approveContent(contentType: ManagedContentType, id: number, note?: string) {
    const response = await apiClient.post<ApiResponse<ContentSubmissionDetail>>(`/admin/content/${contentType}/${id}/approve`, {
      note: note || undefined,
    })
    return response.data.data
  },

  async rejectContent(contentType: ManagedContentType, id: number, reason: string) {
    const response = await apiClient.post<ApiResponse<ContentSubmissionDetail>>(`/admin/content/${contentType}/${id}/reject`, {
      reason,
    })
    return response.data.data
  },

  async publishContent(contentType: ManagedContentType, id: number, note?: string) {
    const response = await apiClient.post<ApiResponse<ContentSubmissionDetail>>(`/admin/content/${contentType}/${id}/publish`, {
      note: note || undefined,
    })
    return response.data.data
  },

  async archiveContent(contentType: ManagedContentType, id: number, note?: string) {
    const response = await apiClient.patch<ApiResponse<ContentSubmissionDetail>>(`/admin/content/${contentType}/${id}/archive`, {
      note: note || undefined,
    })
    return response.data.data
  },
}
