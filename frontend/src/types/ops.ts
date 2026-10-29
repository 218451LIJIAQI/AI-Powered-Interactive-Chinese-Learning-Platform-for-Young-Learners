/**
 * Types of the ops module's API (public site forms, accounts, Help & support, teacher verification, admin users,
 * dashboard, analytics and audit log). Dates are ISO strings as the backend sends them.
 */
import type { ContentStatus, PageResponse, ReviewContentType, TeacherVerificationState, UserRole } from './api'

// ----- shared -----

/** A teacher's published class (teaching profile, account deletion). */
export interface PublishedClassSummary {
  id: number
  title: string
}

/** A reset or invite link that still works: the account address. */
export interface LinkTokenResponse {
  email: string
}

// ----- account -----

/** E-mail notification preferences; a preference that does not apply to the role is null. */
export interface NotificationPreferences {
  liveSessionReminders: boolean | null
  feedback: boolean | null
  contentReviews: boolean | null
  reviewQueues: boolean | null
}

export type AccountDeletionBlock = 'ADMIN_ACCOUNT' | 'PUBLISHED_CLASSES'

export interface AccountSettingsResponse {
  fullName: string
  email: string
  pendingEmail: string | null
  notifications: NotificationPreferences
  deletion: {
    blockedBy: AccountDeletionBlock | null
    publishedClasses: PublishedClassSummary[]
  }
}

export interface EmailChangeResponse {
  pendingEmail: string
}

// ----- teacher verification -----

export type TeacherDocumentType = 'CERTIFICATE' | 'IDENTITY' | 'QUALIFICATION'
/** READY: new since the last submission · IN_REVIEW · VERIFIED · SENT: already sent, unchanged. */
export type TeacherDocumentStatus = 'READY' | 'IN_REVIEW' | 'VERIFIED' | 'SENT'

export interface TeacherDocument {
  id: number
  documentType: TeacherDocumentType
  fileName: string
  fileSize: number
  uploadedAt: string
  status: TeacherDocumentStatus
}

export interface TeacherProfileResponse {
  teacherId: number
  fullName: string
  email: string
  bio: string | null
  verificationState: TeacherVerificationState
  submittedAt: string | null
  approvedAt: string | null
  latestDecision: {
    outcome: TeacherVerificationState
    reason: string | null
    reviewerName: string | null
    decidedAt: string
  } | null
  documents: TeacherDocument[]
  publishedClasses: PublishedClassSummary[]
}

// ----- Help & support -----

export type SupportStatus = 'OPEN' | 'ANSWERED' | 'SOLVED'
export type SupportTopic = 'ACCOUNT' | 'CLASSES' | 'LEARNING' | 'AI_HELPER' | 'VERIFICATION' | 'SAFETY' | 'OTHER'
export type SupportRequesterRole = 'STUDENT' | 'TEACHER' | 'VISITOR'
export type SupportClosedBy = 'REQUESTER' | 'SUPPORT' | 'AUTO'

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
  lastSupportReplyAt: string | null
  closesAt: string | null
  solvedAt: string | null
  closedBy: SupportClosedBy | null
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

export interface NewSupportRequest {
  topic: SupportTopic
  subject: string
  message: string
  screenshot?: File | null
}

export interface VisitorSupportMessage {
  email: string
  topic: SupportTopic
  message: string
}

// ----- admin users -----

export interface AdminUserListItem {
  id: number
  fullName: string
  email: string
  role: UserRole
  active: boolean
  verificationState: TeacherVerificationState | null
  joinedAt: string
}

export type AuditCategory = 'ACCOUNT' | 'CONTENT' | 'AI' | 'SUPPORT' | 'LEARNING' | 'SYSTEM'

export interface AuditEntry {
  id: number
  actionType: string
  category: AuditCategory
  actorName: string | null
  actorEmail: string | null
  targetType: string | null
  targetLabel: string | null
  /** The account the entry is about (the user itself or a teacher profile's account). */
  targetUserId: number | null
  reason: string | null
  description: string | null
  createdAt: string
}

export interface AdminUserDetail {
  id: number
  fullName: string
  email: string
  role: UserRole
  active: boolean
  verificationState: TeacherVerificationState | null
  approvedAt: string | null
  joinedAt: string
  deactivation: { actorName: string | null; actorEmail: string | null; reason: string | null; at: string } | null
  self: boolean
  deactivationImpact: {
    publishedClasses: string[]
    upcomingSessions: number
    bookings: number
    reviewItems: number
  } | null
  recentAudit: AuditEntry[]
}

export interface AdminDeactivationResult {
  user: AdminUserDetail
  cancelledSessions: number
  endedBookings: number
  itemsBackToDraft: number
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
  expiresAt: string
}

export interface TeacherApplicationSummary {
  teacherId: number
  userId: number
  fullName: string
  email: string
  submittedAt: string | null
  documentCount: number
}

export type TeacherApplicationEvent = 'ACCOUNT_CREATED' | 'APPLICATION_SUBMITTED' | 'APPROVED' | 'CHANGES_REQUESTED'

export interface TeacherApplication {
  teacherId: number
  userId: number
  fullName: string
  email: string
  bio: string | null
  verificationState: TeacherVerificationState
  submittedAt: string | null
  documents: Omit<TeacherDocument, 'status'>[]
  history: { event: TeacherApplicationEvent; actorName: string | null; at: string; note: string | null }[]
}

// ----- admin dashboard, analytics, audit -----

export interface DayCount {
  date: string
  count: number
}

export interface PlatformNumbers {
  users: { total: number; students: number; teachers: number; admins: number }
  publishedContent: number
  totalContent: number
  aiRequests: number
  successfulAiRequests: number
  auditEvents: number
}

export interface AdminQueue<T> {
  count: number
  items: T[]
}

export interface AdminDashboardResponse {
  teacherApplications: AdminQueue<TeacherApplicationSummary>
  reviewQueue: AdminQueue<{
    type: ReviewContentType
    id: number
    title: string
    ownerName: string | null
    parentTitle: string | null
    submittedAt: string | null
  }>
  aiReports: AdminQueue<{
    id: number
    reason: string
    featureKey: string
    studentName: string | null
    reportedAt: string
  }>
  support: AdminQueue<SupportRequestSummary>
  numbers: PlatformNumbers
  registrations: DayCount[]
}

export type AiRequestStatus = 'SUCCESS' | 'FAILED' | 'UNAVAILABLE'

export interface AdminAnalyticsResponse {
  days: number
  from: string
  to: string
  numbers: PlatformNumbers
  newRegistrations: number
  registrations: DayCount[]
  learning: {
    activeStudents: number
    totalStudents: number
    lessonsCompleted: number
    quizAverage: number | null
    needsSupport: number
  }
  contentByStatus: Record<ContentStatus, number>
  aiRequestsByStatus: Record<AiRequestStatus, number>
}

export interface AuditQuery {
  keyword?: string
  actionType?: string
  actor?: string
  /** Inclusive yyyy-MM-dd days. */
  from?: string
  to?: string
  page?: number
  size?: number
}
