<template>
  <div class="plan-setup" :class="{ 'is-building': step === 'building' }">
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

    <template v-else>
      <!-- Welcome -->
      <section v-if="step === 'welcome'" class="plan-center" aria-labelledby="plan-welcome-title">
        <h2 id="plan-welcome-title" class="focus-headline">{{ t('plan.setup.welcomeTitle') }}</h2>
        <ol class="plan-welcome-steps">
          <li v-for="(item, index) in WELCOME_STEPS" :key="item.title">
            <span class="plan-welcome-number" aria-hidden="true">{{ index + 1 }}</span>
            <span class="plan-welcome-copy">
              <strong>{{ t(item.title) }}</strong>
              <span>{{ t(item.hint) }}</span>
            </span>
          </li>
        </ol>
        <p class="plan-form-note"><IconGlyph name="clock" :size="16" />{{ t('plan.setup.welcomeNote') }}</p>
      </section>

      <!-- 1 Why -->
      <section v-else-if="step === 'why'" class="focus-stage plan-question" aria-labelledby="plan-why-title">
        <div class="plan-question-head">
          <h2 id="plan-why-title" class="focus-headline">{{ t('plan.setup.whyTitle') }}</h2>
          <p>{{ t('plan.setup.whyLead') }}</p>
        </div>
        <GoalForm v-model="draft" :options="options.data.value" :sections="['reason']" />
      </section>

      <!-- 2 Goal -->
      <section v-else-if="step === 'goal'" class="focus-stage plan-question" aria-labelledby="plan-goal-title">
        <div class="plan-question-head">
          <h2 id="plan-goal-title" class="focus-headline">{{ t('plan.setup.goalTitle') }}</h2>
          <p>{{ t('plan.setup.goalLead') }}</p>
        </div>
        <GoalForm v-model="draft" :options="options.data.value" :sections="['goals']" />
      </section>

      <!-- 3 Pace -->
      <section v-else-if="step === 'pace'" class="focus-stage plan-question" aria-labelledby="plan-pace-title">
        <div class="plan-question-head">
          <h2 id="plan-pace-title" class="focus-headline">{{ t('plan.setup.paceTitle') }}</h2>
        </div>
        <GoalForm v-model="draft" :options="options.data.value" :sections="['deadline', 'pace']" />
      </section>

      <!-- 4 Your Mandarin -->
      <section v-else-if="step === 'level'" class="focus-stage plan-question" aria-labelledby="plan-level-title">
        <div class="plan-question-head">
          <h2 id="plan-level-title" class="focus-headline">{{ t('plan.setup.levelTitle') }}</h2>
          <p>{{ t('plan.setup.levelLead') }}</p>
        </div>
        <PlanChoiceGrid
          :choices="selfLevelChoices"
          :selected="levelChoice ? [levelChoice] : []"
          :label="t('plan.setup.levelTitle')"
          variant="rows"
          :columns="2"
          test-id="self-level-choices"
          @toggle="pickSelfLevel"
        />
        <p class="callout is-compact">
          <IconGlyph name="info" :size="16" /><span>{{ t('plan.setup.checkInfo') }}</span>
        </p>
        <p>
          <button class="btn btn-link plan-skip-check" type="button" data-testid="skip-check" @click="skipCheck">
            {{ t('plan.setup.skipCheck') }}
          </button>
        </p>
      </section>

      <!-- Level check -->
      <LevelCheckRunner
        v-else-if="step === 'check' && levelChoice"
        :start-level="CHECK_START[selfLevel]"
        @progress="checkProgress"
        @finished="checkFinished"
      />
      <LevelCheckDone
        v-else-if="step === 'checkDone' && checkResult"
        :known-skills="checkResult.knownSkills"
        :new-skills="checkResult.newSkills"
      />

      <!-- 5 Focus -->
      <section v-else-if="step === 'focus'" class="focus-stage plan-question" aria-labelledby="plan-focus-title">
        <div class="plan-question-head">
          <h2 id="plan-focus-title" class="focus-headline">{{ t('plan.setup.focusTitle') }}</h2>
          <p>{{ t('plan.setup.focusLead') }}</p>
        </div>
        <GoalForm
          v-model="draft"
          :options="options.data.value"
          :sections="['focus']"
          :suggested-focus="suggestedFocus"
        />
        <p v-if="suggestedSkill" class="plan-form-note">
          <IconGlyph name="magic" :size="14" />{{
            t('plan.setup.suggestedNote', { skill: t(SKILL_NAME_KEYS[suggestedSkill]).toLowerCase() })
          }}
        </p>
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
      <section v-else-if="step === 'result' && plan" class="focus-stage plan-wide" aria-labelledby="plan-result-title">
        <h2 id="plan-result-title" class="focus-headline">{{ t('plan.setup.resultTitle') }}</h2>
        <PlanResult :plan="plan" />
      </section>

      <!-- Path -->
      <section v-else-if="step === 'path' && plan" class="focus-stage plan-wide" aria-labelledby="plan-path-title">
        <div class="plan-path-head">
          <div>
            <h2 id="plan-path-title" class="focus-headline">
              {{ t('plan.path.title', { goal: goalTitle(t, plan.goal.title, plan.goal.reason) }) }}
            </h2>
            <ul class="plan-path-tags">
              <li class="tag">{{ t('plan.path.weeksChip', { count: plan.weeks.total }, plan.weeks.total) }}</li>
              <li class="tag">{{ t(PACES[plan.pace.key].hint) }}</li>
              <li class="tag">
                {{
                  plan.level.placed === plan.level.target
                    ? t(LEVEL_KEYS[plan.level.placed])
                    : t('plan.level.fromTo', {
                        from: t(LEVEL_KEYS[plan.level.placed]),
                        to: t(LEVEL_KEYS[plan.level.target]),
                      })
                }}
              </li>
            </ul>
          </div>
          <p v-if="plan.aiAssisted" class="plan-form-note">
            <IconGlyph name="magic" :size="14" />{{ t('plan.path.aiChip') }}
          </p>
        </div>
        <div class="plan-layout">
          <div class="plan-road-card">
            <PlanPath :plan="plan" :picked="picked" @pick="togglePick" />
          </div>
          <aside class="plan-rail">
            <PlanWeeks :plan="plan" :picked="picked" />
            <PlanOutcomes :plan="plan" />
            <section class="plan-rail-block" aria-labelledby="plan-working-title">
              <h3 id="plan-working-title" class="card-title">{{ t('plan.side.working') }}</h3>
              <dl class="plan-rail-rows">
                <div>
                  <dt>{{ t('plan.side.workingWeekly') }}</dt>
                  <dd>{{ t('plan.side.workingWeeklyText') }}</dd>
                </div>
                <div>
                  <dt>{{ t('plan.side.workingUnit') }}</dt>
                  <dd>{{ t('plan.side.workingUnitText') }}</dd>
                </div>
                <div>
                  <dt>{{ t('plan.side.wk', { week: plan.weeks.total }) }}</dt>
                  <dd>{{ t('plan.side.workingRetake') }}</dd>
                </div>
              </dl>
            </section>
          </aside>
        </div>
      </section>
    </template>

    <!-- Footer -->
    <FocusFooter v-if="footer">
      <template #start>
        <button
          v-if="footer.secondary"
          class="btn btn-secondary btn-lg"
          type="button"
          data-testid="setup-secondary"
          @click="footer.secondary.run"
        >
          {{ footer.secondary.label }}
        </button>
      </template>
      <template v-if="footer.note" #default>
        <span role="status">{{ footer.note }}</span>
      </template>
      <template #end>
        <button
          class="btn btn-primary btn-lg"
          type="button"
          data-testid="setup-primary"
          :disabled="footer.primary.disabled"
          @click="footer.primary.run"
        >
          {{ footer.primary.label }}
        </button>
      </template>
    </FocusFooter>
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
      <p class="plan-form-note">{{ t('plan.setup.editLevelStays') }}</p>
      <template #footer>
        <button class="btn btn-secondary" type="button" @click="editing = false">{{ t('plan.common.cancel') }}</button>
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
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import GoalForm from '@/components/plan/GoalForm.vue'
import { deadlineReady, emptyDraft, goalInput, type GoalDraft } from '@/components/plan/goalDraft'
import LevelCheckDone from '@/components/plan/LevelCheckDone.vue'
import LevelCheckRunner from '@/components/plan/LevelCheckRunner.vue'
import PlanBuilding from '@/components/plan/PlanBuilding.vue'
import PlanChoiceGrid, { type PlanChoice } from '@/components/plan/PlanChoiceGrid.vue'
import { goalTitle } from '@/components/plan/planFormat'
import {
  CHECK_START,
  FOCUS_FOR_SKILL,
  LEVEL_KEYS,
  PACES,
  SELF_LEVELS,
  SKILL_NAME_KEYS,
  type SelfLevelChoice,
} from '@/components/plan/planLabels'
import PlanOutcomes from '@/components/plan/PlanOutcomes.vue'
import PlanPath from '@/components/plan/PlanPath.vue'
import PlanResult from '@/components/plan/PlanResult.vue'
import PlanWeeks from '@/components/plan/PlanWeeks.vue'
import { usePlanPick } from '@/components/plan/usePlanPick'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { planMessages } from '@/plugins/planMessages'
import type { LevelCheckStep } from '@/types/learn'
import type { LearningPlan, LevelCheckAnswer, PlanSelfLevel } from '@/types/plan'
import { resolveApiError } from '@/utils/apiError'

