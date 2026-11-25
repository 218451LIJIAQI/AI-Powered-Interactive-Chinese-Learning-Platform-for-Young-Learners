/**
 * Types of the ops module's API (public site forms, accounts, support requests, admin users, dashboard, analytics and
 * audit log). Dates are ISO strings as the backend sends them.
 */
import type { AiResponseReportReason, AiResultStatus, AppLanguage, ContentStatus, PageResponse, UserRole } from './api'

/**
 * An account's address: the one a reset, confirmation or invite link belongs to while the link can still be used, or
 * the one an account was just created with (sign-up, an accepted admin invite).
 */
export interface LinkTokenResponse {
  email: string
}

// Account

/**
 * The e-mail notifications a user can switch off: an admin's daily summary of AI reports and support requests waiting
 * (`reviewQueues`). Learners have none to switch, so it is null for them.
 */
export interface NotificationPreferences {
  reviewQueues: boolean | null
}

export interface AccountSettingsResponse {
  fullName: string
  email: string
  pendingEmail: string | null
  notifications: NotificationPreferences
}

export interface EmailChangeResponse {
  pendingEmail: string
}

// Support requests

export const SUPPORT_STATUSES = ['OPEN', 'ANSWERED', 'SOLVED'] as const
export type SupportStatus = (typeof SUPPORT_STATUSES)[number]
/** VERIFICATION only labels requests sent while teachers still applied to the platform; the forms no longer offer it. */
export type SupportTopic = 'ACCOUNT' | 'CLASSES' | 'LEARNING' | 'AI_HELPER' | 'VERIFICATION' | 'SAFETY' | 'OTHER'
export type SelectableSupportTopic = Exclude<SupportTopic, 'VERIFICATION'>
/** A signed-in learner or a visitor of the Help page; TEACHER marks requests teacher accounts sent before 2026-11. */
export type SupportRequesterRole = 'STUDENT' | 'TEACHER' | 'VISITOR'

export interface SupportRequestSummary {
  id: number
  number: number
  topic: SupportTopic
  subject: string
  status: SupportStatus
  requesterName: string | null
  requesterEmail: string
  requesterRole: SupportRequesterRole
  createdAt: string
  updatedAt: string
  closesAt: string | null
  solvedAt: string | null
  closedBy: 'REQUESTER' | 'SUPPORT' | 'AUTO' | null
}

export interface SupportMessage {
  id: number
  fromSupport: boolean
  authorName: string | null
  body: string
  createdAt: string
  attachment: { fileName: string; fileSize: number } | null
}

export interface SupportRequestDetail {
  request: SupportRequestSummary
  requesterUserId: number | null
  messages: SupportMessage[]
}

export interface SupportQueueResponse {
  counts: Record<SupportStatus, number>
  requests: PageResponse<SupportRequestSummary>
}

/** "Contact support" of a signed-in learner (S11.03, X2.21). */
export interface NewSupportRequest {
  topic: SelectableSupportTopic
  subject: string
  message: string
  screenshot?: File | null
}

/** The address support's e-mail reply goes to after a request is created. */
export interface SupportRequestCreated {
  email: string
}

export interface VisitorSupportMessage {
  email: string
  topic: SelectableSupportTopic
  message: string
  /** The page's interface language: the e-mails that bring support's replies use it. */
  language: AppLanguage
}

// Admin users

export interface AdminUserListItem {
  id: number
  fullName: string
  email: string
  role: UserRole
  active: boolean
  joinedAt: string
}

export type AuditCategory = 'ACCOUNT' | 'CONTENT' | 'AI' | 'SUPPORT' | 'LEARNING' | 'SYSTEM'

export interface AuditEntry {
  id: number
  actionType: string
  category: AuditCategory
  actorName: string | null
  actorEmail: string | null
  targetLabel: string | null
  /** The account the entry is about. */
  targetUserId: number | null
  reason: string | null
  description: string | null
  createdAt: string
}

export interface AdminUserDetail extends Omit<AdminUserListItem, 'role'> {
  /** Null for an account whose former role was removed. */
  role: UserRole | null
  /** True once the learner permanently deleted their account; deleted accounts cannot be reactivated or deactivated. */
  deleted?: boolean
  deactivation: { actorName: string | null; reason: string | null; at: string } | null
  self: boolean
  /** The account's own recent events ("Account created", "Account deactivated" …), newest first. */
  recentAudit: AuditEntry[]
}

export interface AdminUserQuery {
  keyword?: string
  role?: UserRole
  active?: boolean
  page?: number
  size?: number
}

export interface AdminInvite {
  id: number
  email: string
  sentAt: string
}

// Admin dashboard, analytics and audit log

interface AdminQueue<T> {
  count: number
  items: T[]
}

/** A draft class on the dashboard's Class drafts card (A1.01), newest first; archived lessons are not counted. */
interface ClassDraftItem {
  id: number
  title: string
  lessonCount: number
  /** The last change to the class or any of its lessons ("edited 18 May"). */
  updatedAt: string
}

export interface AdminDashboardResponse {
  classDrafts: AdminQueue<ClassDraftItem>
  aiReports: AdminQueue<{
    id: number
    reason: AiResponseReportReason
    studentName: string
    reportedAt: string
  }>
  support: AdminQueue<SupportRequestSummary>
}

export interface AdminAnalyticsResponse {
  days: 7 | 30
  from: string
  to: string
  numbers: {
    users: { total: number; students: number; admins: number }
    publishedContent: number
    aiRequests: number
  }
  newRegistrations: number
  registrations: { date: string; count: number }[]
  learning: {
    activeStudents: number
    totalStudents: number
    lessonsCompleted: number
    quizAverage: number | null
    needsSupport: number
  }
  contentByStatus: Record<ContentStatus, number>
  aiRequestsByStatus: Record<AiResultStatus, number>
}

export interface AuditQuery {
  keyword?: string
  actionType?: string
  actor?: string
  /** Inclusive yyyy-MM-dd days. */
  from?: string
  to?: string
  /** Only the entries by or about this account. */
  userId?: number
  page?: number
  size?: number
}
