<template>
  <section class="plan-rail-block" aria-labelledby="plan-weeks-title">
    <h3 id="plan-weeks-title" class="card-title">
      {{ t('plan.side.yourWeeks', { count: plan.weeks.total }, plan.weeks.total) }}
    </h3>
    <dl class="plan-rail-rows">
      <div v-for="row in rows" :key="row.key">
        <dt>{{ row.when }}</dt>
        <dd>{{ row.what }}</dd>
      </div>
    </dl>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'
import type { LearningPlan, PlanStep } from '@/types/plan'

import { optionalClassSteps } from './usePlanPick'

/**
 * "Your 8 weeks": each Learn step with the weeks the pace gives it, the class that follows it (by name once the
 * student picked or joined it, else "your first class"), and the review weeks left before the deadline.
 */
const props = withDefaults(defineProps<{ plan: LearningPlan; picked?: number | null }>(), { picked: null })

const { t } = useLocalMessages({ plan: planMessages })

function when(from: number, to: number): string {
  return from === to ? t('plan.side.wk', { week: from }) : t('plan.side.wkRange', { from, to })
}

function learnLabel(step: PlanStep): string {
  const label = t('plan.side.unitRow', { number: step.unitNumber ?? '', title: step.title })
  return step.quickReview ? t('plan.side.quickReviewRow', { title: label }) : label
}

/** The optional classes in the order they come: a picked one takes the first place. */
const classOrder = computed(() => {
  const steps = optionalClassSteps(props.plan)
  return [...steps].sort((a, b) => Number(b.refId === props.picked) - Number(a.refId === props.picked))
})

function classLabel(step: PlanStep, slot: number): string {
  if (step.joined) return step.title
  if (props.picked !== null) return classOrder.value[slot]?.title ?? step.title
  return slot === 0 ? t('plan.side.firstClass') : t('plan.side.nextClass')
}

const rows = computed(() => {
  const list: { key: string; when: string; what: string }[] = []
  let slot = 0
  const steps = props.plan.steps
  steps.forEach((step, index) => {
    if (step.kind !== 'LEARN_UNIT') return
    const classes: string[] = []
    for (let next = index + 1; next < steps.length && steps[next]!.kind === 'CLASS'; next += 1) {
      const item = steps[next]!
      if (item.status === 'UNAVAILABLE') continue
      classes.push(classLabel(item, slot))
      if (!item.joined) slot += 1
    }
    const what = [learnLabel(step), ...classes].join(' + ')
    list.push({ key: `step-${step.position}`, when: when(step.weekFrom, step.weekTo), what })
  })
  const { content, total } = props.plan.weeks
  if (total > content) {
    list.push({ key: 'review', when: when(content + 1, total), what: t('plan.side.reviewWeeks') })
  }
  return list
})
</script>
