<template>
  <section class="plan-side-block" aria-labelledby="plan-coverage-title" data-testid="plan-coverage">
    <h3 id="plan-coverage-title" class="plan-side-title">{{ t('plan.side.coverage') }}</h3>
    <ProgressBar
      :progress="percent"
      :label="t('plan.side.coverageLabel', { covered: covered.length, total: plan.coverage.length })"
    />
    <p class="plan-side-note">
      {{ t('plan.side.coverageLearn', { covered: learnCount, total: plan.coverage.length }) }}
    </p>
    <p v-for="item in byClass" :key="item.goal" class="plan-side-note">{{ item.text }}</p>
    <p v-if="gaps.length" class="plan-side-note">{{ t('plan.side.coverageGap', { goals: goalList(t, gaps) }) }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import ProgressBar from '@/components/common/ProgressBar.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'
import type { LearningPlan } from '@/types/plan'

import { goalList } from './planFormat'
import { GOAL_KEYS } from './planLabels'

/**
 * Goal coverage: how many of the student's goals have a matching Learn lesson or class in the plan (lesson
 * coverage, never a chance of success), which class covers the others, and what no lesson or class covers yet.
 */
const props = defineProps<{ plan: LearningPlan }>()

const { t } = useLocalMessages({ plan: planMessages })

const covered = computed(() => props.plan.coverage.filter((item) => item.learn || item.classId !== null))
const learnCount = computed(() => props.plan.coverage.filter((item) => item.learn).length)
const percent = computed(() =>
  props.plan.coverage.length ? Math.round((covered.value.length * 100) / props.plan.coverage.length) : 0,
)

const byClass = computed(() =>
  props.plan.coverage
    .filter((item) => !item.learn && item.classId !== null)
    .map((item) => {
      const joined = props.plan.steps.some(
        (step) => step.kind === 'CLASS' && step.refId === item.classId && step.joined,
      )
      const values = { goal: t(GOAL_KEYS[item.goal]), title: item.classTitle ?? '' }
      return {
        goal: item.goal,
        text: joined ? t('plan.side.coverageJoined', values) : t('plan.side.coverageClass', values),
      }
    }),
)

const gaps = computed(() =>
  props.plan.coverage.filter((item) => !item.learn && item.classId === null).map((item) => item.goal),
)
</script>
