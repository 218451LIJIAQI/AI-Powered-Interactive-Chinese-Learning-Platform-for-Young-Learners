<template>
  <div class="plan-setup">
    <FocusTopBar
      :label="topLabel"
      :close-label="t('plan.setup.closeLabel')"
      :progress="topProgress"
      :progress-label="t('plan.setup.progressLabel')"
      @close="close"
    />
    <h1 class="sr-only">{{ t('plan.setup.title') }}</h1>

    <StatePanel
      v-if="options.loading.value"
      tone="loading"
      :title="t('plan.page.loading')"
      :message="t('plan.check.loadingHint')"
      spacious
    />
    <StatePanel
      v-else-if="options.error.value || !options.data.value"
      tone="error"
      icon="alert"
      :title="t('plan.setup.buildError')"
      :message="options.error.value ?? ''"
      spacious
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="options.reload">{{ t('plan.common.retry') }}</button>
      </template>
    </StatePanel>

    <div v-else class="plan-setup-body" :class="{ 'is-wide': WIDE_STEPS.has(step) }">
      <!-- SparkBot asks every question; its short replies answer the student's choices. -->
      <p v-if="QUESTION_STEPS.has(step)" class="plan-asker">
        <span class="plan-spark-avatar" aria-hidden="true"><IconGlyph name="magic" :size="16" /></span>
        {{ t('plan.setup.sparkBot') }}
      </p>

      <!-- Welcome -->
      <section v-if="step === 'welcome'" class="plan-welcome" aria-labelledby="plan-welcome-title">
        <div class="plan-journey" aria-hidden="true">
          <span class="plan-journey-stop">
            <span class="plan-journey-you">{{ t('plan.setup.journeyYou') }}</span>
            {{ t('plan.setup.journeyToday') }}
          </span>
          <span class="plan-journey-road">{{ t('plan.setup.journeyPath') }}</span>
          <span class="plan-journey-stop">
            <span class="seal is-outline plan-journey-goal"><IconGlyph name="flag" :size="22" /></span>
            {{ t('plan.setup.journeyGoal') }}
          </span>
        </div>
        <h2 id="plan-welcome-title" class="plan-setup-title">{{ t('plan.setup.welcomeTitle') }}</h2>
        <p class="plan-setup-lead">{{ t('plan.setup.welcomeLead') }}</p>
        <ol class="plan-welcome-steps">
          <li v-for="(item, index) in WELCOME_STEPS" :key="item.title">
            <span class="plan-welcome-number" aria-hidden="true">{{ index + 1 }}</span>
            <strong>{{ t(item.title) }}</strong>
            <span>{{ t(item.hint) }}</span>
          </li>
        </ol>
        <p class="plan-setup-note"><IconGlyph name="clock" :size="16" />{{ t('plan.setup.welcomeNote') }}</p>
      </section>

      <!-- 1 Why -->
      <section v-else-if="step === 'why'" aria-labelledby="plan-why-title">
        <h2 id="plan-why-title" class="plan-setup-title">{{ t('plan.setup.whyTitle') }}</h2>
        <p class="plan-setup-lead">{{ t('plan.setup.whyLead') }}</p>
        <GoalForm v-model="draft" :options="options.data.value" :sections="['reason']" />
        <SparkReply v-if="draft.reason" :text="t(REASONS[draft.reason].reply)" />
      </section>

      <!-- 2 Goal -->
      <section v-else-if="step === 'goal'" aria-labelledby="plan-goal-title">
        <h2 id="plan-goal-title" class="plan-setup-title">{{ t('plan.setup.goalTitle') }}</h2>
        <p class="plan-setup-lead">{{ t('plan.setup.goalLead') }}</p>
        <GoalForm v-model="draft" :options="options.data.value" :sections="['goals']" />
        <SparkReply v-if="goalReply" :text="goalReply" />
      </section>

      <!-- 3 Pace -->
      <section v-else-if="step === 'pace'" aria-labelledby="plan-pace-title">
        <h2 id="plan-pace-title" class="plan-setup-title">{{ t('plan.setup.paceTitle') }}</h2>
        <p class="plan-setup-lead">{{ t('plan.setup.paceLead') }}</p>
        <GoalForm v-model="draft" :options="options.data.value" :sections="['deadline', 'pace']" />
        <SparkReply v-if="draft.pace" :text="t(PACES[draft.pace].reply)" />
      </section>

      <!-- 4 Your Mandarin -->
      <section v-else-if="step === 'level'" aria-labelledby="plan-level-title">
        <h2 id="plan-level-title" class="plan-setup-title">{{ t('plan.setup.levelTitle') }}</h2>
        <p class="plan-setup-lead">{{ t('plan.setup.levelLead') }}</p>
        <PlanChoiceGrid
          :choices="selfLevelChoices"
          :selected="selfLevel ? [selfLevel] : []"
          :label="t('plan.setup.levelTitle')"
          test-id="self-level-choices"
          @toggle="pickSelfLevel"
        />
        <p class="plan-setup-note"><IconGlyph name="info" :size="16" />{{ t('plan.setup.checkInfo') }}</p>
        <button class="btn btn-link plan-skip-check" type="button" data-testid="skip-check" @click="skipCheck">
          {{ t('plan.setup.skipCheck') }}<IconGlyph name="chevronRight" :size="16" />
        </button>
      </section>

      <!-- Level check -->
      <LevelCheckRunner
        v-else-if="step === 'check' && selfLevel"
        :start-level="CHECK_START[selfLevel]"
        @progress="checkProgress"
        @finished="checkFinished"
      />
      <LevelCheckDone
        v-else-if="step === 'checkDone' && checkResult"
        :answered="checkResult.answeredCount"
        :known-skills="checkResult.knownSkills"
        :new-skills="checkResult.newSkills"
      />

      <!-- 5 Focus -->
      <section v-else-if="step === 'focus'" aria-labelledby="plan-focus-title">
        <h2 id="plan-focus-title" class="plan-setup-title">{{ t('plan.setup.focusTitle') }}</h2>
        <p class="plan-setup-lead">{{ t('plan.setup.focusLead') }}</p>
        <GoalForm
          v-model="draft"
          :options="options.data.value"
          :sections="['focus']"
          :suggested-focus="suggestedFocus"
        />
        <p v-if="suggestedSkill" class="plan-setup-note">
          <IconGlyph name="info" :size="16" />{{
            t('plan.setup.suggestedNote', { skill: t(SKILL_KEYS[suggestedSkill]) })
          }}
        </p>
        <SparkReply v-if="draft.focus.length" :text="t('plan.setup.focusReply')" />
      </section>

      <!-- Building -->
      <template v-else-if="step === 'building'">
        <StatePanel
          v-if="buildError"
          tone="error"
          icon="alert"
          :title="t('plan.setup.buildError')"
          :message="buildError"
          spacious
        >
          <template #actions>
            <button class="btn btn-secondary" type="button" @click="step = plan ? 'result' : 'focus'">
              {{ t('plan.common.back') }}
            </button>
            <button class="btn btn-primary" type="button" @click="retryBuild">{{ t('plan.common.retry') }}</button>
          </template>
        </StatePanel>
        <PlanBuilding v-else />
      </template>

      <!-- Result -->
      <section v-else-if="step === 'result' && plan" aria-labelledby="plan-result-title">
        <div class="plan-result-head">
          <span class="seal plan-result-seal" lang="zh-CN" aria-hidden="true">好</span>
          <h2 id="plan-result-title" class="plan-setup-title">{{ t('plan.setup.resultTitle') }}</h2>
          <p class="plan-setup-lead">{{ t('plan.setup.resultLead') }}</p>
        </div>
        <PlanResult :plan="plan" />
      </section>

      <!-- Path -->
      <section v-else-if="step === 'path' && plan" aria-labelledby="plan-path-title">
        <div class="plan-path-head">
          <h2 id="plan-path-title" class="plan-setup-title">
            {{ t('plan.path.title', { goal: goalTitle(t, plan.goal.title, plan.goal.reason) }) }}
          </h2>
          <ul class="plan-path-chips">
            <li class="tag">{{ t('plan.path.weeksChip', { count: plan.weeks.total }, plan.weeks.total) }}</li>
            <li class="tag">{{ t(PACES[plan.pace.key].hint) }}</li>
            <li class="tag">
              {{
                t('plan.level.fromTo', { from: t(LEVEL_KEYS[plan.level.placed]), to: t(LEVEL_KEYS[plan.level.target]) })
              }}
            </li>
          </ul>
          <p v-if="plan.aiAssisted" class="plan-path-ai">
            <IconGlyph name="ai" :size="14" />{{ t('plan.path.aiChip') }}
          </p>
        </div>
        <DataNotice v-if="!plan.aiAssisted" :info="t('plan.path.rulesNote')" />
        <div class="plan-path-layout">
          <PlanPath :plan="plan" />
          <aside class="plan-side">
            <PlanWeeks :plan="plan" />
            <PlanOutcomes :plan="plan" />
            <PlanCoverage :plan="plan" />
            <PlanAiNote v-if="plan.aiAssisted" :plan="plan" />
          </aside>
        </div>
      </section>
    </div>

    <!-- Footer -->
    <div v-if="footer" class="plan-setup-footer">
      <button
        v-if="footer.secondary"
        class="btn btn-outline btn-lg"
        type="button"
        data-testid="setup-secondary"
        @click="footer.secondary.run"
      >
        {{ footer.secondary.label }}
      </button>
      <p v-if="footer.note" class="plan-setup-footer-note">{{ footer.note }}</p>
      <button
        class="btn btn-primary btn-lg"
        type="button"
        data-testid="setup-primary"
        :disabled="footer.primary.disabled"
        @click="footer.primary.run"
      >
        {{ footer.primary.label }}
      </button>
    </div>
    <p v-if="skipError" class="alert alert-danger plan-setup-error" role="alert">{{ skipError }}</p>

    <ConfirmDialog
      tone="leave"
      :open="leaving"
      :title="t('plan.setup.laterTitle')"
      :message="t('plan.setup.laterMessage')"
      :confirm-label="t('plan.setup.setUpLater')"
      :cancel-label="t('plan.setup.keepGoing')"
      :busy="skipping"
      @confirm="setUpLater"
      @cancel="leaving = false"
    />

    <AppOverlay :open="editing" :title="t('plan.setup.editTitle')" test-id="edit-goal-dialog" @close="editing = false">
      <GoalForm v-model="editDraft" :options="options.data.value!" :sections="['goals', 'deadline']" variant="chips" />
      <p class="plan-form-note"><IconGlyph name="info" :size="16" />{{ t('plan.setup.editLevelStays') }}</p>
      <template #footer>
        <button class="btn btn-ghost" type="button" @click="editing = false">{{ t('plan.common.cancel') }}</button>
        <button class="btn btn-primary" type="button" :disabled="!deadlineReady(editDraft)" @click="rebuild">
          {{ t('plan.setup.saveRebuild') }}
        </button>
      </template>
    </AppOverlay>
  </div>
