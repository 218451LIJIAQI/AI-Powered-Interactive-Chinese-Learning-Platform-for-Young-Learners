/**
 * Full i18n keys (bundle `opsLabelMessages`, namespace `opsLabels`) and status tones for the values the ops API
 * sends: support topics and statuses, teacher verification states, document types and roles.
 */
import type { PillTone } from '@/components/common/StatusPill.vue'
import type { TeacherVerificationState, UserRole } from '@/types/api'
import type { SupportRequesterRole, SupportStatus, SupportTopic, TeacherDocumentType } from '@/types/ops'

export const SUPPORT_TOPICS: readonly SupportTopic[] = [
  'ACCOUNT',
  'CLASSES',
  'LEARNING',
  'AI_HELPER',
  'VERIFICATION',
  'SAFETY',
  'OTHER',
]

const TOPIC_KEYS: Record<SupportTopic, string> = {
  ACCOUNT: 'opsLabels.topicAccount',
  CLASSES: 'opsLabels.topicClasses',
  LEARNING: 'opsLabels.topicLearning',
  AI_HELPER: 'opsLabels.topicAiHelper',
  VERIFICATION: 'opsLabels.topicVerification',
  SAFETY: 'opsLabels.topicSafety',
  OTHER: 'opsLabels.topicOther',
}

/** The requester's view of a status ("Answered · your turn"). */
const REQUEST_STATUS_KEYS: Record<SupportStatus, string> = {
  OPEN: 'opsLabels.requestOpen',
  ANSWERED: 'opsLabels.requestAnswered',
  SOLVED: 'opsLabels.requestSolved',
}

/** Support's view of a status (the admin queue tabs). */
const QUEUE_STATUS_KEYS: Record<SupportStatus, string> = {
  OPEN: 'opsLabels.queueOpen',
  ANSWERED: 'opsLabels.queueAnswered',
  SOLVED: 'opsLabels.queueSolved',
}

const VERIFICATION_KEYS: Record<TeacherVerificationState, string> = {
  NOT_SUBMITTED: 'opsLabels.verificationNotSubmitted',
  IN_REVIEW: 'opsLabels.verificationInReview',
  CHANGES_REQUESTED: 'opsLabels.verificationChangesRequested',
  APPROVED: 'opsLabels.verificationApproved',
}

const DOCUMENT_KEYS: Record<TeacherDocumentType, string> = {
  CERTIFICATE: 'opsLabels.documentCertificate',
  IDENTITY: 'opsLabels.documentIdentity',
  QUALIFICATION: 'opsLabels.documentQualification',
}

const ROLE_KEYS: Record<UserRole | SupportRequesterRole, string> = {
  STUDENT: 'opsLabels.roleStudent',
  TEACHER: 'opsLabels.roleTeacher',
  ADMIN: 'opsLabels.roleAdmin',
  VISITOR: 'opsLabels.roleVisitor',
}

export function supportTopicKey(topic: SupportTopic): string {
  return TOPIC_KEYS[topic]
}

export function requestStatusKey(status: SupportStatus): string {
  return REQUEST_STATUS_KEYS[status]
}

export function queueStatusKey(status: SupportStatus): string {
  return QUEUE_STATUS_KEYS[status]
}

/** The requester's view: "Answered · your turn" needs them now, "Open" waits for support, "Solved" is done. */
export function requestStatusTone(status: SupportStatus): PillTone {
  if (status === 'ANSWERED') return 'strong'
  return status === 'SOLVED' ? 'success' : 'outline'
}

export function verificationStateKey(state: TeacherVerificationState): string {
  return VERIFICATION_KEYS[state]
}

export function documentTypeKey(type: TeacherDocumentType): string {
  return DOCUMENT_KEYS[type]
}

export function roleKey(role: UserRole | SupportRequesterRole): string {
  return ROLE_KEYS[role]
}
