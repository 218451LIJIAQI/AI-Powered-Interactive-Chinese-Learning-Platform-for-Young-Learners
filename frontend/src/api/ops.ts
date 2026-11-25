/**
 * API of the ops module: the Account page, support requests, and the admin users, support queue, dashboard, analytics
 * and audit log.
 */
import { apiDelete, apiDownload, apiGet, apiPost, apiPut } from './request'
import type {
  AccountSettingsResponse,
  AdminAnalyticsResponse,
  AdminDashboardResponse,
  AdminInvite,
  AdminUserDetail,
  AdminUserListItem,
  AdminUserQuery,
  AuditEntry,
  AuditQuery,
  EmailChangeResponse,
  NewSupportRequest,
  NotificationPreferences,
  SupportQueueResponse,
  SupportRequestCreated,
  SupportRequestDetail,
  SupportStatus,
} from '@/types/ops'
import type { AppLanguage, AuthResponse, CurrentUserResponse, PageResponse } from '@/types/api'
import type { EmailConfirmation } from '@/types/plan'
import type { PasswordChangeForm } from '@/utils/password'

/** The Account page and signed-in account lifecycle endpoints (every signed-in role). */
export const accountOpsApi = {
  settings() {
    return apiGet<AccountSettingsResponse>('/users/me/account')
  },
  /**
   * This browser's session (Account › This device): `signedInAt`, the local date-time it signed in, or null when the
   * request's token names no session.
   */
  session() {
    return apiGet<{ signedInAt: string | null }>('/users/me/session')
  },
  /** "Send the link again" for sign-up e-mail confirmation (once a minute). */
  resendEmailConfirmation() {
    return apiPost<EmailConfirmation>('/users/me/email-confirmation')
  },
  /** Only the submitted fields change, including when another device edits the same profile. */
  updateProfile(
    payload: { displayName: string; preferredLanguage?: AppLanguage } | { preferredLanguage: AppLanguage },
  ) {
    return apiPut<CurrentUserResponse>('/users/me/profile', payload)
  },
  updatePreferences(payload: Partial<NotificationPreferences>) {
    return apiPut<NotificationPreferences>('/users/me/notification-preferences', payload)
  },
  requestEmailChange(payload: { newEmail: string; currentPassword: string }) {
    return apiPost<EmailChangeResponse>('/users/me/email-change', payload)
  },
  /** "Resend link": a new link to the address that waits for confirmation; the link sent before stops working. */
  resendEmailChange() {
    return apiPost<EmailChangeResponse>('/users/me/email-change/resend')
  },
  /** "Cancel change": the waiting e-mail change ends and its link stops working. */
  cancelEmailChange() {
    return apiDelete<null>('/users/me/email-change')
  },
  /** Returns a fresh access token; the server revokes every other session and sets a new refresh cookie (FRAUTH08). */
  changePassword(payload: PasswordChangeForm) {
    return apiPost<AuthResponse>('/users/me/password', payload)
  },
  deleteAccount(confirmEmail: string) {
    return apiPost<null>('/users/me/delete', { confirmEmail })
  },
}

/**
 * A signed-in learner's "Contact support" (S11.03, X2.21): support answers by e-mail, so the answer is the address the
 * reply goes to.
 */
export const supportApi = {
  create(request: NewSupportRequest) {
    const form = new FormData()
    form.append('topic', request.topic)
    form.append('subject', request.subject)
    form.append('message', request.message)
    if (request.screenshot) form.append('screenshot', request.screenshot)
    return apiPost<SupportRequestCreated>('/users/me/support', form)
  },
}

/** The admin Users section: users and admin invites. */
export const adminUsersApi = {
  list(query: AdminUserQuery) {
    return apiGet<PageResponse<AdminUserListItem>>('/admin/users', { params: query })
  },
  get(id: number) {
    return apiGet<AdminUserDetail>(`/admin/users/${id}`)
  },
  deactivate(id: number, reason: string) {
    return apiPost<AdminUserDetail>(`/admin/users/${id}/deactivate`, { reason })
  },
  /** Runs at once: reactivating needs no reason. */
  reactivate(id: number) {
    return apiPost<AdminUserDetail>(`/admin/users/${id}/reactivate`)
  },
  invites() {
    return apiGet<AdminInvite[]>('/admin/admin-invites')
  },
  /** Sends an invite, or sends a pending one again ("Resend"): a new link replaces the one sent before. */
  invite(email: string) {
    return apiPost<AdminInvite>('/admin/admin-invites', { email })
  },
  /** Withdraws a pending invite: its e-mail link stops working until the cancel is undone. */
  cancelInvite(id: number) {
    return apiDelete<null>(`/admin/admin-invites/${id}`)
  },
  /** The Undo of a cancel: the invite is pending again and the link already sent works again. */
  restoreInvite(id: number) {
    return apiPost<AdminInvite>(`/admin/admin-invites/${id}/restore`)
  },
}

/** The admin Support queue. */
export const adminSupportApi = {
  queue(status: SupportStatus, page: number, size: number) {
    return apiGet<SupportQueueResponse>('/admin/support', { params: { status, page, size } })
  },
  get(id: number) {
    return apiGet<SupportRequestDetail>(`/admin/support/${id}`)
  },
  reply(id: number, body: string) {
    return apiPost<SupportRequestDetail>(`/admin/support/${id}/replies`, { body })
  },
  solve(id: number) {
    return apiPost<SupportRequestDetail>(`/admin/support/${id}/solve`)
  },
  /** The Undo of Mark as solved: back to Answered after a reply, otherwise Open. */
  reopen(id: number) {
    return apiPost<SupportRequestDetail>(`/admin/support/${id}/reopen`)
  },
  attachment(messageId: number) {
    return apiDownload(`/admin/support/attachments/${messageId}`)
  },
}

/** The admin dashboard, analytics and audit log. */
export const adminOverviewApi = {
  dashboard() {
    return apiGet<AdminDashboardResponse>('/admin/dashboard')
  },
  analytics(days: AdminAnalyticsResponse['days']) {
    return apiGet<AdminAnalyticsResponse>('/admin/analytics', { params: { days } })
  },
  auditLog(query: AuditQuery) {
    return apiGet<PageResponse<AuditEntry>>('/admin/audit-logs', { params: query })
  },
  auditEntry(id: number) {
    return apiGet<AuditEntry>(`/admin/audit-logs/${id}`)
  },
  auditActions() {
    return apiGet<string[]>('/admin/audit-logs/actions')
  },
  exportAuditLog(query: Omit<AuditQuery, 'page' | 'size'>) {
    return apiDownload('/admin/audit-logs/export', { params: query })
  },
}
