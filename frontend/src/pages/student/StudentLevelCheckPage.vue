<template>
  <div class="plan-setup">
    <FocusTopBar
      :label="topLabel"
      :close-label="t('plan.retake.leaveLabel')"
      :progress="progress"
      :progress-label="t('plan.retake.title')"
      @close="close"
    />
    <h1 class="sr-only">{{ t('plan.retake.title') }}</h1>

    <div class="plan-setup-body">
      <StatePanel
        v-if="overview.loading.value"
        tone="loading"
        :title="t('plan.page.loading')"
        :message="t('plan.check.loadingHint')"
        spacious
      />
      <StatePanel
        v-else-if="overview.error.value"
        tone="error"
        icon="alert"
        :title="t('plan.page.loadError')"
        :message="overview.error.value"
        spacious
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="overview.reload">{{ t('plan.common.retry') }}</button>
        </template>
      </StatePanel>
      <StatePanel
        v-else-if="!planSummary"
        tone="empty"
        icon="route"
        :title="t('plan.retake.noPlanTitle')"
        :message="t('plan.retake.noPlanMessage')"
        spacious
      >
        <template #actions>
          <RouterLink class="btn btn-primary" :to="{ name: 'student-setup' }">{{
            t('plan.page.buildPlan')
          }}</RouterLink>
        </template>
      </StatePanel>

      <LevelCheckRunner
        v-else-if="!finished"
        :start-level="planSummary.level"
        @progress="onProgress"
        @finished="save"
      />
      <StatePanel
        v-else-if="saving"
        tone="loading"
        :title="t('plan.retake.saving')"
        :message="t('plan.check.loadingHint')"
        spacious
      />
      <StatePanel
        v-else-if="saveError"
        tone="error"
        icon="alert"
        :title="t('plan.retake.saveError')"
        :message="saveError"
        spacious
      >
        <template #actions>
          <button class="btn btn-primary" type="button" @click="retrySave">{{ t('plan.common.retry') }}</button>
        </template>
      </StatePanel>
      <LevelCheckDone
        v-else-if="result"
        :answered="result.answered"
        :known-skills="result.knownSkills"
        :new-skills="result.newSkills"
      >
        <p class="plan-retake-outcome" data-testid="retake-outcome">{{ outcome }}</p>
      </LevelCheckDone>
    </div>

    <div v-if="result" class="plan-setup-footer">
      <RouterLink class="btn btn-primary btn-lg" :to="{ name: 'student-plan' }" data-testid="back-to-plan">
        {{ t('plan.retake.backToPlan') }}
      </RouterLink>
    </div>

    <ConfirmDialog
      tone="leave"
      :open="leaving"
      :title="t('plan.retake.leaveTitle')"
      :message="t('plan.retake.leaveMessage')"
      :confirm-label="t('plan.retake.leave')"
      :cancel-label="t('plan.retake.keepGoing')"
      @confirm="leave"
      @cancel="leaving = false"
    />
  </div>
</template>

<script setup lang="ts">
import '@/styles/learn.css'
import '@/styles/plan.css'

import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { planApi } from '@/api/plan'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import LevelCheckDone from '@/components/plan/LevelCheckDone.vue'
import LevelCheckRunner from '@/components/plan/LevelCheckRunner.vue'
import { LEVEL_KEYS } from '@/components/plan/planLabels'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { planMessages } from '@/plugins/planMessages'
import type { LevelCheckAnswer, RetakeResult } from '@/types/plan'
import { resolveApiError } from '@/utils/apiError'

/**
 * Retaking the level check from My learning plan (focus mode). It starts at the plan's level; the server grades the
 * answers again and, when the level changed, rebuilds the plan for the same goal (progress is kept).
 */
const router = useRouter()
const { t, locale } = useLocalMessages({ plan: planMessages })
const overview = useResource(() => planApi.overview())

const planSummary = computed(() => overview.data.value?.plan ?? null)
const answered = ref(0)
const max = ref(6)
const finished = ref(false)
const answers = ref<LevelCheckAnswer[]>([])
const saving = ref(false)
const saveError = ref<string | null>(null)
const result = ref<RetakeResult | null>(null)
const leaving = ref(false)

const topLabel = computed(() =>
  finished.value
    ? t('plan.check.topDone')
    : t('plan.check.topCheck', { count: Math.min(answered.value + 1, max.value), max: max.value }),
)
const progress = computed(() => (finished.value ? 100 : Math.round((answered.value / Math.max(1, max.value)) * 100)))

const outcome = computed(() => {
  const value = result.value
  if (!value) return ''
  const level = t(LEVEL_KEYS[value.level])
  if (value.changed) return t('plan.retake.changed', { level })
  return value.headStart ? t('plan.retake.sameHeadStart', { level }) : t('plan.retake.same', { level })
})

function onProgress(count: number, total: number): void {
  answered.value = count
  max.value = total
}

async function save(sent: LevelCheckAnswer[]): Promise<void> {
  finished.value = true
  answers.value = sent
  const start = planSummary.value?.level
  if (!start) return
  saving.value = true
  saveError.value = null
  try {
    result.value = await planApi.retake(start, sent, locale.value)
  } catch (reason) {
    saveError.value = resolveApiError(reason, t('plan.retake.saveError'))
  } finally {
    saving.value = false
  }
}

function retrySave(): void {
  void save(answers.value)
}

function close(): void {
  if (result.value || !planSummary.value) {
    leave()
    return
  }
  leaving.value = true
}

function leave(): void {
  leaving.value = false
  void router.push({ name: 'student-plan' })
}
</script>
