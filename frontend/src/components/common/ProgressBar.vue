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
    <span :style="{ width: `${safeProgress}%` }" />
  </div>
</template>

<script setup lang="ts">
/**
 * Horizontal progress bar used on student dashboards, lesson cards,
 * and admin analytics. Clamps the input to `[0, 100]` so callers can
 * pass raw percentage math without worrying about overflow, and
 * exposes ARIA `role="progressbar"` + value attributes for assistive
 * tech.
 */
import { computed } from 'vue'

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

const safeProgress = computed<number>(() => Math.round(Math.max(0, Math.min(100, props.progress))))
const toneClass = computed<string>(() => (props.tone === 'primary' ? '' : props.tone))
const ariaLabel = computed<string>(() => `${props.label}: ${safeProgress.value}%`)
</script>