</template>

<script setup lang="ts">
import '@/styles/learn.css'
import '@/styles/plan.css'

import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { planApi } from '@/api/plan'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import GoalForm from '@/components/plan/GoalForm.vue'
import { deadlineReady, emptyDraft, goalInput, type GoalDraft } from '@/components/plan/goalDraft'
import LevelCheckDone from '@/components/plan/LevelCheckDone.vue'
import LevelCheckRunner from '@/components/plan/LevelCheckRunner.vue'
import PlanAiNote from '@/components/plan/PlanAiNote.vue'
import PlanBuilding from '@/components/plan/PlanBuilding.vue'
import PlanChoiceGrid, { type PlanChoice } from '@/components/plan/PlanChoiceGrid.vue'
import PlanCoverage from '@/components/plan/PlanCoverage.vue'
import { goalTitle } from '@/components/plan/planFormat'
import {
  CHECK_START,
  FOCUS_FOR_SKILL,
  LEVEL_KEYS,
  PACES,
  REASONS,
  SELF_LEVELS,
  SKILL_KEYS,
} from '@/components/plan/planLabels'
import PlanOutcomes from '@/components/plan/PlanOutcomes.vue'
import PlanPath from '@/components/plan/PlanPath.vue'
import PlanResult from '@/components/plan/PlanResult.vue'
import PlanWeeks from '@/components/plan/PlanWeeks.vue'
import SparkReply from '@/components/plan/SparkReply.vue'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { planMessages } from '@/plugins/planMessages'
import type { LevelCheckStep } from '@/types/learn'
import type { LearningPlan, LevelCheckAnswer, PlanSelfLevel } from '@/types/plan'
import { resolveApiError } from '@/utils/apiError'

