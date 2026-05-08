import type { TeacherStatus, TeacherVerificationDocumentSummary, TeacherVerificationReviewSummary } from './teacherManagement'

export type PageResponse<T> = {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type AdminTeacherListItem = {
  teacherId: number
  userId: number
  fullName: string
  email: string
  teacherStatus: TeacherStatus
  bioSummary: string | null
  verificationDocumentCount: number
  latestReviewDate: string | null
  latestSubmissionTime: string | null
  createdAt: string
}

export type AdminAuditSummary = {
  auditLogId: number
  actionType: string
  targetType: string | null
  description: string | null
  createdAt: string
}

export type AdminTeacherDetail = {
  teacherId: number
  userId: number
  fullName: string
  email: string
  bio: string | null
  shortIntroduction: string | null
  teachingCategories: string[]
  teacherStatus: TeacherStatus
  rejectionReason: string | null
  verifiedAt: string | null
  verificationDocuments: TeacherVerificationDocumentSummary[]
  verificationReviewHistory: TeacherVerificationReviewSummary[]
  recentAuditLogs: AdminAuditSummary[]
  createdAt: string
  updatedAt: string
}

export type AdminUserListItem = {
  userId: number
  fullName: string
  email: string
  roles: string[]
  teacherStatus: TeacherStatus | null
  createdAt: string
  updatedAt: string
}

export type AdminStudentListItem = {
  studentId: number
  userId: number
  fullName: string
  email: string
  preferredLanguage: string | null
  createdAt: string
}
