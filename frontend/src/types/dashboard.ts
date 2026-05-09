export type StudentDashboardSummary = {
  fullName: string
  currentLevel: string
  learningProgressPercent: number
  dailyStreak: number
  points: number
  badgeCount: number
  recentQuizResults: {
    attemptId: number
    quizTitle: string
    score: number | null
    maxScore: number | null
    completedAt: string | null
  }[]
  weakAreas: string[]
  unfinishedLessons: {
    lessonId: number
    lessonTitle: string
    completionPercent: number | null
    lastAccessedAt: string | null
  }[]
  recommendedLessons: {
    lessonId: number
    title: string
    category: string | null
    level: string | null
  }[]
  savedItems: {
    savedItemId: number
    itemType: string
    itemId: number
    note: string | null
    savedAt: string
  }[]
  recentNotifications: {
    notificationId: number
    title: string
    message: string
    status: string
    targetPath: string | null
    createdAt: string
  }[]
  recommendedNextStep: string
}

export type TeacherDashboardSummary = {
  fullName: string
  teacherStatus: 'PENDING' | 'APPROVED' | 'REJECTED'
  totalLessons: number
  totalStudents: number
  averageQuizScore: number
  ratingAverage: number
  earningsSummary: {
    grossAmount: number
    netAmount: number
    availableBalance: number
    pendingBalance: number
  }
  activeStudents: {
    studentId: number
    fullName: string
    currentLevel: string | null
  }[]
  recentQuizResults: {
    attemptId: number
    quizTitle: string
    studentName: string
    score: number | null
    maxScore: number | null
    completedAt: string | null
  }[]
  recentReviews: {
    reviewId: number
    reviewerName: string
    rating: number
    comment: string | null
    createdAt: string
  }[]
  recentNotifications: {
    notificationId: number
    title: string
    message: string
    status: string
    targetPath: string | null
    createdAt: string
  }[]
}

export type AdminDashboardSummary = {
  fullName: string
  totalStudents: number
  totalTeachers: number
  pendingTeacherApprovals: number
  totalModules: number
  totalLessons: number
  totalQuizzes: number
  totalGames: number
  recentUserActivity: {
    userId: number
    fullName: string
    email: string
    roles: string[]
    createdAt: string
  }[]
  aiUsageSummary: {
    totalRequests: number
    successfulRequests: number
  }
  paymentRevenueSummary: {
    totalTransactions: number
    paidTransactions: number
    totalRevenue: number
  }
  pendingRefunds: number
  pendingPayouts: number
  recentReports: {
    reportId: number
    targetType: string
    status: string
    createdAt: string
  }[]
  recentAuditLogs: {
    auditLogId: number
    actionType: string
    targetType: string | null
    description: string | null
    createdAt: string
  }[]
}