/**
 * The learning plan setup after sign-up (focus mode, about 3 minutes): why, the goal, the pace, how much Mandarin,
 * the adaptive level check, the focus, then the plan is built (with SparkBot, or from rules when it is unavailable),
 * revealed for the student to confirm, and shown as a path. "Set up later" leaves for Home without saving answers.
 */
type Step =
  | 'welcome'
  | 'why'
  | 'goal'
  | 'pace'
  | 'level'
  | 'check'
  | 'checkDone'
  | 'focus'
  | 'building'
  | 'result'
  | 'path'

interface FooterAction {
  label: string
  run: () => void
  disabled?: boolean
}

/** Steps SparkBot asks, and the wide steps that show the plan. */
const QUESTION_STEPS: ReadonlySet<Step> = new Set(['why', 'goal', 'pace', 'level', 'focus'])
const WIDE_STEPS: ReadonlySet<Step> = new Set(['result', 'path'])

const WELCOME_STEPS = [
  { title: 'plan.setup.stepGoal', hint: 'plan.setup.stepGoalHint' },
  { title: 'plan.setup.stepLevel', hint: 'plan.setup.stepLevelHint' },
  { title: 'plan.setup.stepPlan', hint: 'plan.setup.stepPlanHint' },
] as const

/** The building screen stays at least this long, so its steps can be read. */
const MIN_BUILDING_MS = 2400

