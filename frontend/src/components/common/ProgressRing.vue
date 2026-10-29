<template>
  <div class="progress-ring">
    <div
      class="ring"
      :style="ringStyle"
      role="progressbar"
      :aria-valuenow="progressValue"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="ariaLabel"
    >
      <span aria-hidden="true">{{ label }}</span>
    </div>
    <div class="progress-ring-copy">
      <strong>{{ title }}</strong>
      <span v-if="subtitle" class="muted">{{ subtitle }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type StyleValue } from 'vue'

import { clampPercent } from '@/utils/format'

/** A 56px progress dial with its title and subtitle beside it. */
interface Props {
  progress: number
  title: string
  subtitle?: string
}

const props = withDefaults(defineProps<Props>(), {
  subtitle: '',
})

const progressValue = computed<number>(() => clampPercent(props.progress))
const label = computed<string>(() => `${progressValue.value}%`)
const ariaLabel = computed<string>(() => `${props.title}: ${label.value}`)
const ringStyle = computed<StyleValue>(() => ({ '--progress': progressValue.value }))
</script>
