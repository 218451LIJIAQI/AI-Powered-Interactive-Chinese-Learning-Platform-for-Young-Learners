export type LiveClassStatus = 'SCHEDULED' | 'ONGOING' | 'COMPLETED' | 'CANCELLED' | 'RESCHEDULED'
export type LiveClassEnrolmentStatus = 'ENROLLED' | 'CANCELLED' | 'ATTENDED' | 'MISSED'
export type MeetingPlatform = 'ZOOM' | 'GOOGLE_MEET' | 'MICROSOFT_TEAMS' | 'OTHER'
export type RecordedVideoSourceType = 'UPLOADED_FILE' | 'EXTERNAL_URL'

export type LiveClassTeacherSummary = {
  teacherId: number
  userId: number
  displayName: string
}

export type LiveClassLessonSummary = {
  lessonId: number
  lessonTitle: string
  learningType: string | null
  accessType: string | null
}

export type LiveClassPackageSummary = {
  packageId: number
  packageTitle: string
  learningType: string | null
  accessType: string | null
}

export type LiveClass = {
  id: number
  title: string
  description: string | null
  meetingPlatform: MeetingPlatform | null
  liveClassStatus: LiveClassStatus | null
  startTime: string | null
  endTime: string | null
  durationMinutes: number | null
  maxParticipants: number | null
  enrolledCount: number
  enrolled: boolean
  canEnrol: boolean
  accessAllowed: boolean
  canJoin: boolean
  meetingLinkVisible: boolean
  meetingLink?: string | null
  teacher: LiveClassTeacherSummary | null
  lesson: LiveClassLessonSummary | null
  coursePackage: LiveClassPackageSummary | null
  createdAt: string
  updatedAt: string
}

export type LiveClassEnrolment = {
  id: number
  liveClassId: number
  studentId: number
  studentName: string
  studentEmail: string | null
  enrolmentStatus: LiveClassEnrolmentStatus | null
  enrolledAt: string | null
  cancelledAt: string | null
  attendedAt: string | null
}

export type LiveClassFormPayload = {
  title: string
  description?: string
  lessonId?: number | null
  packageId?: number | null
  startTime: string
  endTime: string
  meetingLink: string
  meetingPlatform: MeetingPlatform
  status?: LiveClassStatus
  maxParticipants?: number | null
}

export type RescheduleLiveClassPayload = {
  startTime: string
  endTime: string
}

export type JoinLiveClassResult = {
  liveClassId: number
  title: string
  meetingPlatform: MeetingPlatform | null
  meetingLink: string
}

export type RecordedLessonVideo = {
  id: number
  lessonId: number
  packageId: number | null
  title: string
  description: string | null
  sourceType: RecordedVideoSourceType
  externalVideoUrl: string | null
  streamUrl: string | null
  durationSeconds: number | null
  sortOrder: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export type RecordedLessonVideoPayload = {
  title: string
  description?: string
  packageId?: number | null
  sourceType: RecordedVideoSourceType
  externalVideoUrl?: string
  durationSeconds?: number | null
  sortOrder: number
}

export type VideoProgress = {
  id: number
  lessonId: number
  recordedVideoId: number
  watchedSeconds: number
  totalSeconds: number
  progressPercentage: number
  completed: boolean
  lastWatchedAt: string | null
  updatedAt: string
}

export type VideoProgressPayload = {
  watchedSeconds?: number
  totalSeconds?: number
  progressPercentage?: number
  completed?: boolean
}

export type LessonVideoProgressSummary = {
  studentId: number
  studentName: string
  studentEmail: string | null
  recordedVideoId: number | null
  recordedVideoTitle: string | null
  progressPercentage: number | null
  completed: boolean
  watchedSeconds: number | null
  totalSeconds: number | null
  lastWatchedAt: string | null
}

export type ScheduleItem = {
  itemType: string
  itemId: number
  title: string
  description: string | null
  status: string | null
  startTime: string | null
  endTime: string | null
  secondaryLabel: string | null
  routePath: string | null
  actionable: boolean
}

export type StudentSchedule = {
  upcomingLiveClasses: ScheduleItem[]
  learningTasks: ScheduleItem[]
  continueWatching: ScheduleItem[]
}

export type TeacherSchedule = {
  upcomingTeachingSessions: ScheduleItem[]
  recordedLessonUpdates: ScheduleItem[]
}

export type AdminSchedule = {
  liveClasses: ScheduleItem[]
}
