<template>
  <div
    class="progress-bar"
    :class="{ success: tone === 'success' }"
    role="progressbar"
    :aria-valuenow="safeProgress"
    aria-valuemin="0"
    aria-valuemax="100"
    :aria-label="`${label}: ${safeProgress}%`"
  >
    <span :style="{ width: `${safeProgress}%` }" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { clampPercent } from '@/utils/format'

interface Props {
  progress: number
  tone?: 'primary' | 'success'
  label: string
}

const props = withDefaults(defineProps<Props>(), {
  tone: 'primary',
})

const safeProgress = computed<number>(() => clampPercent(props.progress))
</script>
