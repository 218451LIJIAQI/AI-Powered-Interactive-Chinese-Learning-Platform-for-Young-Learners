import { apiClient } from './apiClient'
import type {
  JoinLiveClassResult,
  LiveClass,
  LiveClassEnrolment,
  LiveClassFormPayload,
  RescheduleLiveClassPayload,
} from '../types/liveLearning'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

export const liveClassService = {
  async getTeacherLiveClasses() {
    const response = await apiClient.get<ApiResponse<LiveClass[]>>('/teachers/live-classes')
    return response.data.data
  },

  async getTeacherLiveClass(id: number) {
    const response = await apiClient.get<ApiResponse<LiveClass>>(`/teachers/live-classes/${id}`)
    return response.data.data
  },

  async createLiveClass(payload: LiveClassFormPayload) {
    const response = await apiClient.post<ApiResponse<LiveClass>>('/teachers/live-classes', payload)
    return response.data.data
  },

  async updateLiveClass(id: number, payload: LiveClassFormPayload) {
    const response = await apiClient.put<ApiResponse<LiveClass>>(`/teachers/live-classes/${id}`, payload)
    return response.data.data
  },

  async updateLiveClassStatus(id: number, status: string) {
    const response = await apiClient.patch<ApiResponse<LiveClass>>(`/teachers/live-classes/${id}/status`, { status })
    return response.data.data
  },

  async cancelLiveClass(id: number) {
    const response = await apiClient.patch<ApiResponse<LiveClass>>(`/teachers/live-classes/${id}/cancel`)
    return response.data.data
  },

  async rescheduleLiveClass(id: number, payload: RescheduleLiveClassPayload) {
    const response = await apiClient.patch<ApiResponse<LiveClass>>(`/teachers/live-classes/${id}/reschedule`, payload)
    return response.data.data
  },

  async getLiveClassEnrolments(id: number) {
    const response = await apiClient.get<ApiResponse<LiveClassEnrolment[]>>(`/teachers/live-classes/${id}/enrolments`)
    return response.data.data
  },

  async getStudentLiveClasses() {
    const response = await apiClient.get<ApiResponse<LiveClass[]>>('/students/live-classes')
    return response.data.data
  },

  async getStudentLiveClass(id: number) {
    const response = await apiClient.get<ApiResponse<LiveClass>>(`/students/live-classes/${id}`)
    return response.data.data
  },

  async enrolLiveClass(id: number) {
    const response = await apiClient.post<ApiResponse<LiveClass>>(`/students/live-classes/${id}/enrol`)
    return response.data.data
  },

  async cancelLiveClassEnrolment(id: number) {
    const response = await apiClient.post<ApiResponse<LiveClass>>(`/students/live-classes/${id}/cancel-enrolment`)
    return response.data.data
  },

  async joinLiveClass(id: number) {
    const response = await apiClient.get<ApiResponse<JoinLiveClassResult>>(`/students/live-classes/${id}/join`)
    return response.data.data
  },

  async getAdminLiveClasses() {
    const response = await apiClient.get<ApiResponse<LiveClass[]>>('/admin/live-classes')
    return response.data.data
  },

  async getAdminLiveClass(id: number) {
    const response = await apiClient.get<ApiResponse<LiveClass>>(`/admin/live-classes/${id}`)
    return response.data.data
  },
}
