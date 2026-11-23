<template>
  <div class="plan-setup">
    <FocusTopBar
      :label="t('plan.check.label')"
      :close-label="t('plan.retake.leaveLabel')"
      :close-disabled="saving"
      :progress="progress"
      :progress-label="t('plan.retake.title')"
      @close="close"
    />
    <h1 class="sr-only">{{ t('plan.retake.title') }}</h1>

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
      icon="alert"
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
      v-else-if="!finished"
      :start-level="planSummary.level"
      :paused="leaving"
      @progress="onProgress"
      @finished="save"
    />
    <StatePanel
      v-else-if="saving"
      heading-tag="h2"
      tone="loading"
      :title="t('plan.retake.saving')"
      :message="t('plan.check.loadingHint')"
      spacious
    />
    <StatePanel
      v-else-if="saveError"
      heading-tag="h2"
      tone="error"
      icon="alert"
      :title="t('plan.retake.saveError')"
      :message="saveError"
      spacious
      @retry="save(answers)"
    />
    <LevelCheckDone v-else-if="outcome" :title="outcome.title" :lead="outcome.lead" />

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

import { computed, ref } from 'vue'
import { RouterLink, useRouter, type RouteLocationRaw } from 'vue-router'

import { getAuthSessionVersion } from '@/api/http'
import { planApi } from '@/api/plan'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusFooter from '@/components/layout/FocusFooter.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import LevelCheckDone from '@/components/plan/LevelCheckDone.vue'
import LevelCheckRunner from '@/components/plan/LevelCheckRunner.vue'
import { ladderRung, LEVEL_KEYS } from '@/components/plan/planLabels'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { invalidateHintSettings } from '@/composables/useHintSettings'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { planSetupMessages } from '@/plugins/planSetupMessages'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import type { LevelCheckAnswer, RetakeResult } from '@/types/plan'
import { resolveApiError } from '@/utils/apiError'
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

const planSummary = computed(() => overview.data.value?.plan ?? null)
const answered = ref(0)
const max = ref(8)
const answers = ref<LevelCheckAnswer[]>([])
const saving = ref(false)
const saveError = ref<string | null>(null)
const result = ref<RetakeResult | null>(null)
/** The questions are over once their answers are sent: saving, failed to save, or saved. */
const finished = computed(() => saving.value || saveError.value !== null || result.value !== null)
const leaving = ref(false)
const isUnmounted = useIsUnmounted()

const progress = computed(() => {
  if (!planSummary.value) return null
  return finished.value ? 100 : ratioPercent(answered.value + 0.5, max.value)
})

/**
 * The result screen. The plan on this page is the one loaded before the retake, so its level and target are the ones
 * to compare with.
 */
const outcome = computed(() => {
  const value = result.value
  const before = planSummary.value
  if (!value || !before) return null
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
  result.value?.changed ? { name: 'student-plan', query: { updated: 'level' } } : { name: 'student-plan' },
)

function onProgress(count: number, total: number): void {
  answered.value = count
  max.value = total
}

async function save(sent: LevelCheckAnswer[]): Promise<void> {
  if (saving.value) return
  const version = getAuthSessionVersion()
  const stillCurrent = () => !isUnmounted() && version === getAuthSessionVersion()
  leaving.value = false
  answers.value = sent
  saving.value = true
  saveError.value = null
  try {
    const saved = await planApi.retake(sent, locale.value)
    if (saved.changed && version === getAuthSessionVersion()) invalidateHintSettings()
    if (!stillCurrent()) return
    result.value = saved
  } catch (reason) {
    if (stillCurrent()) {
      saveError.value = resolveApiError(reason, t('plan.retake.saveError'))
    }
  } finally {
    if (stillCurrent()) saving.value = false
  }
}

/** Once the server has saved the result, closing takes the learner back to the plan. */
function close(): void {
  if (saving.value) return
  if (result.value || !planSummary.value) void router.push(planRoute.value)
  else leaving.value = true
}

function leave(): void {
  if (saving.value) return
  leaving.value = false
  void router.push({ name: 'student-plan' })
}
</script>
