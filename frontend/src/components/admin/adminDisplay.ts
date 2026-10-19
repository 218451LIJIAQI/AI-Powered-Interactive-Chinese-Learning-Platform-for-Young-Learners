/** Display helpers shared by the admin dashboard, analytics, settings and moderation pages. */
import type { AdminAnalyticsResponse, HealthStatusResponse } from '@/types/api'

import type { AdminTone } from './adminTone'

/** Shown while analytics are not loaded (live mode); the preview build uses the demo analytics instead. */
export const EMPTY_ADMIN_ANALYTICS: AdminAnalyticsResponse = {
  overview: {
    totalStudents: 0,
    totalTeachers: 0,
    pendingTeacherApprovals: 0,
    approvedTeachers: 0,
    rejectedTeachers: 0,
    publishedContentItems: 0,
    moderationActions: 0,
    auditLogEntries: 0,
    adminUnreadNotifications: 0,
    aiRequests: 0,
  },
  userRegistrationTrend: [],
  teacherApprovalBreakdown: [],
  contentStatusBreakdown: [],
  aiStatusBreakdown: [],
}

export const EMPTY_HEALTH: HealthStatusResponse = { status: 'UNKNOWN', service: 'backend', timestamp: '' }

/** Preview-build health fixture, stamped when the page is opened. */
export function previewHealth(): HealthStatusResponse {
  return { status: 'UP', service: 'backend', timestamp: new Date().toISOString() }
}

export function auditIcon(actionType: string): string {
  if (actionType.includes('APPROVE')) return 'status'
  if (actionType.includes('REJECT')) return 'close'
  if (actionType.includes('CREATE') || actionType.includes('UPDATE')) return 'content'
  return 'history'
}

export function auditTone(actionType: string): AdminTone {
  if (actionType.includes('APPROVE')) return 'success'
  if (actionType.includes('REJECT')) return 'danger'
  if (actionType.includes('CREATE') || actionType.includes('UPDATE')) return 'primary'
  return 'info'
}

export function aiReportStatusTone(status: string): 'info' | 'primary' | 'success' | 'warning' {
  switch (status) {
    case 'PENDING_REVIEW':
      return 'warning'
    case 'IN_REVIEW':
      return 'info'
    case 'RESOLVED':
      return 'success'
    default:
      return 'primary'
  }
}
