<template>
  <div
    class="progress-bar"
    :class="toneClass"
    role="progressbar"
    :aria-valuenow="safeProgress"
    aria-valuemin="0"
    aria-valuemax="100"
    :aria-label="ariaLabel"
  >
    <span :style="fillStyle" />
  </div>
</template>

<script setup lang="ts">
import { computed, type StyleValue } from 'vue'

type BarTone = 'primary' | 'success' | 'warning' | 'info'

interface Props {
  progress: number
  tone?: BarTone
  label?: string
}

const props = withDefaults(defineProps<Props>(), {
  tone: 'primary',
  label: 'Progress',
})

function clampProgress(value: number): number {
  return Number.isFinite(value) ? Math.round(Math.max(0, Math.min(100, value))) : 0
}

const safeProgress = computed<number>(() => clampProgress(props.progress))
const fillStyle = computed<StyleValue>(() => ({ width: `${safeProgress.value}%` }))
const toneClass = computed<string>(() => (props.tone === 'primary' ? '' : props.tone))
const ariaLabel = computed<string>(() => `${props.label}: ${safeProgress.value}%`)
</script>
