<template>
  <div class="plan-setup" :class="{ 'is-building': step === 'building' && !buildError }">
    <FocusTopBar
      :label="topLabel"
      :close-label="t('plan.setup.closeLabel')"
      :close-disabled="starting || hasPlanAlready"
      :progress="topProgress"
      :progress-label="t('plan.setup.progressLabel')"
      @close="requestClose"
    />
    <h1 class="sr-only">{{ t('plan.setup.title') }}</h1>
    <p v-if="exitError" ref="exitErrorRef" class="alert alert-danger plan-setup-error" role="alert" tabindex="-1">
      {{ exitError }}
    </p>
    <!--
      "Start my path" failed. A plan built more than a day ago (409) is built again from the same answers; a plan whose
      goal date has passed (409) needs a new date first, so its goal opens with the date to pick again.
    -->
    <div
      v-if="startError"
      ref="startErrorRef"
      class="alert alert-danger plan-setup-error plan-start-error"
      role="alert"
      tabindex="-1"
    >
      <span>{{ startError.message }}</span>
      <button
        v-if="startError.action === 'rebuild'"
        class="btn btn-secondary btn-sm"
        type="button"
        :disabled="Boolean(finishing) || refreshingOptions"
        data-testid="setup-build-again"
        @click="buildAgain"
      >
        {{ t('plan.setup.buildAgain') }}
      </button>
      <button
        v-else-if="startError.action === 'pickDate'"
        class="btn btn-secondary btn-sm"
        type="button"
        :disabled="Boolean(finishing)"
        data-testid="setup-pick-new-date"
        @click="pickNewDate"
      >
        {{ t('plan.setup.editGoal') }}
      </button>
    </div>

    <StatePanel
      v-if="loading"
      heading-tag="h2"
      tone="loading"
      :title="t('plan.page.loading')"
      :message="t('plan.check.loadingHint')"
      spacious
    />
    <StatePanel
      v-else-if="!setupOptions"
      heading-tag="h2"
      tone="error"
      icon="alert"
      :title="t('plan.page.loadError')"
      :message="loadError ?? ''"
      spacious
      @retry="reloadPage"
    />

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

      <!-- Why, the goal, when and how much time, the focus -->
      <section
        v-else-if="formStep"
        :key="step"
        class="focus-stage plan-question"
        :aria-labelledby="`plan-${step}-title`"
      >
        <div class="plan-question-head">
          <h2 :id="`plan-${step}-title`" class="focus-headline">{{ t(formStep.title) }}</h2>
          <p v-if="formStep.lead">{{ t(formStep.lead) }}</p>
        </div>
        <GoalForm
          v-model="draft"
          :options="setupOptions"
          :sections="formStep.sections"
          :suggested-focus="suggestedFocus"
        />
        <p v-if="step === 'focus' && suggestedFocus" class="plan-form-note" data-testid="focus-suggested">
          <IconGlyph name="magic" :size="14" />{{ t('plan.setup.suggestedNote') }}
        </p>
      </section>

      <!-- How much Mandarin -->
      <section v-else-if="step === 'level'" class="focus-stage plan-question" aria-labelledby="plan-level-title">
        <div class="plan-question-head">
          <h2 id="plan-level-title" class="focus-headline">{{ t('plan.setup.levelTitle') }}</h2>
        </div>
        <PlanChoiceGrid
          :choices="selfLevelChoices"
          :selected="levelChoice ? [levelChoice] : []"
          :label="t('plan.setup.levelTitle')"
          variant="rows"
          :columns="2"
          test-id="self-level-choices"
          @toggle="levelChoice = $event"
        />
        <p class="callout is-compact">
          <IconGlyph name="info" :size="16" /><span>{{ t('plan.setup.checkInfo') }}</span>
        </p>
        <!-- Brand new learners start from the first lesson (S1.32); anyone else can still skip the check. -->
        <p>
          <button
            v-if="levelChoice === 'NEW'"
            class="btn btn-link"
            type="button"
            data-testid="take-check"
            @click="startCheck"
          >
            {{ t('plan.setup.takeCheckInstead') }}
          </button>
          <button v-else class="btn btn-link" type="button" data-testid="skip-check" @click="skipCheck">
            {{ t('plan.setup.skipCheck') }}
          </button>
        </p>
      </section>

      <!-- Level check -->
      <LevelCheckRunner
        v-else-if="step === 'check'"
        :start-level="setupOptions.checkStarts[selfLevel]"
        :paused="leaving"
        @progress="checkProgress"
        @finished="checkFinished"
      />
      <LevelCheckDone
        v-else-if="step === 'checkDone' && check"
        :title="t('plan.check.startingLevel', { level: t(LEVEL_KEYS[check.result.level]) })"
      />

      <!-- Building -->
      <template v-else-if="step === 'building'">
        <StatePanel
          v-if="buildError"
          heading-tag="h2"
          tone="error"
          icon="alert"
          :title="t('plan.setup.buildError')"
          :message="buildError"
          spacious
        >
          <template #actions>
            <button class="btn btn-secondary" type="button" @click="backFromBuildError">
              {{ t('plan.common.back') }}
            </button>
            <button
              v-if="!plan && buildErrorStatus === 400"
              class="btn btn-secondary"
              type="button"
              data-testid="setup-edit-pace"
              @click="returnToPace"
            >
              {{ t('plan.setup.editGoal') }}
            </button>
            <button class="btn btn-primary" type="button" @click="retryBuild">{{ t('common.retry') }}</button>
          </template>
        </StatePanel>
        <PlanBuilding v-else :skipped-check="!check" />
      </template>

      <!-- Result -->
      <section v-else-if="step === 'result' && plan" class="focus-stage plan-wide" aria-labelledby="plan-result-title">
        <h2 id="plan-result-title" class="focus-headline">{{ t('plan.setup.resultTitle') }}</h2>
        <PlanResult :plan="plan" :skipped-check="!check" @reported="reported" @daily-goal="showToast" />
      </section>

      <!-- Path -->
      <PlanSetupPath
        v-else-if="step === 'path' && plan"
        :plan="plan"
        :picked="picked"
        :pick-error="pickError"
        :starting="starting"
        @pick="togglePick"
        @reported="reported"
      />

      <section v-else-if="finished" class="plan-center" aria-labelledby="plan-finished-title">
        <h2 id="plan-finished-title" class="focus-headline">
          {{ t(step === 'saved' ? 'plan.setup.pathSaved' : 'plan.setup.setupSkipped') }}
        </h2>
      </section>

      <AppOverlay
        :open="editing"
        :title="t('plan.setup.editTitle')"
        test-id="edit-goal-dialog"
        @close="editing = false"
      >
        <GoalForm v-model="editDraft" :options="setupOptions" :sections="['goals', 'deadline']" compact />
        <template #footer>
          <button class="btn btn-secondary" type="button" @click="editing = false">
            {{ t('common.cancel') }}
          </button>
          <button
            class="btn btn-primary"
            type="button"
            :disabled="refreshingOptions || !deadlineReady(editDraft)"
            data-testid="save-rebuild"
            @click="rebuild"
          >
            {{ t('plan.setup.saveRebuild') }}
          </button>
        </template>
      </AppOverlay>
    </template>

    <FocusFooter v-if="footer">
      <template #start>
        <button
          v-if="footer.secondary"
          class="btn btn-secondary btn-lg"
          type="button"
          data-testid="setup-secondary"
          :disabled="starting"
          @click="footer.secondary.run"
        >
          <IconGlyph v-if="footer.secondary.icon" :name="footer.secondary.icon" :size="18" />
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

    <!-- × and "Set up later" ask first (X2.01); once the plan is built, leaving loses more and says so (X2.28). -->
    <ConfirmDialog
      :open="leaving"
      :title="plan ? t('plan.setup.readyTitle') : t('plan.setup.laterTitle')"
      :message="plan ? t('plan.setup.readyMessage') : t('plan.setup.laterMessage')"
      :confirm-label="plan ? t('plan.setup.leaveWithoutSaving') : t('plan.setup.setUpLater')"
      :cancel-label="t('plan.setup.keepGoing')"
      :busy="finishing === 'skip'"
      @confirm="finishSetup('skip')"
      @cancel="leaving = false"
    />
    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import '@/styles/learn-shared.css'
