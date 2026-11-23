<template>
  <div class="plan-daily-goal" data-testid="plan-daily-goal">
    <FilterChips
      :model-value="current"
      :options="options"
      :label="t('common.dailyGoal')"
      size="sm"
      :disabled="saving || current === null"
      @update:model-value="choose"
    />
    <span v-if="current !== null" class="plan-daily-goal-lessons">
      {{ t('plan.result.dailyGoalLessons', { count: lessons }, lessons) }}
    </span>
    <p v-if="error || resource.error.value" class="field-error" role="alert">
      {{ error ?? resource.error.value }}
      <button v-if="resource.error.value" class="btn btn-link btn-sm" type="button" @click="resource.reload">
        {{ t('common.retry') }}
      </button>
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { getAuthSessionVersion } from '@/api/http'
import { xpApi } from '@/api/learn'
import FilterChips, { type FilterChipOption } from '@/components/common/FilterChips.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import { notifyProgressChanged } from '@/composables/useProgressRefresh'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import { resolveApiError } from '@/utils/apiError'
import { DAILY_GOALS } from '@/utils/xpLevel'

/**
 * The daily goal on the plan result (S1.09): 10, 20 or 30 XP a day, the learner's current goal picked (20 for a new
 * account), and how many lessons it means (a Learn lesson pays 10 XP). It is an account setting, so a pick saves at
 * once, as it does on Home and the Account page; the time a day only sets the plan's weeks.
 */
const emit = defineEmits<{ saved: [message: string] }>()

/** XP a Learn lesson pays. */
const LESSON_XP = 10

const { t } = useLocalMessages({ plan: planSummaryMessages })
const resource = useResource(xpApi.dailyGoal)
const saving = ref(false)
const error = ref<string | null>(null)
const isUnmounted = useIsUnmounted()

const current = computed(() => resource.data.value?.dailyGoalXp ?? null)
const lessons = computed(() => Math.max(1, Math.round((current.value ?? 0) / LESSON_XP)))
const options = computed<FilterChipOption<number | null>[]>(() =>
  DAILY_GOALS.map((xp) => ({ value: xp, label: t('plan.result.dailyGoalOption', { xp }), testId: `daily-goal-${xp}` })),
)

async function choose(xp: number | null): Promise<void> {
  if (xp === null || saving.value || xp === current.value) return
  const session = getAuthSessionVersion()
  saving.value = true
  error.value = null
  try {
    const saved = await xpApi.setDailyGoal(xp)
    if (session !== getAuthSessionVersion()) return
    notifyProgressChanged()
    if (isUnmounted()) return
    resource.replace(saved)
    emit('saved', t('plan.result.dailyGoalSaved', { xp: saved.dailyGoalXp }))
  } catch (reason) {
    if (isUnmounted() || session !== getAuthSessionVersion()) return
    error.value = resolveApiError(reason, t('plan.result.dailyGoalError'))
  } finally {
    if (!isUnmounted()) saving.value = false
  }
}
</script>
