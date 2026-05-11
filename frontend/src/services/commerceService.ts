import { apiClient } from './apiClient'
import type {
  Cart,
  CheckoutResponse,
  CommissionSetting,
  PaymentRecord,
  PayoutRequest,
  RefundRequest,
  StudentSubscription,
  SubscriptionPlan,
  TeacherEarning,
  TeacherEarningSummary,
} from '../types/commerce'

import type { ApiResponse } from '../types/api'

export const commerceService = {
  async getCart() {
    const response = await apiClient.get<ApiResponse<Cart>>('/students/cart')
    return response.data.data
  },

  async addCartItem(payload: { itemType: string; itemId: number; featureKey?: string }) {
    const response = await apiClient.post<ApiResponse<Cart>>('/students/cart/items', payload)
    return response.data.data
  },

  async removeCartItem(cartItemId: number) {
    const response = await apiClient.delete<ApiResponse<Cart>>(`/students/cart/items/${cartItemId}`)
    return response.data.data
  },

  async clearCart() {
    const response = await apiClient.delete<ApiResponse<Cart>>('/students/cart/clear')
    return response.data.data
  },

  async checkoutCart() {
    const response = await apiClient.post<ApiResponse<CheckoutResponse>>('/students/checkout/cart')
    return response.data.data
  },

  async buyNow(payload: { itemType: string; itemId: number; featureKey?: string }) {
    const response = await apiClient.post<ApiResponse<CheckoutResponse>>('/students/checkout/buy-now', payload)
    return response.data.data
  },

  async getPayments() {
    const response = await apiClient.get<ApiResponse<PaymentRecord[]>>('/students/payments')
    return response.data.data
  },

  async getAdminPayments() {
    const response = await apiClient.get<ApiResponse<PaymentRecord[]>>('/admin/payments')
    return response.data.data
  },

  async getPayment(paymentId: number) {
    const response = await apiClient.get<ApiResponse<PaymentRecord>>(`/students/payments/${paymentId}`)
    return response.data.data
  },

  async verifyPaymentStatus(paymentId: number) {
    const response = await apiClient.get<ApiResponse<PaymentRecord>>(`/payments/status/${paymentId}`)
    return response.data.data
  },

  async getSubscriptionPlans() {
    const response = await apiClient.get<ApiResponse<SubscriptionPlan[]>>('/students/subscription-plans')
    return response.data.data
  },

  async getCurrentSubscription() {
    const response = await apiClient.get<ApiResponse<StudentSubscription | null>>('/students/subscription')
    return response.data.data
  },

  async checkoutSubscription(payload: { itemType: string; itemId: number; featureKey?: string }) {
    const response = await apiClient.post<ApiResponse<CheckoutResponse>>('/students/subscription/checkout', payload)
    return response.data.data
  },

  async getRefundRequests() {
    const response = await apiClient.get<ApiResponse<RefundRequest[]>>('/students/refund-requests')
    return response.data.data
  },

  async createRefundRequest(payload: { orderId: number; reason: string }) {
    const response = await apiClient.post<ApiResponse<RefundRequest>>('/students/refund-requests', payload)
    return response.data.data
  },

  async getTeacherEarnings() {
    const response = await apiClient.get<ApiResponse<TeacherEarning[]>>('/teachers/earnings')
    return response.data.data
  },

  async getTeacherEarningSummary() {
    const response = await apiClient.get<ApiResponse<TeacherEarningSummary>>('/teachers/earnings/summary')
    return response.data.data
  },

  async getTeacherPayoutRequests() {
    const response = await apiClient.get<ApiResponse<PayoutRequest[]>>('/teachers/payout-requests')
    return response.data.data
  },

  async createPayoutRequest(payload: { amount: number; requestedNote?: string }) {
    const response = await apiClient.post<ApiResponse<PayoutRequest>>('/teachers/payout-requests', payload)
    return response.data.data
  },

  async getAdminRefundRequests() {
    const response = await apiClient.get<ApiResponse<RefundRequest[]>>('/admin/refund-requests')
    return response.data.data
  },

  async approveRefund(id: number) {
    const response = await apiClient.post<ApiResponse<RefundRequest>>(`/admin/refund-requests/${id}/approve`)
    return response.data.data
  },

  async rejectRefund(id: number, payload: { reason: string }) {
    const response = await apiClient.post<ApiResponse<RefundRequest>>(`/admin/refund-requests/${id}/reject`, payload)
    return response.data.data
  },

  async markRefunded(id: number) {
    const response = await apiClient.post<ApiResponse<RefundRequest>>(`/admin/refund-requests/${id}/mark-refunded`)
    return response.data.data
  },

  async getAdminPayoutRequests() {
    const response = await apiClient.get<ApiResponse<PayoutRequest[]>>('/admin/payout-requests')
    return response.data.data
  },

  async approvePayout(id: number) {
    const response = await apiClient.post<ApiResponse<PayoutRequest>>(`/admin/payout-requests/${id}/approve`)
    return response.data.data
  },

  async rejectPayout(id: number, payload: { reason: string }) {
    const response = await apiClient.post<ApiResponse<PayoutRequest>>(`/admin/payout-requests/${id}/reject`, payload)
    return response.data.data
  },

  async markPayoutPaid(id: number) {
    const response = await apiClient.post<ApiResponse<PayoutRequest>>(`/admin/payout-requests/${id}/mark-paid`)
    return response.data.data
  },

  async getAdminSubscriptionPlans() {
    const response = await apiClient.get<ApiResponse<SubscriptionPlan[]>>('/admin/subscription-plans')
    return response.data.data
  },

  async createSubscriptionPlan(payload: { name: string; price: number; benefits: string; active?: boolean }) {
    const response = await apiClient.post<ApiResponse<SubscriptionPlan>>('/admin/subscription-plans', payload)
    return response.data.data
  },

  async updateSubscriptionPlan(id: number, payload: { name: string; price: number; benefits: string; active?: boolean }) {
    const response = await apiClient.put<ApiResponse<SubscriptionPlan>>(`/admin/subscription-plans/${id}`, payload)
    return response.data.data
  },

  async activateSubscriptionPlan(id: number) {
    const response = await apiClient.patch<ApiResponse<SubscriptionPlan>>(`/admin/subscription-plans/${id}/activate`)
    return response.data.data
  },

  async deactivateSubscriptionPlan(id: number) {
    const response = await apiClient.patch<ApiResponse<SubscriptionPlan>>(`/admin/subscription-plans/${id}/deactivate`)
    return response.data.data
  },

  async getCommissionSetting() {
    const response = await apiClient.get<ApiResponse<CommissionSetting>>('/admin/system-settings/commission')
    return response.data.data
  },

  async updateCommissionSetting(payload: { commissionRate: number }) {
    const response = await apiClient.put<ApiResponse<CommissionSetting>>('/admin/system-settings/commission', payload)
    return response.data.data
  },
}
