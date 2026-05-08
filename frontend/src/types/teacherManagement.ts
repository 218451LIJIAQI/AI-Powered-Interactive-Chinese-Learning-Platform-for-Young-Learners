export type TeacherStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export type TeacherVerificationDocumentSummary = {
  id: number
  documentType: string
  description: string | null
  originalFileName: string
  contentType: string
  fileSize: number
  submittedAt: string | null
  currentVersion: boolean
}

export type TeacherVerificationReviewSummary = {
  id: number
  decisionStatus: TeacherStatus
  note: string | null
  reviewerName: string | null
  reviewedAt: string | null
}

export type TeacherProfileResponse = {
  userId: number
  fullName: string
  email: string
  teacherProfileId: number
  bio: string | null
  shortIntroduction: string | null
  teachingCategories: string[]
  teacherStatus: TeacherStatus
  rejectionReason: string | null
  verificationDocuments: TeacherVerificationDocumentSummary[]
  createdAt: string
  updatedAt: string
}

export type TeacherVerificationStatusResponse = {
  teacherStatus: TeacherStatus
  latestSubmittedDocuments: TeacherVerificationDocumentSummary[]
  latestReviewResult: TeacherVerificationReviewSummary | null
  rejectionReason: string | null
  resubmissionAllowed: boolean
}

export type UpdateTeacherProfilePayload = {
  fullName: string
  bio?: string
  shortIntroduction?: string
  teachingCategories?: string[]
}

export type TeacherProfilePublicResponse = {
  teacherId: number
  displayName: string
  bio: string | null
  verified: boolean
  teachingCategories: string[]
  courseCount: number
  ratingAverage: number
  reviewCount: number
}
