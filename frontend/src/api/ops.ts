/**
 * API of the ops module: the public Help form and account links, the Account page, Help & support, teacher
 * verification, and the admin users, applications, support queue, dashboard, analytics and audit log.
 */
import { apiDownload, apiGet, apiPost, apiPut } from './services'
import type {
  AccountSettingsResponse,
  AdminAnalyticsResponse,
  AdminDashboardResponse,
  AdminDeactivationResult,
  AdminInvite,
  AdminUserDetail,
  AdminUserListItem,
  AdminUserQuery,
  AuditEntry,
  AuditQuery,
  EmailChangeResponse,
  LinkTokenResponse,
  NewSupportRequest,
  NotificationPreferences,
  SupportQueueResponse,
  SupportRequestDetail,
  SupportRequestSummary,
  SupportStatus,
  TeacherApplication,
  TeacherApplicationSummary,
  TeacherDocumentType,
  TeacherProfileResponse,
  VisitorSupportMessage,
} from '@/types/ops'
import type { CurrentUserResponse, PageResponse, RegistrationResponse } from '@/types/api'

/** Links sent by e-mail (reset password, confirm a new e-mail address, admin invite) and the public Help form. */
export const publicOpsApi = {
  sendHelpMessage(payload: VisitorSupportMessage) {
    return apiPost<{ number: number; email: string }>('/public/support', payload)
  },
  checkResetLink(token: string) {
    return apiPost<LinkTokenResponse>('/auth/reset-password/check', { token })
  },
  confirmEmail(token: string) {
    return apiPost<LinkTokenResponse>('/auth/confirm-email', { token })
  },
  checkAdminInvite(token: string) {
    return apiPost<LinkTokenResponse>('/auth/admin-invite/check', { token })
  },
  acceptAdminInvite(payload: { token: string; fullName: string; password: string; confirmPassword: string }) {
    return apiPost<RegistrationResponse>('/auth/admin-invite/accept', payload)
  },
}

/** The Account page (every signed-in role). */
export const accountOpsApi = {
  settings() {
    return apiGet<AccountSettingsResponse>('/users/me/account')
  },
  /** Saves the name; the interface language goes along so the header switch's choice is remembered. */
  updateProfile(payload: { displayName: string; preferredLanguage: string }) {
    return apiPut<CurrentUserResponse>('/users/me/profile', payload)
  },
  updatePreferences(payload: Partial<NotificationPreferences>) {
    return apiPut<NotificationPreferences>('/users/me/notification-preferences', payload)
  },
  requestEmailChange(payload: { newEmail: string; currentPassword: string }) {
    return apiPost<EmailChangeResponse>('/users/me/email-change', payload)
  },
  deleteAccount(confirmEmail: string) {
    return apiPost<null>('/users/me/delete', { confirmEmail })
  },
}

/** Help & support for signed-in students and teachers: their own requests. */
export const supportApi = {
  list() {
    return apiGet<SupportRequestSummary[]>('/users/me/support')
  },
  get(id: number) {
    return apiGet<SupportRequestDetail>(`/users/me/support/${id}`)
  },
  create(request: NewSupportRequest) {
    const form = new FormData()
    form.append('topic', request.topic)
    form.append('subject', request.subject)
    form.append('message', request.message)
    if (request.screenshot) form.append('screenshot', request.screenshot)
    return apiPost<SupportRequestDetail>('/users/me/support', form)
  },
  reply(id: number, body: string) {
    return apiPost<SupportRequestDetail>(`/users/me/support/${id}/replies`, { body })
  },
  solve(id: number) {
    return apiPost<SupportRequestDetail>(`/users/me/support/${id}/solve`)
  },
  attachment(messageId: number) {
    return apiDownload(`/users/me/support/attachments/${messageId}`)
  },
}

/** The teacher's teaching profile and verification (open before approval). */
export const teacherVerificationApi = {
  profile() {
    return apiGet<TeacherProfileResponse>('/teachers/me')
  },
  updateProfile(payload: { fullName: string; bio: string }) {
    return apiPut<TeacherProfileResponse>('/teachers/me/profile', payload)
  },
  upload(documentType: TeacherDocumentType, file: File) {
    const form = new FormData()
    form.append('file', file)
    form.append('documentType', documentType)
    return apiPost<TeacherProfileResponse>('/teachers/verification-documents', form)
  },
  submit() {
    return apiPost<TeacherProfileResponse>('/teachers/verification/submit', { acceptedStandards: true })
  },
  document(id: number) {
    return apiDownload(`/teachers/verification-documents/${id}`)
  },
}

/** The admin Users section: users, admin invites and teacher applications. */
export const adminUsersApi = {
  list(query: AdminUserQuery = {}) {
    return apiGet<PageResponse<AdminUserListItem>>('/admin/users', { params: query })
  },
  get(id: number) {
    return apiGet<AdminUserDetail>(`/admin/users/${id}`)
  },
  deactivate(id: number, reason: string) {
    return apiPost<AdminDeactivationResult>(`/admin/users/${id}/deactivate`, { reason })
  },
  reactivate(id: number, reason: string) {
    return apiPost<AdminUserDetail>(`/admin/users/${id}/reactivate`, { reason })
  },
  invites() {
    return apiGet<AdminInvite[]>('/admin/admin-invites')
  },
  invite(email: string) {
    return apiPost<AdminInvite>('/admin/admin-invites', { email })
  },
  applications() {
    return apiGet<TeacherApplicationSummary[]>('/admin/teacher-applications')
  },
  application(teacherId: number) {
    return apiGet<TeacherApplication>(`/admin/teacher-applications/${teacherId}`)
  },
  approve(teacherId: number) {
    return apiPost<TeacherApplication>(`/admin/teachers/${teacherId}/approve`, {})
  },
  requestChanges(teacherId: number, reason: string) {
    return apiPost<TeacherApplication>(`/admin/teachers/${teacherId}/reject`, { reason })
  },
  document(documentId: number) {
    return apiDownload(`/admin/teacher-documents/${documentId}`)
  },
}

/** The admin Support queue. */
export const adminSupportApi = {
  queue(status: SupportStatus, page = 0, size = 20) {
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
  attachment(messageId: number) {
    return apiDownload(`/admin/support/attachments/${messageId}`)
  },
}

/** The admin dashboard, analytics and audit log. */
export const adminOverviewApi = {
  dashboard() {
    return apiGet<AdminDashboardResponse>('/admin/dashboard')
  },
  analytics(days: 7 | 30) {
    return apiGet<AdminAnalyticsResponse>('/admin/analytics', { params: { days } })
  },
  auditLog(query: AuditQuery = {}) {
    return apiGet<PageResponse<AuditEntry>>('/admin/audit-logs', { params: query })
  },
  auditEntry(id: number) {
    return apiGet<AuditEntry>(`/admin/audit-logs/${id}`)
  },
  auditActions() {
    return apiGet<string[]>('/admin/audit-logs/actions')
  },
  exportAuditLog(query: Omit<AuditQuery, 'page' | 'size'> = {}) {
    return apiDownload('/admin/audit-logs/export', { params: query })
  },
}