import '@/styles/plan.css'
import '@/styles/plan-setup.css'

import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { getAuthSessionVersion } from '@/api/http'
import { planApi } from '@/api/plan'
import AppOverlay from '@/components/common/AppOverlay.vue'
import AppToast from '@/components/common/AppToast.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import GoalForm, { type Section } from '@/components/plan/GoalForm.vue'
import { deadlinePassed, deadlineReady, emptyDraft, goalInput, type GoalDraft } from '@/components/plan/goalDraft'
import LevelCheckDone from '@/components/plan/LevelCheckDone.vue'
import LevelCheckRunner from '@/components/plan/LevelCheckRunner.vue'
import PlanBuilding from '@/components/plan/PlanBuilding.vue'
import PlanChoiceGrid, { type PlanChoice } from '@/components/plan/PlanChoiceGrid.vue'
import { FOCUS_FOR_SKILL, LEVEL_KEYS, SELF_LEVELS, type SelfLevelChoice } from '@/components/plan/planLabels'
import PlanResult from '@/components/plan/PlanResult.vue'
import PlanSetupPath from '@/components/plan/PlanSetupPath.vue'
import { usePlanBuilder, type BuiltPlan } from '@/components/plan/usePlanBuilder'
import { usePlanPick } from '@/components/plan/usePlanPick'
import { invalidateHintSettings } from '@/composables/useHintSettings'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { usePageActive, usePageRedirect } from '@/composables/usePageNavigation'
import { useToast } from '@/composables/useToast'
import { planPathMessages } from '@/plugins/planPathMessages'
import { planSetupMessages } from '@/plugins/planSetupMessages'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import type { LevelCheckStep } from '@/types/learn'
import type { LearningPlan, LevelCheckAnswer, PlanOptions, PlanSelfLevel } from '@/types/plan'
import { apiErrorStatus, hasServerMessage, resolveApiError } from '@/utils/apiError'
import { SERVER_MESSAGE } from '@/utils/serverMessages'

