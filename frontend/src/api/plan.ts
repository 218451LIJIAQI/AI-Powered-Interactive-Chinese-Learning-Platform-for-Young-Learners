/**
 * The learning plan module's endpoints: Home, the setup, the plan page, a new goal, a retaken level check and the
 * e-mail confirmation of a new account.
 */
import type { AppLanguage } from '@/types/api'
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
} from '@/types/plan'

import { apiGet, apiPost, apiPut } from './request'

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
  /** "Build my plan": the plan is saved as a draft until `confirm` ("Start my path"). */
  build(payload: BuildPlanPayload) {
    return apiPost<LearningPlan>('/students/plan', payload, BUILD_CONFIG)
  },
  /**
   * "Start my path": the draft becomes the learner's plan; 409 when it was built more than a day ago or its goal date has
   * passed.
   */
  confirm() {
    return apiPost<LearningPlan>('/students/plan/confirm')
  },
  /** Changes the plan, or, before "Start my path", rebuilds the setup's draft. */
  updateGoal(goal: GoalInput, language: AppLanguage) {
    return apiPut<LearningPlan>('/students/plan/goal', { goal, language }, BUILD_CONFIG)
  },
  /** "Pick this first": the optional class to take first (null for the plan's own order), on the plan or the draft. */
  pickClass(classId: number | null) {
    return apiPut<LearningPlan>('/students/plan/class-pick', { classId })
  },
  retake(answers: readonly LevelCheckAnswer[], language: AppLanguage) {
    return apiPost<RetakeResult>('/students/plan/level-check', { answers, language }, BUILD_CONFIG)
  },
  /** "Set up later", also after the plan was built: a draft that was not started is discarded. */
  skip() {
    return apiPost<void>('/students/plan/skip')
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
