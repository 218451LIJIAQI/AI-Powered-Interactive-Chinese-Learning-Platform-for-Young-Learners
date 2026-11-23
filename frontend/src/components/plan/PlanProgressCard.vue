<template>
  <section
    class="panel plan-strip"
    :aria-labelledby="resource.loading.value || resource.error.value ? undefined : 'plan-strip-title'"
    data-testid="plan-card"
  >
    <StatePanel
      v-if="resource.loading.value"
      heading-tag="h2"
      tone="loading"
      :title="t('plan.page.loading')"
      :message="t('plan.page.loadingHint')"
    />
    <StatePanel
      v-else-if="resource.error.value"
      heading-tag="h2"
      tone="error"
      icon="alert"
      :title="t('plan.page.loadError')"
      :message="resource.error.value"
    >
      <template #actions>
        <button class="btn btn-secondary btn-sm" type="button" @click="resource.reload">
          {{ t('common.retry') }}
        </button>
      </template>
    </StatePanel>

    <template v-else-if="summary">
      <span class="plan-icon-tile is-lg" aria-hidden="true"><IconGlyph name="target" :size="28" /></span>
      <div class="plan-strip-goal">
        <h2 id="plan-strip-title" class="page-eyebrow">{{ t('plan.page.title') }}</h2>
        <p class="plan-strip-title">{{ goalTitle(t, summary.goalTitle, summary.reason) }}</p>
        <p class="type-caption" data-testid="plan-card-when">{{ whenLine }}</p>
      </div>
      <div class="plan-strip-level">
        <p class="page-eyebrow">{{ t('plan.page.mandarinLevel') }}</p>
        <p class="plan-strip-value">{{ levelSpan(t, summary.level, summary.targetLevel) }}</p>
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
      <RouterLink class="btn btn-primary" :to="{ name: 'student-setup' }">{{ t('plan.page.buildPlan') }}</RouterLink>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { planApi } from '@/api/plan'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import { formatWeekdayLongDate } from '@/utils/format'

import { goalTitle, levelSpan, planExpired } from './planFormat'

/**
 * The strip at the top of Progress: where the learner is heading (the goal, its date and its week), the Mandarin level
 * from the level check, plan progress and the way to the plan; or "Build your learning plan".
 */
const { t, locale } = useLocalMessages({ plan: planSummaryMessages })
const resource = useResource(planApi.overview)

const summary = computed(() => resource.data.value?.plan ?? null)

const ended = computed(() => {
  const value = summary.value
  return value !== null && planExpired(value.status, value.deadline, value.today)
})

/** "by Sat 18 Jul 2026 · week 2 of 10", the first milestone's date, an ended date, or the week alone without a date. */
const whenLine = computed(() => {
  const value = summary.value
  if (!value) return ''
  if (ended.value && value.deadline) {
    return t('plan.cards.wasDue', { date: formatWeekdayLongDate(value.deadline, locale.value) })
  }
  const weeks = { week: value.week, total: value.totalWeeks }
  if (value.milestoneDate) {
    return t('plan.cards.milestoneWeek', { date: formatWeekdayLongDate(value.milestoneDate, locale.value), ...weeks })
  }
  if (value.deadline) {
    return t('plan.cards.byWeek', { date: formatWeekdayLongDate(value.deadline, locale.value), ...weeks })
  }
  return t('plan.cards.weekOnly', weeks)
})

const statusLine = computed(() => {
  const value = summary.value
  if (!value) return ''
  if (ended.value) return t('plan.cards.planEnded')
  return value.status === 'ON_TRACK' ? t('plan.cards.onTrackWeek') : t(`plan.status.${value.status}`)
})
</script>

<style scoped>
.plan-strip {
  display: grid;
  grid-template-columns: auto minmax(0, 1.2fr) minmax(0, 1fr) minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-6);
  border-radius: var(--radius-2xl);
}

/* Loading and error take the whole strip. */
.plan-strip > :deep(.state-panel) {
  grid-column: 1 / -1;
}

.plan-strip .type-caption {
  margin: 0;
}

.plan-strip-goal,
.plan-strip-level {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.plan-strip-progress {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.plan-strip-title {
  margin: 0;
  font-size: var(--type-h2-size);
  font-weight: var(--font-weight-heading);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.plan-strip-value {
  margin: 0;
  font-size: var(--type-card-title-size);
  font-weight: var(--font-weight-heading);
  line-height: 1.375;
}

.plan-strip-build {
  grid-column: 2 / span 3;
}

@media (max-width: 75rem) {
  .plan-strip {
    grid-template-columns: auto minmax(0, 1fr) minmax(0, 1fr) auto;
  }

  .plan-strip-level {
    grid-column: 2;
    grid-row: 2;
  }

  .plan-strip-progress {
    grid-column: 3 / -1;
    grid-row: 2;
  }

  .plan-strip-goal,
  .plan-strip-build {
    grid-column: 2 / 4;
  }
}

@media (max-width: 48rem) {
  .plan-strip {
    grid-template-columns: auto minmax(0, 1fr);
    gap: var(--space-4);
  }

  .plan-strip-goal,
  .plan-strip-build {
    grid-column: 2;
  }

  .plan-strip-level,
  .plan-strip-progress {
    grid-column: 1 / -1;
    grid-row: auto;
  }

  .plan-strip > .btn {
    grid-column: 1 / -1;
    justify-self: start;
  }
}
</style>
