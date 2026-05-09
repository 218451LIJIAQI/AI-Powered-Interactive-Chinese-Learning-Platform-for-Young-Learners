import { apiClient } from './apiClient'
import type {
  CreateSupportTicketPayload,
  SupportTicketDetail,
  SupportTicketItem,
  SupportTicketMessagePayload,
  UpdateSupportTicketPriorityPayload,
  UpdateSupportTicketStatusPayload,
} from '../types/communication'

type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  errors: string[]
}

export const supportTicketService = {
  async createTicket(payload: CreateSupportTicketPayload) {
    const response = await apiClient.post<ApiResponse<SupportTicketItem>>('/support-tickets', payload)
    return response.data.data
  },

  async getMyTickets() {
    const response = await apiClient.get<ApiResponse<SupportTicketItem[]>>('/support-tickets/my')
    return response.data.data
  },

  async getMyTicket(id: number) {
    const response = await apiClient.get<ApiResponse<SupportTicketDetail>>(`/support-tickets/${id}`)
    return response.data.data
  },

  async addMyMessage(id: number, payload: SupportTicketMessagePayload) {
    const response = await apiClient.post<ApiResponse<SupportTicketDetail>>(`/support-tickets/${id}/messages`, payload)
    return response.data.data
  },

  async getAdminTickets() {
    const response = await apiClient.get<ApiResponse<SupportTicketItem[]>>('/admin/support-tickets')
    return response.data.data
  },

  async getAdminTicket(id: number) {
    const response = await apiClient.get<ApiResponse<SupportTicketDetail>>(`/admin/support-tickets/${id}`)
    return response.data.data
  },

  async addAdminMessage(id: number, payload: SupportTicketMessagePayload) {
    const response = await apiClient.post<ApiResponse<SupportTicketDetail>>(`/admin/support-tickets/${id}/messages`, payload)
    return response.data.data
  },

  async updateAdminStatus(id: number, payload: UpdateSupportTicketStatusPayload) {
    const response = await apiClient.patch<ApiResponse<SupportTicketItem>>(`/admin/support-tickets/${id}/status`, payload)
    return response.data.data
  },

  async updateAdminPriority(id: number, payload: UpdateSupportTicketPriorityPayload) {
    const response = await apiClient.patch<ApiResponse<SupportTicketItem>>(`/admin/support-tickets/${id}/priority`, payload)
    return response.data.data
  },
}
