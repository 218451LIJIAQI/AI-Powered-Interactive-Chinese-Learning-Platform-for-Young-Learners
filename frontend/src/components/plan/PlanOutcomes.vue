<template>
  <section class="plan-rail-block" :aria-labelledby="headingId" data-testid="plan-outcomes">
    <component :is="headingTag" :id="headingId" class="card-title">{{ heading }}</component>
    <ul class="plan-icon-list">
      <li v-for="(item, index) in items" :key="`${index}-${item}`"><IconGlyph name="check" :size="16" />{{ item }}</li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planPathMessages } from '@/plugins/planPathMessages'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import type { LearningPlan } from '@/types/plan'
import { formatWeekdayDate } from '@/utils/format'

import { LEVEL_CODE_KEYS } from './planLabels'

/**
 * What the plan leads to: SparkBot's list, or the plan's goals when it was built from rules. "By the end" of a plan
 * with a date, "By Mon 1 Jun" of a first milestone, "At HSK 2" of a plan without a date.
 */
const props = withDefaults(defineProps<{ plan: LearningPlan; headingTag?: 'h2' | 'h3' }>(), { headingTag: 'h3' })

const { t, locale } = useLocalMessages({ plan: [planSummaryMessages, planPathMessages] })
const headingId = `${useId()}-title`

const heading = computed(() => {
  const { milestone, goal, level } = props.plan
  if (milestone) return t('plan.side.byDate', { date: formatWeekdayDate(milestone.date, locale.value) })
  if (!goal.deadline) return t('plan.side.atLevel', { level: t(LEVEL_CODE_KEYS[level.target]) })
  return t('plan.side.byTheEnd')
})

const items = computed(() =>
  props.plan.outcomes.length ? props.plan.outcomes : props.plan.goal.goals.map((goal) => t(`plan.outcome.${goal}`)),
)
</script>