const router = useRouter()
const { t, locale } = useLocalMessages({ plan: planMessages })
const options = useResource(() => planApi.options())

const step = ref<Step>('welcome')
const draft = ref<GoalDraft>(emptyDraft())
const editDraft = ref<GoalDraft>(emptyDraft())
const selfLevel = ref<PlanSelfLevel | null>(null)
const skipped = ref(false)
const answers = ref<LevelCheckAnswer[]>([])
const checkResult = ref<NonNullable<LevelCheckStep['result']> | null>(null)
const checkAnswered = ref(0)
const checkMax = ref(6)
const plan = ref<LearningPlan | null>(null)
const buildError = ref<string | null>(null)
const leaving = ref(false)
const skipping = ref(false)
const skipError = ref<string | null>(null)
const editing = ref(false)
let lastBuild: (() => Promise<LearningPlan>) | null = null

const selfLevelChoices = computed<PlanChoice[]>(() =>
  (Object.keys(SELF_LEVELS) as PlanSelfLevel[]).map((level) => ({
    value: level,
    label: t(SELF_LEVELS[level].label),
    hint: t(SELF_LEVELS[level].hint),
    icon: SELF_LEVELS[level].icon,
  })),
)

const suggestedSkill = computed(() => checkResult.value?.newSkills[0] ?? null)
const suggestedFocus = computed(() => (suggestedSkill.value ? FOCUS_FOR_SKILL[suggestedSkill.value] : null))

const goalReply = computed(() => {
  if (draft.value.goalText.trim()) return t('plan.setup.goalReplyWords')
  return draft.value.goals.length ? t('plan.setup.goalReplyPicked') : ''
})

const topLabel = computed(() => {
  switch (step.value) {
    case 'welcome':
      return t('plan.setup.topWelcome')
    case 'why':
      return t('plan.setup.topStep', { step: 1 })
    case 'goal':
      return t('plan.setup.topStep', { step: 2 })
    case 'pace':
      return t('plan.setup.topStep', { step: 3 })
    case 'level':
      return t('plan.setup.topStep', { step: 4 })
    case 'check':
      return t('plan.check.topCheck', { count: Math.min(checkAnswered.value + 1, checkMax.value), max: checkMax.value })
    case 'checkDone':
      return t('plan.check.topDone')
    case 'focus':
      return t('plan.setup.topStep', { step: 5 })
    case 'building':
      return t('plan.setup.topBuilding')
    default:
      return t('plan.setup.topPlan')
  }
})

const topProgress = computed(() => {
  const fixed: Record<Step, number> = {
    welcome: 0,
    why: 10,
    goal: 22,
    pace: 34,
    level: 46,
    check: 50,
    checkDone: 66,
    focus: 76,
    building: 88,
    result: 94,
    path: 100,
  }
  if (step.value === 'check') return 50 + Math.round((checkAnswered.value / Math.max(1, checkMax.value)) * 14)
  return fixed[step.value]
})

const whyReady = computed(
  () => draft.value.reason !== null && (draft.value.reason !== 'OTHER' || draft.value.reasonText.trim() !== ''),
)

