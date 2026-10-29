<template>
  <section class="plan-side-block" aria-labelledby="plan-weeks-title">
    <h3 id="plan-weeks-title" class="plan-side-title">{{ t('plan.side.yourWeeks') }}</h3>
    <dl class="plan-weeks">
      <div v-for="row in rows" :key="row.key">
        <dt>{{ row.when }}</dt>
        <dd>{{ row.what }}</dd>
      </div>
    </dl>
    <p v-for="row in optionalRows" :key="row" class="plan-side-note">{{ row }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'
import type { LearningPlan } from '@/types/plan'

/**
 * "Your weeks": the Learn steps by the weeks the pace gives them (steps sharing weeks share a row), the review weeks
 * left before the deadline, and the optional classes with their weeks.
 */
const props = defineProps<{ plan: LearningPlan }>()

const { t } = useLocalMessages({ plan: planMessages })

function when(from: number, to: number): string {
  return from === to ? t('plan.side.wk', { week: from }) : t('plan.side.wkRange', { from, to })
}

const rows = computed(() => {
  const grouped = new Map<string, { from: number; to: number; titles: string[] }>()
  for (const step of props.plan.steps) {
    if (step.kind !== 'LEARN_UNIT') continue
    const key = `${step.weekFrom}-${step.weekTo}`
    const entry = grouped.get(key) ?? { from: step.weekFrom, to: step.weekTo, titles: [] }
    entry.titles.push(step.quickReview ? t('plan.side.quickReviewRow', { title: step.title }) : step.title)
    grouped.set(key, entry)
  }
  const list = [...grouped.entries()].map(([key, entry]) => ({
    key,
    when: when(entry.from, entry.to),
    what: entry.titles.join(', '),
  }))
  const { content, total } = props.plan.weeks
  if (total > content) {
    list.push({ key: 'review', when: when(content + 1, total), what: t('plan.side.reviewWeeks') })
  }
  return list
})

const optionalRows = computed(() =>
  props.plan.steps
    .filter((step) => step.kind === 'CLASS' && !step.joined && step.status !== 'UNAVAILABLE')
    .map((step) => t('plan.side.optionalRow', { title: step.title, from: step.weekFrom, to: step.weekTo })),
)
</script>