/**
 * The learning plan setup after sign-up (focus mode, about 3 minutes): why, the goal, the goal date (picked on a
 * calendar, or No date) with the daily time, how much Mandarin, the level check, the focus, then the plan is built
 * (with SparkBot, or from rules when it is unavailable) as a draft, revealed for the learner to confirm, and shown as a
 * road map; "Start my path" makes the draft the learner's plan. "Set up later" (× asks first) leaves for Home and
 * discards a draft. A learner who already has a plan is sent to My learning plan, where the goal and the level change.
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
  | 'saved'
  | 'skipped'

interface FooterAction {
  label: string
  run: () => void
  disabled?: boolean
  icon?: string
}

/** The steps that ask for part of the goal. */
const FORM_STEPS: Partial<Record<Step, { title: string; lead?: string; sections: Section[] }>> = {
  why: { title: 'plan.setup.whyTitle', lead: 'plan.setup.whyLead', sections: ['reason'] },
  goal: { title: 'plan.setup.goalTitle', lead: 'plan.setup.goalLead', sections: ['goals'] },
  pace: { title: 'plan.setup.paceTitle', sections: ['deadline', 'pace'] },
  focus: { title: 'plan.setup.focusTitle', lead: 'plan.setup.focusLead', sections: ['focus'] },
}

const WELCOME_STEPS = [
  { title: 'plan.setup.stepGoal', hint: 'plan.setup.stepGoalHint' },
  { title: 'plan.setup.stepLevel', hint: 'plan.setup.stepLevelHint' },
  { title: 'plan.setup.stepPlan', hint: 'plan.setup.stepPlanHint' },
] as const

/** Where the bar stands on "How much Mandarin do you know?", the last step before the level check. */
const BEFORE_CHECK = 80

/**
 * The steps up to the level check fill the bar to there, and the check carries on from it; the steps after the check
 * leave it full.
 */
const STEP_PROGRESS: Partial<Record<Step, number>> = {
  welcome: 0,
  why: 20,
  goal: 40,
  pace: 60,
  level: BEFORE_CHECK,
  check: BEFORE_CHECK,
}

const router = useRouter()
const { t, locale } = useLocalMessages({ plan: [planSummaryMessages, planSetupMessages, planPathMessages] })
const { toast, showToast } = useToast()
const options = useResource(planApi.options)
const refreshingOptions = ref(false)
let optionsRefreshSeq = 0
/**
 * Whether the learner already has a plan. The setup builds a first plan; a learner who has one (back from the plan, a
 * refresh after it was built, a bookmark) changes it on My learning plan ("Update my goal", "Retake level check"), so a
 * second setup cannot replace its goal and level from a fresh start.
 */
const existing = useResource(planApi.overview)

