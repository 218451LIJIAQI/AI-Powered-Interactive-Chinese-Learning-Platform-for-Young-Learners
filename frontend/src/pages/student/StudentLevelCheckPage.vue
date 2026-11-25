<template>
  <div ref="stageRef" class="plan-setup">
    <FocusTopBar
      :label="t('plan.check.label')"
      :close-label="t('plan.retake.leaveLabel')"
      :close-disabled="phase.kind === 'saving'"
      :progress="progress"
      :progress-label="t('plan.retake.title')"
      @close="close"
    />
    <h1 class="sr-only">{{ t('plan.retake.title') }}</h1>
    <p class="sr-only" role="status">{{ statusAnnouncement }}</p>

    <StatePanel
      v-if="overview.loading.value"
      heading-tag="h2"
      tone="loading"
      :title="t('plan.page.loading')"
      :message="t('plan.check.loadingHint')"
      spacious
    />
    <StatePanel
      v-else-if="overview.error.value"
      heading-tag="h2"
      tone="error"
      :title="t('plan.page.loadError')"
      :message="overview.error.value"
      spacious
      @retry="overview.reload"
    />
    <StatePanel
      v-else-if="!planSummary"
      heading-tag="h2"
      tone="empty"
      icon="route"
      :title="t('plan.retake.noPlanTitle')"
      :message="t('plan.retake.noPlanMessage')"
      spacious
    >
      <template #actions>
        <RouterLink class="btn btn-primary" :to="{ name: 'student-setup' }">{{ t('plan.page.buildPlan') }}</RouterLink>
      </template>
    </StatePanel>

    <LevelCheckRunner
      v-else-if="phase.kind === 'checking'"
      :key="runKey"
      :start-level="planSummary.level"
      :paused="leaving"
      @progress="onProgress"
      @finished="(sent, _result, token) => save(sent, token)"
    />
    <StatePanel
      v-else-if="phase.kind === 'saving'"
      heading-tag="h2"
      tone="loading"
      :title="t('plan.retake.saving')"
      :message="t('plan.check.loadingHint')"
      spacious
    />
    <StatePanel
      v-else-if="phase.kind === 'error'"
      heading-tag="h2"
      tone="error"
      :title="t('plan.retake.saveError')"
      :message="phase.message"
      spacious
    >
      <template #actions>
        <button
          v-if="mustRestartCheck"
          class="btn btn-primary"
          type="button"
          data-testid="retake-restart"
          @click="restartCheck"
        >
          {{ t('plan.retake.startAgain') }}
        </button>
        <button v-else class="btn btn-primary" type="button" @click="save(phase.answers, phase.runToken)">
          {{ t('common.retry') }}
        </button>
      </template>
    </StatePanel>
    <section v-else-if="outcome" class="plan-center" data-testid="level-check-done">
      <h2 ref="doneHeading" class="focus-headline" tabindex="-1">{{ outcome.title }}</h2>
      <p v-if="outcome.lead" class="plan-center-lead" data-testid="level-check-lead">{{ outcome.lead }}</p>
    </section>

    <FocusFooter v-if="outcome">
      <template v-if="outcome.targetReached" #start>
        <RouterLink
          class="btn btn-secondary btn-lg"
          :to="{ name: 'student-plan', query: { edit: 'goal' } }"
          data-testid="retake-new-goal"
        >
          {{ t('plan.retake.setNewGoal') }}
        </RouterLink>
      </template>
      <template #end>
        <RouterLink class="btn btn-primary btn-lg" :to="planRoute" data-testid="back-to-plan">
          {{ t('plan.common.continue') }}
        </RouterLink>
      </template>
    </FocusFooter>

    <ConfirmDialog
      :open="leaving"
      :title="t('plan.retake.leaveTitle')"
      :message="planSummary ? t('plan.retake.leaveMessage', { level: t(LEVEL_KEYS[planSummary.level]) }) : ''"
      :confirm-label="t('plan.retake.leave')"
      :cancel-label="t('plan.retake.keepGoing')"
      @confirm="leave"
      @cancel="leaving = false"
    />
  </div>
</template>

<script setup lang="ts">
import '@/styles/learn-shared.css'
import '@/styles/plan-setup.css'

import { computed, ref, shallowRef, watch } from 'vue'
import { RouterLink, useRouter, type RouteLocationRaw } from 'vue-router'

import { getAuthSessionVersion } from '@/api/http'
import { planApi } from '@/api/plan'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import LevelCheckRunner from '@/components/plan/LevelCheckRunner.vue'
import { ladderRung, LEVEL_KEYS } from '@/components/plan/planLabels'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { invalidateHintSettings } from '@/composables/useHintSettings'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { planSetupMessages } from '@/plugins/planSetupMessages'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import type { LevelCheckAnswer } from '@/types/learn'
import type { RetakeResult } from '@/types/plan'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { restoreFocusIfDropped } from '@/utils/focus'
import { ratioPercent } from '@/utils/format'

