<template>
  <section class="panel plan-strip" aria-labelledby="plan-strip-title" data-testid="plan-card">
    <StatePanel
      v-if="resource.loading.value"
      tone="loading"
      :title="t('plan.page.loading')"
      :message="t('plan.page.loadingHint')"
    />
    <StatePanel
      v-else-if="resource.error.value"
      tone="error"
      icon="alert"
      :title="t('plan.page.loadError')"
      :message="resource.error.value"
    >
      <template #actions>
        <button class="btn btn-secondary btn-sm" type="button" @click="resource.reload">
          {{ t('plan.common.retry') }}
        </button>
      </template>
    </StatePanel>

    <template v-else-if="summary">
      <span class="plan-icon-tile is-lg" aria-hidden="true"><IconGlyph name="target" :size="28" /></span>
      <div class="plan-strip-goal">
        <h2 id="plan-strip-title" class="page-eyebrow">{{ t('plan.cards.myPlan') }}</h2>
        <p class="plan-strip-title">{{ title }}</p>
        <p class="type-caption">
          {{
            summary.deadline
              ? t('plan.cards.byWeek', {
                  date: shortDayDate(summary.deadline),
                  week: summary.week,
                  total: summary.totalWeeks,
                })
              : t('plan.cards.weekOnly', { week: summary.week, total: summary.totalWeeks })
          }}
        </p>
      </div>
      <div class="plan-strip-level">
        <p class="page-eyebrow">{{ t('plan.page.mandarinLevel') }}</p>
        <p class="plan-strip-value">{{ level }}</p>
      </div>
      <div class="plan-strip-progress">
        <p class="page-eyebrow">{{ t('plan.cards.planProgress', { percent: summary.percent }) }}</p>
        <ProgressBar class="is-sm" :progress="summary.percent" :label="t('plan.page.planProgress')" />
        <p class="type-caption">{{ statusLine }}</p>
      </div>
      <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'student-plan' }">
        {{ t('plan.cards.viewPlan') }}
      </RouterLink>
    </template>

    <template v-else>
      <span class="plan-icon-tile is-lg" aria-hidden="true"><IconGlyph name="magic" :size="28" /></span>
      <div class="plan-strip-goal plan-strip-build">
        <h2 id="plan-strip-title" class="plan-strip-title">{{ t('plan.cards.buildTitle') }}</h2>
        <p class="type-caption">{{ t('plan.cards.buildText') }}</p>
      </div>
      <RouterLink class="btn btn-primary" :to="{ name: 'student-setup' }">{{ t('plan.cards.buildPlan') }}</RouterLink>
    </template>
  </section>
</template>

<script setup lang="ts">
import '@/styles/plan.css'

import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { planApi } from '@/api/plan'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { planMessages } from '@/plugins/planMessages'

import { goalTitle, shortDayDate } from './planFormat'
import { LEVEL_KEYS, STATUS_KEYS } from './planLabels'

/**
 * The strip at the top of Progress: where the student is heading (the goal and its week), the Mandarin level from the
 * level check, plan progress and the way to the plan; or "Build your learning plan".
 */
const { t } = useLocalMessages({ plan: planMessages })
const resource = useResource(() => planApi.overview())

const summary = computed(() => resource.data.value?.plan ?? null)
const title = computed(() => (summary.value ? goalTitle(t, summary.value.goalTitle, summary.value.reason) : ''))

const level = computed(() => {
  const value = summary.value
  if (!value) return ''
  return value.level === value.targetLevel
    ? t(LEVEL_KEYS[value.level])
    : t('plan.level.fromTo', { from: t(LEVEL_KEYS[value.level]), to: t(LEVEL_KEYS[value.targetLevel]) })
})

const statusLine = computed(() => {
  const value = summary.value
  if (!value) return ''
  return value.status === 'ON_TRACK' ? t('plan.cards.onTrackWeek') : t(STATUS_KEYS[value.status])
})
</script>
