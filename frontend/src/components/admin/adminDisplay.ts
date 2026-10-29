/** AI feature names, shared by the admin dashboard and the AI usage and AI response report pages. */
import { humanizeEnumValue } from '@/plugins/statusLabelMessages'
import type { AiFeatureKey } from '@/types/api'

/** Full message keys in the aiFeatures bundle (plugins/aiFeatureMessages) for the AI features. */
export const AI_FEATURE_MESSAGE_KEYS: Readonly<Record<AiFeatureKey, string>> = {
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
