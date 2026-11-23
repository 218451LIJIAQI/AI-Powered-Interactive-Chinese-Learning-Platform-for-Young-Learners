<template>
  <section class="plan-center" aria-labelledby="plan-building-title">
    <span class="plan-building-icon" aria-hidden="true"><IconGlyph name="magic" :size="40" /></span>
    <h2 id="plan-building-title" class="focus-headline">{{ t('plan.setup.buildingTitle') }}</h2>
    <ol class="plan-building-steps" aria-live="polite">
      <li
        v-for="(step, index) in steps"
        :key="step"
        :class="{ 'is-done': index < reached, 'is-current': index === reached }"
      >
        <span class="plan-building-mark" aria-hidden="true">
          <IconGlyph v-if="index < reached" name="check" :size="14" />
          <span v-else-if="index === reached" class="spinner is-sm" />
        </span>
        <span>{{ t(step) }}</span>
        <span class="sr-only">{{ index < reached ? t('plan.setup.buildStepDone') : '' }}</span>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planSetupMessages } from '@/plugins/planSetupMessages'

/**
 * "Building your plan…": what happens while the server builds the plan. The steps tick on while the request runs; the
 * last one waits for the answer. A learner who skipped the level check starts at the first lesson, and the second step
 * says so (S1.29).
 */
const props = withDefaults(defineProps<{ skippedCheck?: boolean }>(), { skippedCheck: false })

const STEP_MS = 900

const steps = computed(() => [
  'plan.setup.buildStepGoal',
  props.skippedCheck ? 'plan.setup.buildStepFirstLesson' : 'plan.setup.buildStepLevel',
  'plan.setup.buildStepCatalogue',
  'plan.setup.buildStepWeeks',
])

const { t } = useLocalMessages({ plan: planSetupMessages })
const reached = ref(0)
let timer: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  timer = setInterval(() => {
    reached.value += 1
    if (reached.value === steps.value.length - 1) clearInterval(timer)
  }, STEP_MS)
})

onBeforeUnmount(() => clearInterval(timer))
</script>
