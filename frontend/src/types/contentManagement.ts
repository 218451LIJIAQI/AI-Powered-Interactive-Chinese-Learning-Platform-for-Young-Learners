export type ContentStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'PUBLISHED' | 'ARCHIVED'
export type AccessType = 'FREE' | 'PAID'
export type LearningType = 'LIVE' | 'RECORDED' | 'HYBRID'
export type ManagedContentType = 'MODULE' | 'LESSON' | 'PACKAGE'
export type LessonActivityType =
  | 'FLASHCARD'
  | 'QUIZ'
  | 'MATCHING'
  | 'LISTENING'
  | 'TRACING'
  | 'DRAG_DROP'
  | 'STORY'
  | 'AI_HELP'
  | 'TEXT_CONTENT'
  | 'VIDEO_PLACEHOLDER'
export type PackageItemType = 'MODULE' | 'LESSON' | 'ACTIVITY'

export type PageResponse<T> = {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type LearningLevel = {
  id: number
  name: string
  description: string | null
}

export type LearningCategory = {
  id: number
  name: string
  description: string | null
}

export type TeacherOwnerSummary = {
  teacherId: number
  userId: number
  fullName: string
  teacherStatus: 'PENDING' | 'APPROVED' | 'REJECTED'
}

export type LearningModuleItem = {
  id: number
  title: string
  description: string | null
  level: LearningLevel | null
  category: LearningCategory | null
  accessType: AccessType
  price: number | null
  coverImageUrl: string | null
  contentStatus: ContentStatus
  rejectionReason: string | null
  teacher: TeacherOwnerSummary | null
  createdAt: string
  updatedAt: string
}

export type LessonActivityItem = {
  id: number
  lessonId: number
  title: string
  activityType: LessonActivityType
  instruction: string | null
  description: string | null
  displayOrder: number
  placeholderContent: string | null
  contentStatus: ContentStatus
  createdAt: string
  updatedAt: string
}

export type LessonItem = {
  id: number
  title: string
  description: string | null
  basicContent: string | null
  moduleId: number | null
  moduleTitle: string | null
  level: LearningLevel | null
  category: LearningCategory | null
  learningType: LearningType
  accessType: AccessType
  price: number | null
  estimatedDurationMinutes: number | null
  coverImageUrl: string | null
  contentStatus: ContentStatus
  rejectionReason: string | null
  teacher: TeacherOwnerSummary | null
  activities: LessonActivityItem[]
  createdAt: string
  updatedAt: string
}

export type PackageItem = {
  id: number
  packageId: number
  itemType: PackageItemType
  itemId: number
  displayOrder: number
  titleSnapshot: string | null
}

export type CoursePackageItem = {
  id: number
  title: string
  description: string | null
  level: LearningLevel | null
  category: LearningCategory | null
  learningType: LearningType
  accessType: AccessType
  price: number | null
  coverImageUrl: string | null
  contentStatus: ContentStatus
  rejectionReason: string | null
  teacher: TeacherOwnerSummary | null
  packageItems: PackageItem[]
  createdAt: string
  updatedAt: string
}

export type ContentSubmissionItem = {
  contentType: ManagedContentType
  id: number
  title: string
  description: string | null
  teacher: TeacherOwnerSummary | null
  level: LearningLevel | null
  category: LearningCategory | null
  learningType: LearningType | null
  accessType: AccessType
  price: number | null
  contentStatus: ContentStatus
  rejectionReason: string | null
  createdAt: string
  updatedAt: string
}

export type ContentSubmissionDetail = {
  contentType: ManagedContentType
  id: number
  title: string
  description: string | null
  basicContent: string | null
  moduleId: number | null
  moduleTitle: string | null
  teacher: TeacherOwnerSummary | null
  level: LearningLevel | null
  category: LearningCategory | null
  learningType: LearningType | null
  accessType: AccessType
  price: number | null
  coverImageUrl: string | null
  contentStatus: ContentStatus
  rejectionReason: string | null
  activities: LessonActivityItem[]
  packageItems: PackageItem[]
  createdAt: string
  updatedAt: string
}

export type PublicTeacherSummary = {
  teacherId: number
  displayName: string
  verified: boolean
}

export type PublicCourseSummary = {
  id: string
  contentId: number
  type: ManagedContentType
  title: string
  shortDescription: string | null
  teacher: PublicTeacherSummary
  level: LearningLevel | null
  category: LearningCategory | null
  learningType: LearningType | null
  accessType: AccessType
  price: number | null
  rating: number
  coverImageUrl: string | null
  locked: boolean
  status: ContentStatus
}

export type PublicCourseItemSummary = {
  type: ManagedContentType
  contentId: number
  title: string | null
  displayOrder: number
}

export type PublicActivitySummary = {
  id: number
  title: string
  activityType: LessonActivityType
  instruction: string | null
  displayOrder: number
}

export type PublicCourseDetail = {
  id: string
  contentId: number
  type: ManagedContentType
  title: string
  description: string | null
  teacher: PublicTeacherSummary
  level: LearningLevel | null
  category: LearningCategory | null
  learningType: LearningType | null
  accessType: AccessType
  price: number | null
  coverImageUrl: string | null
  locked: boolean
  status: ContentStatus
  basicContent: string | null
  packageItems: PublicCourseItemSummary[]
  activities: PublicActivitySummary[]
  callToAction: string
  lockedMessage: string | null
}

export type ModuleFormPayload = {
  title: string
  description?: string
  levelId: number
  categoryId: number
  accessType: AccessType
  price?: number
  coverImageUrl?: string
}

export type LessonFormPayload = {
  title: string
  description?: string
  moduleId?: number | null
  levelId: number
  categoryId: number
  learningType: LearningType
  accessType: AccessType
  price?: number
  estimatedDurationMinutes?: number | null
  basicContent?: string
  coverImageUrl?: string
}

export type LessonActivityFormPayload = {
  title: string
  activityType: LessonActivityType
  instruction?: string
  description?: string
  displayOrder?: number
  placeholderContent?: string
}

export type CoursePackageFormPayload = {
  title: string
  description?: string
  levelId: number
  categoryId: number
  learningType: LearningType
  accessType: AccessType
  price?: number
  coverImageUrl?: string
}

export type PackageItemFormPayload = {
  itemType: PackageItemType
  itemId: number
  displayOrder?: number
}
