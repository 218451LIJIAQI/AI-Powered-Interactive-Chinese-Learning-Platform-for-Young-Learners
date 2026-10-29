/**
 * How the admin audit log shows an entry: the action's name (full keys of `auditActionMessages`, namespace
 * `auditActions`), its category label and an icon. Codes this bundle does not know fall back to the shared
 * `statusLabels.auditAction_*` labels and finally to a readable form of the code.
 */
import type { AuditCategory } from '@/types/ops'

const ACTION_KEYS: Readonly<Record<string, string>> = {
  ADMIN_USER_DEACTIVATE: 'auditActions.adminUserDeactivate',
  ADMIN_USER_REACTIVATE: 'auditActions.adminUserReactivate',
  ADMIN_TEACHER_APPROVE: 'auditActions.adminTeacherApprove',
  ADMIN_TEACHER_REJECT: 'auditActions.adminTeacherReject',
  ADMIN_TEACHER_DOCUMENT_VIEW: 'auditActions.adminTeacherDocumentView',
  ADMIN_INVITE_SENT: 'auditActions.adminInviteSent',
  ADMIN_INVITE_ACCEPTED: 'auditActions.adminInviteAccepted',
  ADMIN_PLATFORM_SETTINGS_UPDATED: 'auditActions.adminPlatformSettingsUpdated',
  ADMIN_ACTIVITY_STATUS: 'auditActions.adminActivityStatus',
  TEACHER_APPLICATION_SUBMITTED: 'auditActions.teacherApplicationSubmitted',
  TEACHER_VERIFICATION_RESUBMIT: 'auditActions.teacherVerificationResubmit',
  TEACHER_VERIFICATION_DOCUMENT_UPLOAD: 'auditActions.teacherVerificationDocumentUpload',
  TEACHER_VERIFICATION_DOCUMENT_VIEW: 'auditActions.teacherVerificationDocumentView',
  ACCOUNT_DELETED: 'auditActions.accountDeleted',
  EMAIL_CHANGE_REQUESTED: 'auditActions.emailChangeRequested',
  EMAIL_CHANGED: 'auditActions.emailChanged',
  PASSWORD_CHANGED: 'auditActions.passwordChanged',
  PASSWORD_RESET_REQUESTED: 'auditActions.passwordResetRequested',
  PASSWORD_RESET_COMPLETED: 'auditActions.passwordResetCompleted',
  LOGIN_LOCKOUT: 'auditActions.loginLockout',
  LOGOUT: 'auditActions.logout',
  PROFILE_UPDATED: 'auditActions.profileUpdated',
  SUPPORT_REPLY_SENT: 'auditActions.supportReplySent',
  SUPPORT_REQUEST_SOLVED: 'auditActions.supportRequestSolved',
  CONTENT_SUBMIT: 'auditActions.contentSubmit',
  CONTENT_RESUBMIT_AFTER_EDIT: 'auditActions.contentResubmitAfterEdit',
  CONTENT_APPROVE: 'auditActions.contentApprove',
  CONTENT_REJECT: 'auditActions.contentReject',
  CONTENT_ARCHIVE: 'auditActions.contentArchive',
  CONTENT_RESTORE: 'auditActions.contentRestore',
  ADMIN_CLASS_APPROVE: 'auditActions.adminClassApprove',
  ADMIN_CLASS_DETAILS_APPROVE: 'auditActions.adminClassDetailsApprove',
  ADMIN_CLASS_REQUEST_CHANGES: 'auditActions.adminClassRequestChanges',
  ADMIN_CLASS_DETAILS_REQUEST_CHANGES: 'auditActions.adminClassDetailsRequestChanges',
  ADMIN_CLASS_ARCHIVE: 'auditActions.adminClassArchive',
  ADMIN_CLASS_RESTORE: 'auditActions.adminClassRestore',
  ADMIN_LESSON_APPROVE: 'auditActions.adminLessonApprove',
  ADMIN_LESSON_REVISION_APPROVE: 'auditActions.adminLessonRevisionApprove',
  ADMIN_LESSON_REQUEST_CHANGES: 'auditActions.adminLessonRequestChanges',
  ADMIN_LESSON_REVISION_REQUEST_CHANGES: 'auditActions.adminLessonRevisionRequestChanges',
  ADMIN_LESSON_ARCHIVE: 'auditActions.adminLessonArchive',
  ADMIN_LESSON_RESTORE: 'auditActions.adminLessonRestore',
  TEACHER_CLASS_CREATE: 'auditActions.teacherClassCreate',
  TEACHER_CLASS_ARCHIVE: 'auditActions.teacherClassArchive',
  TEACHER_CLASS_RESTORE: 'auditActions.teacherClassRestore',
  TEACHER_CLASS_DELETE: 'auditActions.teacherClassDelete',
  TEACHER_LIVE_SESSION_SCHEDULE: 'auditActions.teacherLiveSessionSchedule',
  TEACHER_LIVE_SESSION_UPDATE: 'auditActions.teacherLiveSessionUpdate',
  TEACHER_LIVE_SESSION_CANCEL: 'auditActions.teacherLiveSessionCancel',
  LEARN_UNIT_PUBLISH: 'auditActions.learnUnitPublish',
  LEARN_UNIT_UNPUBLISH: 'auditActions.learnUnitUnpublish',
  LEVEL_CHECK_QUESTION_CREATE: 'auditActions.levelCheckQuestionCreate',
  LEVEL_CHECK_QUESTION_UPDATE: 'auditActions.levelCheckQuestionUpdate',
  PRACTICE_SET_CREATE: 'auditActions.practiceSetCreate',
  PRACTICE_SET_UPDATE: 'auditActions.practiceSetUpdate',
  PRACTICE_SET_PUBLISH: 'auditActions.practiceSetPublish',
  PRACTICE_SET_UNPUBLISH: 'auditActions.practiceSetUnpublish',
  PRACTICE_SET_ARCHIVE: 'auditActions.practiceSetArchive',
  PRACTICE_SET_RESTORE: 'auditActions.practiceSetRestore',
  AI_USAGE_LOG_EXPORTED: 'auditActions.aiUsageLogExported',
  MODERATION_ARCHIVE: 'auditActions.moderationArchive',
  MODERATION_RESTORE: 'auditActions.moderationRestore',
  MODERATION_REQUEST_REVISION: 'auditActions.moderationRequestRevision',
  AI_RESPONSE_REPORT_CREATE: 'auditActions.aiResponseReportCreate',
  AI_RESPONSE_REPORT_REVIEW: 'auditActions.aiResponseReportReview',
  PRACTICE_SUBMISSION_FILE_VIEWED: 'auditActions.practiceSubmissionFileViewed',
  PRACTICE_SUBMISSION_REVIEWED: 'auditActions.practiceSubmissionReviewed',
}