/**
 * Retaking the level check from My learning plan (focus mode). It starts at the plan's level; the server grades the
 * answers again and, when the level changed, rebuilds the plan for the same goal (progress is kept). Leaving midway
 * asks first: the answers of this try are not saved and the level stays. The result leads with the level reached and
 * what it means for the plan; back on the plan a changed level is confirmed.
 */
const router = useRouter()
const { t, locale } = useLocalMessages({ plan: [planSummaryMessages, planSetupMessages] })
const overview = useResource(planApi.overview)

type RetakePhase =
  | { kind: 'checking' }
  | { kind: 'saving' }
  | { kind: 'error'; answers: LevelCheckAnswer[]; runToken: string | null; message: string; status: number | null }
  | { kind: 'done'; result: RetakeResult }

const planSummary = computed(() => overview.data.value?.plan ?? null)
const answered = ref(0)
const max = ref(8)
const runKey = ref(0)
const phase = shallowRef<RetakePhase>({ kind: 'checking' })
const stageRef = ref<HTMLElement | null>(null)
const doneHeading = ref<HTMLElement | null>(null)
/** Expired runToken (400) or concurrent plan change (409) cannot succeed by resending the same answers. */
const mustRestartCheck = computed(
  () => phase.value.kind === 'error' && (phase.value.status === 400 || phase.value.status === 409),
)
const leaving = ref(false)
const isUnmounted = useIsUnmounted()

const progress = computed(() => {
  if (overview.loading.value || overview.error.value || !planSummary.value) return null
  return phase.value.kind !== 'checking' ? 100 : ratioPercent(answered.value + 0.5, max.value)
})

/**
 * The result screen. The plan on this page is the one loaded before the retake, so its level and target are the ones
 * to compare with.
 */
const outcome = computed(() => {
  const before = planSummary.value
  if (phase.value.kind !== 'done' || !before) return null
  const value = phase.value.result
  const level = t(LEVEL_KEYS[value.level])
  const rung = ladderRung(value.level)
  const previous = ladderRung(before.level)
  if (rung === previous) {
    return {
      title: t('plan.retake.sameTitle', { level }),
      lead: value.changed ? t('plan.retake.changedLead') : t('plan.retake.sameLead'),
      targetReached: false,
    }
  }
  const targetReached = rung > previous && rung >= ladderRung(before.targetLevel)
  return {
    title: t(rung > previous ? 'plan.retake.upTitle' : 'plan.retake.downTitle', { level }),
    lead: targetReached ? t('plan.retake.targetLead') : t('plan.retake.changedLead'),
    targetReached,
  }
})

/** Back to the plan, which confirms a new level. */
const planRoute = computed<RouteLocationRaw>(() =>
  phase.value.kind === 'done' && phase.value.result.changed
    ? { name: 'student-plan', query: { updated: 'level' } }
    : { name: 'student-plan' },
)

const statusAnnouncement = computed(() => {
  if (outcome.value) return [outcome.value.title, outcome.value.lead].filter(Boolean).join(' ')
  return phase.value.kind === 'error' ? phase.value.message : ''
})

watch(
  phase,
  (next) => {
    if (next.kind === 'done' && outcome.value) {
      void restoreFocusIfDropped(
        () => doneHeading.value,
        () => !isUnmounted(),
      )
    } else if (next.kind === 'error') {
      void restoreFocusIfDropped(
        () => stageRef.value?.querySelector<HTMLElement>('.state-panel button'),
        () => !isUnmounted(),
      )
    }
  },
  { flush: 'post' },
)

function onProgress(count: number, total: number): void {
  answered.value = count
  max.value = total
}

function restartCheck(): void {
  const shouldReloadPlan = phase.value.kind === 'error' && phase.value.status === 409
  phase.value = { kind: 'checking' }
  answered.value = 0
  runKey.value += 1
  if (shouldReloadPlan) void overview.reload()
}

async function save(sent: LevelCheckAnswer[], token: string | null = null): Promise<void> {
  if (phase.value.kind === 'saving') return
  const version = getAuthSessionVersion()
  const stillCurrent = () => !isUnmounted() && version === getAuthSessionVersion()
  leaving.value = false
  phase.value = { kind: 'saving' }
  try {
    const saved = await planApi.retake(sent, locale.value, token)
    if (saved.changed && version === getAuthSessionVersion()) invalidateHintSettings()
    if (!stillCurrent()) return
    phase.value = { kind: 'done', result: saved }
  } catch (reason) {
    if (stillCurrent()) {
      phase.value = {
        kind: 'error',
        answers: sent,
        runToken: token,
        message: resolveApiError(reason, t('plan.retake.saveError')),
        status: apiErrorStatus(reason),
      }
    }
  }
}

/** Once the server has saved the result, closing takes the learner back to the plan. */
function close(): void {
  if (phase.value.kind === 'saving') return
  if (phase.value.kind === 'done' || !planSummary.value) void router.push(planRoute.value)
  else leaving.value = true
}

function leave(): void {
  if (phase.value.kind === 'saving') return
  leaving.value = false
  void router.push({ name: 'student-plan' })
}
</script>
