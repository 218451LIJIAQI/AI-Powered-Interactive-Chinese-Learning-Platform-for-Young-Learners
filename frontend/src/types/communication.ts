export type NotificationType =
  | 'SYSTEM'
  | 'TEACHER_FEEDBACK'
  | 'QUIZ_RESULT'
  | 'PROGRESS_UPDATE'
  | 'REWARD_EARNED'
  | 'PAYMENT_STATUS'
  | 'REFUND_STATUS'
  | 'PAYOUT_STATUS'
  | 'LIVE_CLASS_REMINDER'
  | 'TEACHER_APPROVAL'
  | 'CONTENT_REVIEW'
  | 'REPORT_CREATED'
  | 'REVIEW_RECEIVED'
  | 'SUPPORT_TICKET'
  | 'MODERATION_ACTION'
  | 'ADMIN_TASK'

export type NotificationStatus = 'UNREAD' | 'READ'
export type ReportStatus = 'PENDING' | 'REVIEWED' | 'DISMISSED' | 'ACTION_TAKEN'
export type ReviewStatus = 'VISIBLE' | 'HIDDEN' | 'FLAGGED'
export type SupportTicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type SupportTicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type ReportTargetType = 'LESSON' | 'PACKAGE' | 'REVIEW' | 'TEACHER' | 'TECHNICAL' | 'OTHER'
export type ReviewTargetType = 'LESSON' | 'PACKAGE'
export type ModerationTargetType = 'LESSON' | 'PACKAGE' | 'REVIEW' | 'TEACHER_PROFILE' | 'OTHER'
export type ModerationAction = 'HIDE' | 'UNPUBLISH' | 'ARCHIVE' | 'REQUEST_REVISION' | 'RESTORE'
export type TeacherFeedbackType = 'GENERAL' | 'QUIZ' | 'LESSON' | 'PROGRESS' | 'WEAK_AREA'

export type NotificationItem = {
  id: number
  title: string
  message: string
  type: NotificationType
  status: NotificationStatus
  targetType: string | null
  targetId: number | null
  targetPath: string | null
  createdAt: string
  readAt: string | null
}

export type NotificationUnreadCount = {
  unreadCount: number
}

export type BulkOperationResult = {
  updatedCount: number
}

export type ReportItem = {
  id: number
  targetType: string
  targetId: number | null
  targetTitle: string | null
  reason: string
  reportStatus: ReportStatus
  reporterName: string | null
  reporterEmail: string | null
  relatedTeacherId: number | null
  relatedTeacherName: string | null
  teacherRevisionRequested: boolean
  adminNote: string | null
  createdAt: string
  updatedAt: string
}

export type ReviewItem = {
  id: number
  targetType: string
  targetId: number
  targetTitle: string | null
  rating: number
  comment: string | null
  reviewerName: string | null
  reviewStatus: ReviewStatus
  visible: boolean
  createdAt: string
  updatedAt: string
}

export type TeacherFeedbackItem = {
  id: number
  teacherId: number | null
  teacherName: string | null
  studentId: number | null
  studentName: string | null
  lessonId: number | null
  lessonTitle: string | null
  quizAttemptId: number | null
  quizTitle: string | null
  feedbackType: string | null
  message: string
  createdAt: string
  updatedAt: string
}

export type FeedbackCandidate = {
  studentId: number
  studentName: string
  studentEmail: string | null
  lessonId: number | null
  lessonTitle: string | null
  quizAttemptId: number | null
  quizTitle: string | null
}

export type SupportTicketItem = {
  id: number
  requesterUserId: number | null
  requesterName: string | null
  requesterEmail: string | null
  requesterRole: string | null
  subject: string
  description: string
  status: SupportTicketStatus
  priority: SupportTicketPriority
  createdAt: string
  updatedAt: string
}

export type SupportTicketMessage = {
  id: number
  senderUserId: number | null
  senderName: string | null
  senderRole: string | null
  message: string
  createdAt: string
}

export type SupportTicketDetail = {
  ticket: SupportTicketItem
  messages: SupportTicketMessage[]
}

export type ModerationRecordItem = {
  id: number
  targetType: string
  targetId: number
  targetTitle: string | null
  actionTaken: string
  note: string | null
  adminUserId: number | null
  adminName: string | null
  relatedTeacherId: number | null
  relatedTeacherName: string | null
  createdAt: string
}

export type AuditLogItem = {
  id: number
  actorUserId: number | null
  actorEmail: string | null
  actionType: string
  targetType: string | null
  targetId: number | null
  description: string | null
  ipAddress: string | null
  createdAt: string
}

export type CreateReportPayload = {
  targetType: ReportTargetType
  targetId?: number | null
  reason: string
}

export type CreateReviewPayload = {
  targetType: ReviewTargetType
  targetId: number
  rating: number
  comment?: string
}

export type UpdateReviewPayload = {
  rating: number
  comment?: string
}

export type CreateSupportTicketPayload = {
  subject: string
  message: string
}

export type SupportTicketMessagePayload = {
  message: string
}

export type CreateTeacherFeedbackPayload = {
  studentId: number
  lessonId?: number | null
  quizAttemptId?: number | null
  feedbackType?: TeacherFeedbackType | ''
  message: string
}

export type UpdateTeacherFeedbackPayload = {
  feedbackType?: TeacherFeedbackType | ''
  message: string
}

export type ModerationActionPayload = {
  targetType: ModerationTargetType
  targetId: number
  reason: string
}

export type AdminReportActionPayload = {
  note?: string
}

export type UpdateSupportTicketStatusPayload = {
  status: SupportTicketStatus
}

export type UpdateSupportTicketPriorityPayload = {
  priority: SupportTicketPriority
}