const footer = computed<{ primary: FooterAction; secondary?: FooterAction; note?: string } | null>(() => {
  const back = (to: Step): FooterAction => ({ label: t('plan.common.back'), run: () => (step.value = to) })
  switch (step.value) {
    case 'welcome':
      return {
        secondary: { label: t('plan.setup.setUpLater'), run: () => (leaving.value = true) },
        primary: { label: t('plan.setup.letsStart'), run: () => (step.value = 'why') },
      }
    case 'why':
      return {
        secondary: back('welcome'),
        primary: { label: t('plan.common.continue'), run: () => (step.value = 'goal'), disabled: !whyReady.value },
      }
    case 'goal':
      return { secondary: back('why'), primary: { label: t('plan.common.continue'), run: () => (step.value = 'pace') } }
    case 'pace':
      return {
        secondary: back('goal'),
        primary: {
          label: t('plan.common.continue'),
          run: () => (step.value = 'level'),
          disabled: !deadlineReady(draft.value) || draft.value.pace === null,
        },
      }
    case 'level':
      return {
        secondary: back('pace'),
        primary: { label: t('plan.setup.startCheck'), run: startCheck, disabled: selfLevel.value === null },
      }
    case 'checkDone':
      return { primary: { label: t('plan.common.continue'), run: () => (step.value = 'focus') } }
    case 'focus':
      return {
        secondary: back(skipped.value ? 'level' : 'checkDone'),
        primary: { label: t('plan.setup.buildPlan'), run: build },
      }
    case 'result':
      return {
        secondary: { label: t('plan.setup.editGoal'), run: openEdit },
        primary: { label: t('plan.setup.showPath'), run: () => (step.value = 'path') },
      }
    case 'path':
      return {
        secondary: back('result'),
        note: t('plan.setup.noGuarantee'),
        primary: { label: t('plan.setup.startPath'), run: startPath },
      }
    default:
      return null
  }
})

function close(): void {
  if (plan.value) {
    void router.push({ name: 'student-home' })
    return
  }
  leaving.value = true
}

async function setUpLater(): Promise<void> {
  skipping.value = true
  skipError.value = null
  try {
    await planApi.skip()
    await router.push({ name: 'student-home' })
  } catch (reason) {
    leaving.value = false
    skipError.value = resolveApiError(reason, t('plan.setup.skipError'))
  } finally {
    skipping.value = false
  }
}

function pickSelfLevel(value: string): void {
  selfLevel.value = value as PlanSelfLevel
}

function startCheck(): void {
  skipped.value = false
  answers.value = []
  checkResult.value = null
  checkAnswered.value = 0
  step.value = 'check'
}

/** "Brand new? Skip the check": start from the first lesson. */
function skipCheck(): void {
  selfLevel.value = 'NEW_TO_MANDARIN'
  skipped.value = true
  answers.value = []
  checkResult.value = null
  step.value = 'focus'
}

function checkProgress(answered: number, max: number): void {
  checkAnswered.value = answered
  checkMax.value = max
}

function checkFinished(sent: LevelCheckAnswer[], result: NonNullable<LevelCheckStep['result']>): void {
  answers.value = sent
  checkResult.value = result
  step.value = 'checkDone'
}

async function runBuild(request: () => Promise<LearningPlan>): Promise<void> {
  lastBuild = request
  buildError.value = null
  step.value = 'building'
  const started = Date.now()
  try {
    const built = await request()
    await new Promise((resolve) => setTimeout(resolve, Math.max(0, MIN_BUILDING_MS - (Date.now() - started))))
    plan.value = built
    step.value = 'result'
    notifyProgressChanged()
  } catch (reason) {
    buildError.value = resolveApiError(reason, t('plan.setup.buildError'))
  }
}

function build(): void {
  const payload = {
    goal: goalInput(draft.value),
    levelCheck: { selfLevel: selfLevel.value ?? 'NEW_TO_MANDARIN', skipped: skipped.value, answers: answers.value },
    language: locale.value,
  }
  void runBuild(() => planApi.build(payload))
}

function retryBuild(): void {
  if (lastBuild) void runBuild(lastBuild)
}

function openEdit(): void {
  editDraft.value = { ...draft.value }
  editing.value = true
}

/** "Save and rebuild": the level check result stays; the plan is rebuilt for the new goal. */
function rebuild(): void {
  draft.value = { ...editDraft.value }
  editing.value = false
  const goal = goalInput(draft.value)
  void runBuild(() => planApi.updateGoal(goal, locale.value))
}

function startPath(): void {
  void router.push({ name: 'student-home' })
}
</script>
