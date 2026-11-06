/** Names and status tones of the admin console, shared by the admin pages and their drawers. */
import type { PillTone } from '@/components/common/StatusPill.vue'
import { humanizeEnumValue } from '@/plugins/statusLabelMessages'
import type { AiFeatureKey, AiResponseReportStatus, ContentStatus, TeacherVerificationState } from '@/types/api'
import type { RevisionStatus } from '@/types/classes'
import type { SupportStatus } from '@/types/ops'

/** Full message keys in the aiFeatures bundle (plugins/aiFeatureMessages) for the AI features. */
const AI_FEATURE_MESSAGE_KEYS: Readonly<Record<AiFeatureKey, string>> = {
  CHAT_TUTOR: 'aiFeatures.chatTutor',
  XIAOMEI_CONVERSATION: 'aiFeatures.xiaomeiConversation',
  QUIZ_GENERATOR: 'aiFeatures.quizGenerator',
  STORY_GENERATOR: 'aiFeatures.storyGenerator',
  PRACTICE_CHECK: 'aiFeatures.practiceCheck',
  TEACHER_DRAFT: 'aiFeatures.teacherDraft',
  LEARNING_PLAN: 'aiFeatures.learningPlan',
}

/**
 * The name of an AI feature through the page's `t` (the page registers the aiFeatures bundle). The key is matched
 * ignoring case and surrounding spaces; an unknown feature key reads as title case.
 */
export function aiFeatureLabel(featureKey: string, translate: (key: string) => string): string {
  const keys: Readonly<Record<string, string | undefined>> = AI_FEATURE_MESSAGE_KEYS
  const key = keys[featureKey.trim().toUpperCase()]
  return key ? translate(key) : humanizeEnumValue(featureKey)
}

/*
 * Status tones from the admin's side: `strong` is work waiting for an admin, `success` a positive end, `outline` what is
 * inactive or waits for someone else.
 */

export function accountTone(active: boolean): PillTone {
  return active ? 'success' : 'outline'
}

export function adminVerificationTone(state: TeacherVerificationState): PillTone {
  if (state === 'IN_REVIEW') return 'strong'
  if (state === 'APPROVED') return 'success'
  return 'outline'
}

export function adminSupportTone(status: SupportStatus): PillTone {
  if (status === 'OPEN') return 'strong'
  return status === 'SOLVED' ? 'success' : 'info'
}

export function adminReportTone(status: AiResponseReportStatus): PillTone {
  if (status === 'PENDING_REVIEW') return 'strong'
  return status === 'RESOLVED' ? 'success' : 'neutral'
}

export function practiceStatusTone(status: ContentStatus): PillTone {
  return status === 'PUBLISHED' ? 'success' : 'outline'
}

const TEACHER_CONTENT_KEYS: Readonly<Record<ContentStatus, string>> = {
  PUBLISHED: 'adminClasses.content.statuses.PUBLISHED',
  SUBMITTED: 'adminClasses.content.statuses.SUBMITTED',
  DRAFT: 'adminClasses.content.statuses.DRAFT',
  REJECTED: 'adminClasses.content.statuses.REJECTED',
  ARCHIVED: 'adminClasses.content.statuses.ARCHIVED',
}

/** One status per row of Teacher classes: a pending update of a published item replaces "Published". */
export function teacherContentStatus(
  status: ContentStatus,
  revisionStatus: RevisionStatus | null,
): { key: string; tone: PillTone } {
  if (status === 'PUBLISHED' && revisionStatus === 'SUBMITTED') {
    return { key: 'adminClasses.content.statuses.REVISION_SUBMITTED', tone: 'strong' }
  }
  if (status === 'PUBLISHED' && revisionStatus === 'REJECTED') {
    return { key: 'adminClasses.content.statuses.REVISION_REJECTED', tone: 'outline' }
  }
  if (status === 'PUBLISHED') return { key: TEACHER_CONTENT_KEYS.PUBLISHED, tone: 'success' }
  return { key: TEACHER_CONTENT_KEYS[status], tone: status === 'SUBMITTED' ? 'strong' : 'outline' }
}
