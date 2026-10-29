/**
 * The learning plan module's endpoints: Home, the setup, the plan page, a new goal, a retaken level check, the
 * teacher's read-only learning goal and the e-mail confirmation of a new account.
 */
import type { LinkTokenResponse } from '@/types/ops'
import type {
  BuildPlanPayload,
  EmailConfirmation,
  GoalInput,
  HomeView,
  LearningPlan,
  LevelCheckAnswer,
  PlanOptions,
  PlanOverview,
  RetakeResult,
  TeacherLearningGoal,
} from '@/types/plan'
import type { MandarinLevel } from '@/types/learn'

import { apiGet, apiPost, apiPut } from './services'

/** SparkBot may take a while to read the goal; the server falls back to rules when it does not answer. */
const BUILD_CONFIG = { timeout: 60_000 }

export const planApi = {
  home() {
    return apiGet<HomeView>('/students/home')
  },
  options() {
    return apiGet<PlanOptions>('/students/plan/options')
  },
  /** Null without a plan. */
  plan() {
    return apiGet<LearningPlan | null>('/students/plan')
  },
  overview() {
    return apiGet<PlanOverview>('/students/plan/overview')
  },
  build(payload: BuildPlanPayload) {
    return apiPost<LearningPlan>('/students/plan', payload, BUILD_CONFIG)
  },
  updateGoal(goal: GoalInput, language: string) {
    return apiPut<LearningPlan>('/students/plan/goal', { goal, language }, BUILD_CONFIG)
  },
  retake(startLevel: MandarinLevel, answers: readonly LevelCheckAnswer[], language: string) {
    return apiPost<RetakeResult>('/students/plan/level-check', { startLevel, answers, language }, BUILD_CONFIG)
  },
  /** "Set up later". */
  skip() {
    return apiPost<PlanOverview>('/students/plan/skip')
  },
}

export const teacherPlanApi = {
  /** Null when the student has no plan; 403 for a student outside the teacher's classes. */
  learningGoal(studentId: number) {
    return apiGet<TeacherLearningGoal | null>(`/teachers/students/${studentId}/learning-goal`)
  },
}

export const emailConfirmationApi = {
  /** "Send the link again" (once a minute). */
  resend() {
    return apiPost<EmailConfirmation>('/users/me/email-confirmation')
  },
  /** The link from the sign-up e-mail. */
  confirm(token: string) {
    return apiPost<LinkTokenResponse>('/auth/confirm-signup-email', { token })
  },
}
