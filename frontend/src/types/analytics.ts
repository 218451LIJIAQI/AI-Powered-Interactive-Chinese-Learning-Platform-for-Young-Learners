export type AnalyticsTrendPoint = {
  label: string
  value: number
}

export type AnalyticsBreakdownItem = {
  key: string
  value: number
}

export type StudentAnalyticsResponse = {
  overview: {
    completedLessons: number
    inProgressLessons: number
    totalTrackedLessons: number
    averageQuizScore: number
    completedVideos: number
    upcomingLiveClasses: number
    unreadNotifications: number
  }
  quizScoreTrend: AnalyticsTrendPoint[]
  activityTrend: AnalyticsTrendPoint[]
  lessonStatusBreakdown: AnalyticsBreakdownItem[]
  weakAreaBreakdown: AnalyticsBreakdownItem[]
  learningModeBreakdown: AnalyticsBreakdownItem[]
}

export type TeacherAnalyticsResponse = {
  overview: {
    totalModules: number
    totalLessons: number
    totalPackages: number
    publishedContentItems: number
    submittedContentItems: number
    totalActiveStudents: number
    averageQuizScore: number
    averageRating: number
    netEarnings: number
    availableBalance: number
    upcomingLiveClasses: number
    totalLiveEnrolments: number
    aiDraftRequests: number
    successfulAiDraftRequests: number
  }
  quizScoreTrend: AnalyticsTrendPoint[]
  earningsTrend: AnalyticsTrendPoint[]
  contentStatusBreakdown: AnalyticsBreakdownItem[]
  reviewRatingBreakdown: AnalyticsBreakdownItem[]
}

export type AdminAnalyticsResponse = {
  overview: {
    totalStudents: number
    totalTeachers: number
    pendingTeacherApprovals: number
    approvedTeachers: number
    rejectedTeachers: number
    publishedContentItems: number
    totalRevenue: number
    pendingRefunds: number
    pendingPayouts: number
    openSupportTickets: number
    pendingReports: number
    moderationActions: number
    auditLogEntries: number
    adminUnreadNotifications: number
    aiRequests: number
  }
  userRegistrationTrend: AnalyticsTrendPoint[]
  teacherApprovalBreakdown: AnalyticsBreakdownItem[]
  contentStatusBreakdown: AnalyticsBreakdownItem[]
  paymentStatusBreakdown: AnalyticsBreakdownItem[]
  reportStatusBreakdown: AnalyticsBreakdownItem[]
  reviewStatusBreakdown: AnalyticsBreakdownItem[]
  supportTicketStatusBreakdown: AnalyticsBreakdownItem[]
  aiStatusBreakdown: AnalyticsBreakdownItem[]
}