/**
 * The learning plan setup after sign-up (focus mode, about 3 minutes): why, the goal, the pace, how much Mandarin, the
 * level check, the focus, then the plan is built (with SparkBot, or from rules when it is unavailable), revealed for the
 * student to confirm, and shown as a road map. "Set up later" leaves for Home without saving answers.
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
const levelChoice = ref<SelfLevelChoice | null>(null)
const skipped = ref(false)
const answers = ref<LevelCheckAnswer[]>([])
const checkResult = ref<NonNullable<LevelCheckStep['result']> | null>(null)
const checkAnswered = ref(0)
const checkMax = ref(8)
const plan = ref<LearningPlan | null>(null)
const buildError = ref<string | null>(null)
const leaving = ref(false)
const skipping = ref(false)
const skipError = ref<string | null>(null)
const editing = ref(false)
let lastBuild: (() => Promise<LearningPlan>) | null = null

const { picked, togglePick } = usePlanPick(plan)

const selfLevel = computed<PlanSelfLevel>(() => (levelChoice.value ? SELF_LEVELS[levelChoice.value].self : 'NOT_SURE'))

const selfLevelChoices = computed<PlanChoice[]>(() =>
  (Object.keys(SELF_LEVELS) as SelfLevelChoice[]).map((key) => ({
    value: key,
    label: t(SELF_LEVELS[key].label),
    hint: t(SELF_LEVELS[key].hint),
    icon: SELF_LEVELS[key].icon,
    tag: SELF_LEVELS[key].tag ? t(SELF_LEVELS[key].tag!) : null,
  })),
)

const suggestedSkill = computed(() => checkResult.value?.newSkills[0] ?? null)
const suggestedFocus = computed(() => (suggestedSkill.value ? FOCUS_FOR_SKILL[suggestedSkill.value] : null))

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
      return ''
    default:
      return t('plan.setup.topPlan')
  }
})

/** Five questions fill the bar; the level check runs its own bar; building and the plan stay full. */
const topProgress = computed(() => {
  const fixed: Record<Step, number> = {
    welcome: 0,
    why: 20,
    goal: 40,
    pace: 60,
    level: 80,
    check: 0,
    checkDone: 100,
    focus: 100,
    building: 100,
    result: 100,
    path: 100,
  }
  if (step.value === 'check') return Math.round(((checkAnswered.value + 0.5) / Math.max(1, checkMax.value)) * 100)
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
        primary: { label: t('plan.setup.startCheck'), run: startCheck, disabled: levelChoice.value === null },
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
        note: plan.value?.aiAssisted ? t('plan.setup.noGuarantee') : t('plan.path.rulesNote'),
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
  levelChoice.value = value as SelfLevelChoice
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
  levelChoice.value = 'NEW'
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
    levelCheck: { selfLevel: selfLevel.value, skipped: skipped.value, answers: answers.value },
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
