<template>
  <section class="focus-stage plan-wide" aria-labelledby="plan-path-title">
    <div class="plan-path-head">
      <h2 id="plan-path-title" class="focus-headline" tabindex="-1">
        {{ t('plan.path.title', { goal: goalTitle(t, plan.goal.title, plan.goal.reason) }) }}
      </h2>
      <p v-if="plan.aiAssisted" class="plan-form-note" data-testid="path-ai">
        <IconGlyph name="magic" :size="14" />{{ t('plan.path.aiChip') }}
        <PlanReportLink :plan="plan" @sent="emit('reported')" />
      </p>
    </div>
    <div class="plan-layout">
      <div class="panel plan-road-card">
        <div :inert="starting">
          <PlanPath :plan="plan" :picked="picked" @pick="emit('pick', $event)" />
        </div>
        <p v-if="pickError" class="field-error" role="alert" data-testid="pick-error">{{ pickError }}</p>
      </div>
      <aside class="panel plan-rail">
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
              <dt>{{ retakeWhen }}</dt>
              <dd>{{ t('plan.side.workingRetake') }}</dd>
            </div>
          </dl>
        </section>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planPathMessages } from '@/plugins/planPathMessages'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import type { LearningPlan } from '@/types/plan'
import { formatShortDate } from '@/utils/format'

import { goalTitle } from './planFormat'
import PlanOutcomes from './PlanOutcomes.vue'
import PlanPath from './PlanPath.vue'
import PlanReportLink from './PlanReportLink.vue'
import PlanWeeks from './PlanWeeks.vue'

const props = defineProps<{
  plan: LearningPlan
  picked: number | null
  pickError: string | null
  starting: boolean
}>()
const emit = defineEmits<{
  pick: [classId: number]
  reported: []
}>()

const { t, locale } = useLocalMessages({ plan: [planSummaryMessages, planPathMessages] })

/** When to retake the level check: at the first milestone, else in the plan's last week. */
const retakeWhen = computed(() =>
  props.plan.milestone
    ? formatShortDate(props.plan.milestone.date, locale.value)
    : t('plan.side.wk', { week: props.plan.weeks.total }),
)
</script>