const step = ref<Step>('welcome')
const finished = computed(() => step.value === 'saved' || step.value === 'skipped')
const draft = ref<GoalDraft>(emptyDraft())
const editDraft = ref<GoalDraft>(emptyDraft())
const levelChoice = ref<SelfLevelChoice | null>(null)
/** The question of the level check on screen, as its `progress` event tells it; null until the first one is in. */
const checkQuestion = ref<{ answered: number; max: number } | null>(null)
/** The finished level check; null when it was skipped. */
const check = ref<{ answers: LevelCheckAnswer[]; result: NonNullable<LevelCheckStep['result']> } | null>(null)
const plan = ref<LearningPlan | null>(null)
const leaving = ref(false)
const finishing = ref<'confirm' | 'skip' | null>(null)
const exitError = ref<string | null>(null)
const exitErrorRef = ref<HTMLElement | null>(null)
const starting = computed(() => finishing.value === 'confirm')
const startError = ref<{ message: string; action: 'rebuild' | 'pickDate' | null } | null>(null)
const startErrorRef = ref<HTMLElement | null>(null)
const editing = ref(false)
const isUnmounted = useIsUnmounted()

watch(
  step,
  () => {
    exitError.value = null
    startError.value = null
  },
  { flush: 'sync' },
)

const { picked, togglePick, pickError, whenSaved } = usePlanPick(plan, () => t('plan.path.pickError'))

const builder = usePlanBuilder({
  isBuilding: () => step.value === 'building' && finishing.value === null,
  canStart: () => finishing.value === null && !finished.value,
  beforeRequest: whenSaved,
  onStart: () => {
    step.value = 'building'
  },
  onBuilt: ({ plan: built, draft: committedDraft }) => {
    if (committedDraft) draft.value = committedDraft
    plan.value = built
    step.value = 'result'
  },
  fallbackError: () => t('plan.setup.buildError'),
})
const { buildError, buildErrorStatus } = builder

/** A plan built before this visit: the overview is read once, when the setup opens. */
const hasPlanAlready = computed(() => Boolean(existing.data.value?.plan))
/** Reading the choices and the learner's plan, or on the way to the plan they already have. */
const loading = computed(() => options.loading.value || existing.loading.value || hasPlanAlready.value)
const loadError = computed(() => options.error.value ?? existing.error.value)
/** The setup's choices once the setup can be used: they are in, and the learner has no plan yet (or built it here). */
const setupOptions = computed(() => (loading.value || loadError.value !== null ? null : options.data.value))

const pageActive = usePageActive()
usePageRedirect(() => (hasPlanAlready.value ? { name: 'student-plan' } : null), pageActive)

function reloadPage(): void {
  if (options.error.value !== null) void options.reload()
  if (existing.error.value !== null) void existing.reload()
}

const selfLevel = computed<PlanSelfLevel>(() => (levelChoice.value ? SELF_LEVELS[levelChoice.value].self : 'NOT_SURE'))

const selfLevelChoices = computed<PlanChoice<SelfLevelChoice>[]>(() =>
  (Object.keys(SELF_LEVELS) as SelfLevelChoice[]).map((value) => {
    const level = SELF_LEVELS[value]
    return { value, label: t(level.label), hint: t(level.hint), icon: level.icon, tag: level.tag ? t(level.tag) : null }
  }),
)

const suggestedFocus = computed(() => {
  const skill = check.value?.result.newSkills[0]
  return skill ? FOCUS_FOR_SKILL[skill] : null
})

const formStep = computed(() => FORM_STEPS[step.value])

/**
 * The top bar names the flow: the plan (while it is built too, S1.08), or the level check while it runs; the bar alone
 * shows where the learner is.
 */
const topLabel = computed(() =>
  t(step.value === 'check' || step.value === 'checkDone' ? 'plan.check.label' : 'plan.setup.stepPlan'),
)

/**
 * One bar for the whole setup: it never goes back when the level check starts. The check's questions fill the rest of
 * the bar, half a question ahead while one is being answered, and only its result completes it.
 */
const topProgress = computed(() => {
  const question = step.value === 'check' ? checkQuestion.value : null
  if (!question) return STEP_PROGRESS[step.value] ?? 100
  const share = question.max > 0 ? Math.min((question.answered + 0.5) / question.max, 1) : 0
  return Math.min(BEFORE_CHECK + (100 - BEFORE_CHECK) * share, 99)
})

const whyReady = computed(
  () => draft.value.reason !== null && (draft.value.reason !== 'OTHER' || draft.value.reasonText.trim() !== ''),
)

