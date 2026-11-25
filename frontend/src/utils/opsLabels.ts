/**
 * Full i18n keys (bundle `opsLabelMessages`, namespace `opsLabels`) for support topics and queue statuses.
 */
import type { SelectableSupportTopic, SupportStatus, SupportTopic } from '@/types/ops'

export const SUPPORT_TOPIC_KEYS: Readonly<Record<SupportTopic, string>> = {
  ACCOUNT: 'opsLabels.topicAccount',
  CLASSES: 'opsLabels.topicClasses',
  LEARNING: 'opsLabels.topicLearning',
  AI_HELPER: 'opsLabels.topicAiHelper',
  VERIFICATION: 'opsLabels.topicVerification',
  SAFETY: 'opsLabels.topicSafety',
  OTHER: 'opsLabels.topicOther',
}

/**
 * The topics a message can be sent about, in display order. Verification only labels requests sent while teachers
 * still applied to the platform.
 */
export const SUPPORT_TOPICS: readonly SelectableSupportTopic[] = [
  'ACCOUNT',
  'CLASSES',
  'LEARNING',
  'AI_HELPER',
  'SAFETY',
  'OTHER',
]

/** Support's view of a status (the admin queue tabs). */
export const QUEUE_STATUS_KEYS: Readonly<Record<SupportStatus, string>> = {
  OPEN: 'opsLabels.queueOpen',
  ANSWERED: 'opsLabels.queueAnswered',
  SOLVED: 'opsLabels.queueSolved',
}