const CATEGORY_KEYS: Record<AuditCategory, string> = {
  ACCOUNT: 'auditActions.categoryAccount',
  CONTENT: 'auditActions.categoryContent',
  AI: 'auditActions.categoryAi',
  SUPPORT: 'auditActions.categorySupport',
  LEARNING: 'auditActions.categoryLearning',
  SYSTEM: 'auditActions.categorySystem',
}

const CATEGORY_ICONS: Record<AuditCategory, string> = {
  ACCOUNT: 'profile',
  CONTENT: 'content',
  AI: 'ai',
  SUPPORT: 'support',
  LEARNING: 'learn',
  SYSTEM: 'audit',
}

/** Icons for what happened, checked in order against the action code; the category icon is the fallback. */
const ACTION_ICONS: readonly (readonly [RegExp, string])[] = [
  [/REACTIVATE/, 'unlock'],
  [/DEACTIVATE|LOCKOUT/, 'lock'],
  [/DELETED/, 'trash'],
  [/INVITE/, 'userPlus'],
  [/EMAIL/, 'mail'],
  [/PASSWORD/, 'key'],
  [/LOGOUT/, 'logout'],
  [/SETTINGS/, 'settings'],
  [/APPLICATION_SUBMITTED|RESUBMIT|UPLOAD/, 'upload'],
  [/REPLY/, 'reply'],
  [/DOCUMENT_VIEW|FILE_VIEWED/, 'eye'],
  [/REJECT|REVISION/, 'edit'],
  [/APPROVE|REVIEW|SOLVED|ACCEPTED|RESTORE/, 'check'],
  [/ARCHIVE/, 'archive'],
  [/SUBMIT|REPORT_CREATE/, 'send'],
]

/** The full key of the action's name, or null when this bundle does not name it. */
export function auditActionKey(actionType: string): string | null {
  return ACTION_KEYS[actionType] ?? null
}

export function auditCategoryKey(category: AuditCategory): string {
  return CATEGORY_KEYS[category]
}

export function auditActionIcon(actionType: string, category: AuditCategory): string {
  return ACTION_ICONS.find(([pattern]) => pattern.test(actionType))?.[1] ?? CATEGORY_ICONS[category]
}