/** The step's actions, only once the page knows the setup is for this learner. */
const footer = computed<{ primary: FooterAction; secondary?: FooterAction; note?: string } | null>(() => {
  if (!setupOptions.value) return null
  const back = (to: Step): FooterAction => ({ label: t('plan.common.back'), run: () => (step.value = to) })
  const continueTo = (to: Step, disabled = false): FooterAction => ({
    label: t('plan.common.continue'),
    run: () => (step.value = to),
    disabled,
  })
  switch (step.value) {
    case 'saved':
    case 'skipped':
      return { primary: { label: t('nav.home'), run: openHome } }
    case 'welcome':
      return {
        secondary: { label: t('plan.setup.setUpLater'), run: () => (leaving.value = true) },
        primary: { label: t('plan.setup.letsStart'), run: () => (step.value = 'why') },
      }
    case 'why':
      return { secondary: back('welcome'), primary: continueTo('goal', !whyReady.value) }
    case 'goal':
      return { secondary: back('why'), primary: continueTo('pace') }
    case 'pace':
      return {
        secondary: back('goal'),
        primary: continueTo(check.value ? 'checkDone' : 'level', !deadlineReady(draft.value)),
      }
    case 'level':
      return {
        secondary: { label: t('plan.common.back'), run: returnToPace },
        primary:
          levelChoice.value === 'NEW'
            ? { label: t('plan.setup.startFirstLesson'), run: skipCheck }
            : { label: t('plan.setup.startCheck'), run: startCheck, disabled: levelChoice.value === null },
      }
    case 'checkDone':
      return {
        secondary: { label: t('plan.common.back'), run: returnToPace },
        primary: continueTo('focus'),
      }
    case 'focus':
      return {
        secondary: back(check.value ? 'checkDone' : 'level'),
        primary: { label: t('plan.page.buildPlan'), run: build },
      }
    case 'result':
      return {
        secondary: { label: t('plan.setup.editGoal'), run: openEdit, icon: 'pen' },
        primary: { label: t('plan.setup.showPath'), run: () => (step.value = 'path') },
      }
    case 'path':
      return {
        secondary: back('result'),
        note: plan.value?.aiAssisted ? undefined : t('plan.path.rulesNote'),
        primary: {
          label: starting.value ? t('plan.setup.starting') : t('plan.setup.startPath'),
          run: () => finishSetup('confirm'),
          disabled: starting.value,
        },
      }
    default:
      return null
  }
})

/** Close leaves directly while loading, on load error, or once setup is done; otherwise asks before discarding. */
function requestClose(): void {
  if (starting.value || hasPlanAlready.value) return
  if (finished.value || !setupOptions.value) void openHome()
  else leaving.value = true
}

/** Save outstanding picks before confirming or discarding the draft, then open Home for the same session. */
async function finishSetup(action: 'confirm' | 'skip'): Promise<void> {
  if (finishing.value || finished.value) return
  finishing.value = action
  const version = getAuthSessionVersion()
  const stillHere = () => !isUnmounted() && version === getAuthSessionVersion()
  if (action === 'skip') exitError.value = null
  else startError.value = null
  let builtDuringSkip: BuiltPlan | null = null
  try {
    // Discard after a draft already being built has reached the server, even if that build failed.
    if (action === 'skip') {
      builtDuringSkip = await builder.abortAndAwait()
    }
    await whenSaved()
    if (!stillHere()) return
    await planApi[action]()
    if (action === 'confirm' && version === getAuthSessionVersion()) invalidateHintSettings()
    if (stillHere()) {
      leaving.value = false
      step.value = action === 'confirm' ? 'saved' : 'skipped'
      await openHome()
    }
  } catch (reason) {
    if (!stillHere()) return
    if (action === 'skip') {
      leaving.value = false
      // Never leave the learner stranded on the infinite building spinner if skip fails after aborting the build.
      if (step.value === 'building') {
        if (builtDuringSkip) {
          if (builtDuringSkip.draft) draft.value = builtDuringSkip.draft
          plan.value = builtDuringSkip.plan
          step.value = 'result'
        } else {
          step.value = plan.value ? 'result' : 'focus'
        }
      }
      exitError.value = resolveApiError(reason, t('plan.setup.skipError'))
      await nextTick()
      if (stillHere()) exitErrorRef.value?.focus()
    } else {
      startError.value = {
        message: resolveApiError(reason, t('plan.setup.startError')),
        action: startErrorAction(reason),
      }
      await nextTick()
      if (stillHere()) startErrorRef.value?.focus()
    }
  } finally {
    if (stillHere()) finishing.value = null
  }
}

