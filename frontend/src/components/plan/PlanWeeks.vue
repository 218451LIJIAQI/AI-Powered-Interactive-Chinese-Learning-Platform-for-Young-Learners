<template>
  <section class="plan-rail-block" aria-labelledby="plan-weeks-title">
    <h3 id="plan-weeks-title" class="card-title">{{ heading }}</h3>
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
import { planPathMessages } from '@/plugins/planPathMessages'
import { planSetupMessages } from '@/plugins/planSetupMessages'
import type { LearningPlan, PlanStep } from '@/types/plan'

import { paceInSentence, unitsWord } from './planFormat'
import { bestAfterUnit, optionalClassesInOrder } from './usePlanPick'

/**
 * "Your 9 weeks": each Learn step with the calendar weeks it takes, the class that follows it (by name once the learner
 * picked or joined it, else "your first class"), and the review weeks left before the date. A named class that is best
 * taken after that row's unit comes after it ("Unit 4, then Shopping and Prices", as its card says "Best after Unit 4");
 * other classes run alongside ("Unit 4 + Travel Mandarin"). Without a date the weeks follow the daily time ("Your weeks
 * at 1–2 hours a day"); with a first milestone they count to it ("Your first 2 weeks") and what comes after it is
 * "Later".
 */
const props = defineProps<{ plan: LearningPlan; picked: number | null }>()

const { t } = useLocalMessages({ plan: [planSetupMessages, planPathMessages] })

const heading = computed(() => {
  const { total } = props.plan.weeks
  if (props.plan.milestone) return t('plan.side.firstWeeks', { count: total }, total)
  if (!props.plan.goal.deadline) return t('plan.side.weeksAtPace', { pace: paceInSentence(t, props.plan.pace.key) })
  return t('plan.side.yourWeeks', { count: total }, total)
})

function when(from: number, to: number): string {
  return from === to ? t('plan.side.wk', { week: from }) : t('plan.side.wkRange', { from, to })
}

function learnLabel(step: PlanStep): string {
  const label =
    step.unitNumber === null ? step.title : t('plan.side.unitRow', { number: step.unitNumber, title: step.title })
  return step.quickReview ? t('plan.side.quickReviewRow', { title: label }) : label
}

const classOrder = computed(() => optionalClassesInOrder(props.plan, props.picked))

/** The optional class a slot names once the learner picked the order; before that the slots are unnamed. */
function namedOptional(step: PlanStep, slot: number): PlanStep | null {
  if (step.joined || props.picked === null) return null
  return classOrder.value[slot] ?? step
}

/**
 * A row per Learn step within the plan's weeks, with the classes that follow it; the steps after the last week share
 * one "Later" row. `slot` numbers the classes not joined yet.
 */
const rows = computed(() => {
  const { content, total } = props.plan.weeks
  const list: { key: string; when: string; what: string; unit: number | null }[] = []
  const laterUnits: number[] = []
  const laterClasses: string[] = []
  let slot = 0
  let inLater = false
  for (const step of props.plan.steps) {
    if (step.status === 'UNAVAILABLE') continue
    if (step.kind === 'LEARN_UNIT') {
      inLater = step.weekFrom > total
      if (inLater) {
        if (step.unitNumber !== null) laterUnits.push(step.unitNumber)
        continue
      }
      list.push({
        key: `step-${step.position}`,
        when: when(step.weekFrom, Math.min(step.weekTo, total)),
        what: learnLabel(step),
        unit: step.unitNumber,
      })
      continue
    }
    const named = namedOptional(step, slot)
    const label =
      (step.joined ? step.title : named?.title) ?? t(slot === 0 ? 'plan.side.firstClass' : 'plan.side.nextClass')
    if (!step.joined) slot += 1
    const row = list.at(-1)
    // Optional classes come after a first milestone, even when anchored to an in-window Learn unit.
    if (inLater || step.weekFrom > total || (props.plan.milestone !== null && !step.joined)) {
      laterClasses.push(label)
    } else if (row) {
      const after = named !== null && row.unit !== null && bestAfterUnit(props.plan, named) === row.unit
      row.what = after ? t('plan.side.thenClass', { first: row.what, second: label }) : `${row.what} + ${label}`
    } else {
      // A plan with no Learn unit left is its classes alone.
      list.push({
        key: `class-${step.position}`,
        when: when(step.weekFrom, Math.min(step.weekTo, total)),
        what: label,
        unit: null,
      })
    }
  }
  if (laterUnits.length || laterClasses.length) {
    const units = laterUnits.length ? unitsWord(t, laterUnits) : ''
    list.push({
      key: 'later',
      when: t('plan.side.later'),
      what: [units, ...laterClasses].filter(Boolean).join(' + '),
      unit: null,
    })
  } else if (total > content) {
    list.push({ key: 'review', when: when(content + 1, total), what: t('plan.side.reviewWeeks'), unit: null })
  }
  return list
})
</script>
