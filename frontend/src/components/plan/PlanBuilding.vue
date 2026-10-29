<template>
  <section class="plan-building" aria-labelledby="plan-building-title" data-testid="plan-building">
    <span class="plan-building-icon" aria-hidden="true"><IconGlyph name="magic" :size="28" /></span>
    <h2 id="plan-building-title" class="plan-building-title">{{ t('plan.setup.buildingTitle') }}</h2>
    <ol class="plan-building-steps" aria-live="polite">
      <li
        v-for="(step, index) in STEPS"
        :key="step"
        :class="{ 'is-done': index < reached, 'is-current': index === reached }"
      >
        <span class="plan-building-mark" aria-hidden="true">
          <IconGlyph v-if="index < reached" name="check" :size="14" />
          <span v-else-if="index === reached" class="spinner is-sm" />
        </span>
        <span>{{ t(step) }}</span>
        <span class="sr-only">{{ index < reached ? t('plan.stepStatus.DONE') : '' }}</span>
      </li>
    </ol>
    <p class="plan-building-note">{{ t('plan.setup.buildingNote') }}</p>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'

/**
 * "Building your plan…": what happens while the server builds the plan (SparkBot reads the goal, the level comes
 * from the check, only real lessons and classes are matched). The steps tick on while the request runs; the last
 * one waits for the answer.
 */
const STEPS = [
  'plan.setup.buildStepGoal',
  'plan.setup.buildStepLevel',
  'plan.setup.buildStepCatalogue',
  'plan.setup.buildStepWeeks',
] as const
const STEP_MS = 900

const { t } = useLocalMessages({ plan: planMessages })
const reached = ref(0)
let timer: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  timer = setInterval(() => {
    if (reached.value < STEPS.length - 1) reached.value += 1
  }, STEP_MS)
})

onBeforeUnmount(() => clearInterval(timer))
</script>