async function openHome(): Promise<void> {
  if (!pageActive.value) return
  exitError.value = null
  try {
    await router.push({ name: 'student-home' })
  } catch (reason) {
    if (!isUnmounted()) exitError.value = resolveApiError(reason)
  }
}

/** Refreshes `setupOptions.today` on day rollover so passed target dates are cleared and disabled. */
async function refreshOptions(): Promise<PlanOptions | null> {
  const seq = ++optionsRefreshSeq
  const session = getAuthSessionVersion()
  refreshingOptions.value = true
  try {
    const fresh = await planApi.options()
    if (isUnmounted() || seq !== optionsRefreshSeq || session !== getAuthSessionVersion()) return null
    options.replace(fresh)
    if (editing.value && editDraft.value.deadline && deadlinePassed(editDraft.value.deadline, fresh.today)) {
      editDraft.value = { ...editDraft.value, deadline: undefined }
    }
    if (step.value === 'pace' && draft.value.deadline && deadlinePassed(draft.value.deadline, fresh.today)) {
      draft.value = { ...draft.value, deadline: undefined }
    }
    return fresh
  } catch {
    // Keep the already loaded setup options if a background day-rollover refresh fails.
    return null
  } finally {
    if (!isUnmounted() && seq === optionsRefreshSeq) refreshingOptions.value = false
  }
}

function returnToPace(): void {
  step.value = 'pace'
  void refreshOptions()
}

function backFromBuildError(): void {
  step.value = plan.value ? 'result' : 'focus'
}

/** The way on from a refused "Start my path": build again, or (the goal date has passed) pick a new date first. */
function startErrorAction(reason: unknown): 'rebuild' | 'pickDate' | null {
  if (apiErrorStatus(reason) !== 409) return null
  return hasServerMessage(reason, SERVER_MESSAGE.GOAL_DATE_PASSED) ? 'pickDate' : 'rebuild'
}

async function buildAgain(): Promise<void> {
  if (finishing.value || finished.value || step.value !== 'path') return
  const session = getAuthSessionVersion()
  startError.value = null
  const fresh = await refreshOptions()
  if (
    isUnmounted() ||
    session !== getAuthSessionVersion() ||
    finishing.value ||
    finished.value ||
    step.value !== 'path'
  ) {
    return
  }
  const today = fresh?.today ?? setupOptions.value?.today
  if (draft.value.deadline && today && deadlinePassed(draft.value.deadline, today)) {
    pickNewDate()
    return
  }
  build()
}

/** Building again with the same date would be refused, so the goal opens with no date chosen. */
function pickNewDate(): void {
  if (finishing.value || finished.value) return
  startError.value = null
  editDraft.value = { ...draft.value, deadline: undefined }
  editing.value = true
  void refreshOptions()
}

function startCheck(): void {
  check.value = null
  checkQuestion.value = null
  step.value = 'check'
}

/** "Brand new? Skip the check": start from the first lesson. */
function skipCheck(): void {
  levelChoice.value = 'NEW'
  check.value = null
  step.value = 'focus'
}

function checkProgress(answered: number, max: number): void {
  checkQuestion.value = { answered, max }
}

function checkFinished(answers: LevelCheckAnswer[], result: NonNullable<LevelCheckStep['result']>): void {
  check.value = { answers, result }
  step.value = 'checkDone'
}

function build(): void {
  const goal = goalInput(draft.value)
  const levelCheck = { selfLevel: selfLevel.value, skipped: !check.value, answers: check.value?.answers ?? [] }
  void builder.run(async () => ({ plan: await planApi.build({ goal, levelCheck, language: locale.value }) }))
}

function retryBuild(): void {
  builder.retry()
}

function openEdit(): void {
  const currentToday = setupOptions.value?.today
  const deadline =
    draft.value.deadline && currentToday && deadlinePassed(draft.value.deadline, currentToday)
      ? undefined
      : draft.value.deadline
  editDraft.value = { ...draft.value, deadline }
  editing.value = true
  void refreshOptions()
}

/** "Save and rebuild": the level check result stays; the plan is rebuilt for the new goal. */
function rebuild(): void {
  if (refreshingOptions.value || !deadlineReady(editDraft.value)) return
  const nextDraft = { ...editDraft.value }
  editing.value = false
  const goal = goalInput(nextDraft)
  void builder.run(async () => ({
    plan: await planApi.updateGoal(goal, locale.value),
    draft: nextDraft,
  }))
}

function reported(): void {
  showToast(t('aiSafety.reportSent'))
}
</script>
