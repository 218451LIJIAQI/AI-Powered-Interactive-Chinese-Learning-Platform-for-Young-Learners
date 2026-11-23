/** Account, notification, health, shell and reward clients; transport primitives live in `api/request.ts`. */
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
  RewardSummaryResponse,
} from '@/types/api'
import type { LinkTokenResponse } from '@/types/ops'

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
  /**
   * This browser's session (Account › This device): `signedInAt`, the local date-time it signed in, or null when the
   * request's token names no session.
   */
  session() {
    return apiGet<{ signedInAt: string | null }>('/users/me/session')
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
}

export const publicApi = {
  platformSettings() {
    return apiGet<PublicPlatformSettingsResponse>('/public/platform-settings')
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

export const studentApi = {
  rewards() {
    return apiGet<RewardSummaryResponse>('/students/rewards')
  },
}

export const shellApi = {
  navCounts() {
    return apiGet<NavCounts>('/users/me/nav-counts')
  },
}
