/** Auth, public, notification, health and shell API clients; transport primitives live in `api/request.ts`. */
import type {
  AppLanguage,
  AuthResponse,
  CurrentUserResponse,
  HealthStatusResponse,
  NavCounts,
  NotificationResponse,
  NotificationStatus,
  NotificationUnreadCountResponse,
  PageResponse,
  PublicPlatformSettingsResponse,
} from '@/types/api'
import type { LinkTokenResponse, SupportRequestCreated, VisitorSupportMessage } from '@/types/ops'

import { apiGet, apiPatch, apiPost } from './request'

export const authApi = {
  /** `keepSignedIn` false: the refresh cookie ends with the browser session ("Keep me signed in" unticked). */
  login(payload: { email: string; password: string; keepSignedIn?: boolean }) {
    return apiPost<AuthResponse>('/auth/login', payload)
  },
  registerStudent(payload: {
    fullName: string
    email: string
    password: string
    confirmPassword: string
    preferredLanguage?: AppLanguage
  }) {
    return apiPost<LinkTokenResponse>('/auth/register/student', payload)
  },
  me() {
    return apiGet<CurrentUserResponse>('/auth/me')
  },
  /** Signs out this browser only; the account's other devices stay signed in. */
  logout() {
    return apiPost<void>('/auth/logout')
  },
  forgotPassword(email: string) {
    return apiPost<void>('/auth/forgot-password', { email })
  },
  resetPassword(payload: { token: string; password: string; confirmPassword: string }) {
    return apiPost<void>('/auth/reset-password', payload)
  },
  checkResetLink(token: string) {
    return apiPost<LinkTokenResponse>('/auth/reset-password/check', { token })
  },
  confirmEmail(token: string) {
    return apiPost<LinkTokenResponse>('/auth/confirm-email', { token })
  },
  /** The link from the sign-up e-mail. */
  confirmSignupEmail(token: string) {
    return apiPost<LinkTokenResponse>('/auth/confirm-signup-email', { token })
  },
  checkAdminInvite(token: string) {
    return apiPost<LinkTokenResponse>('/auth/admin-invite/check', { token })
  },
  /** Creates the admin account; `preferredLanguage` (the page's language) becomes its language, as at sign-up. */
  acceptAdminInvite(payload: {
    token: string
    fullName: string
    password: string
    confirmPassword: string
    preferredLanguage: AppLanguage
  }) {
    return apiPost<LinkTokenResponse>('/auth/admin-invite/accept', payload)
  },
}

export const publicApi = {
  platformSettings() {
    return apiGet<PublicPlatformSettingsResponse>('/public/platform-settings')
  },
  sendHelpMessage(payload: VisitorSupportMessage) {
    return apiPost<SupportRequestCreated>('/public/support', payload)
  },
}

export const notificationsApi = {
  /** Paged list for the notifications page; `status` filters UNREAD / READ (omit for all). */
  page(query: { status?: NotificationStatus; page: number; size: number }) {
    return apiGet<PageResponse<NotificationResponse>>('/notifications', { params: query })
  },
  unreadCount() {
    return apiGet<NotificationUnreadCountResponse>('/notifications/unread-count')
  },
  markAsRead(id: number) {
    return apiPatch<NotificationResponse>(`/notifications/${id}/read`)
  },
  /** Marks every unread notification read; answers with the ids it marked, which an Undo hands back to `markUnread`. */
  markAllRead() {
    return apiPatch<number[]>('/notifications/read-all')
  },
  /** Returns the ids changed back to unread by this request. */
  markUnread(ids: readonly number[]) {
    return apiPatch<number[]>('/notifications/unread', { ids })
  },
}

export const systemApi = {
  health() {
    return apiGet<HealthStatusResponse>('/health')
  },
}

export const shellApi = {
  navCounts() {
    return apiGet<NavCounts>('/users/me/nav-counts')
  },
}
