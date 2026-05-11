<script setup lang="ts">
import { computed } from 'vue'

import KidCounter from './KidCounter.vue'

const props = defineProps<{
  label: string
  value: string | number
  emoji: string
  accent?: 'sun' | 'sea' | 'berry' | 'lime' | 'plum'
}>()

/**
 * Detect a numeric value (or a numeric+suffix like "62%") so KidCounter can
 * animate the numeric portion. Strings such as "Beginner" are rendered as-is.
 */
const parsed = computed(() => {
  if (typeof props.value === 'number') {
    return { isNumber: true, numeric: props.value, suffix: '' }
  }
  const text = String(props.value)
  const match = /^(-?\d+(?:\.\d+)?)([^\d]*)$/.exec(text.trim())
  if (match) {
    return { isNumber: true, numeric: Number(match[1]), suffix: match[2] }
  }
  return { isNumber: false, numeric: 0, suffix: '' }
})
</script>

<template>
  <el-card
    class="metric-card metric-card--student"
    :class="`metric-card--${accent ?? 'sun'}`"
    shadow="hover"
  >
    <span class="kid-stat-icon" aria-hidden="true">{{ emoji }}</span>
    <p>{{ label }}</p>
    <strong>
      <KidCounter v-if="parsed.isNumber" :value="parsed.numeric" :suffix="parsed.suffix" />
      <template v-else>{{ value }}</template>
    </strong>
  </el-card>
</template>
