<template>
  <div class="progress-bar" :class="toneClass">
    <span :style="{ width: `${safeProgress}%` }" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    progress: number
    tone?: 'primary' | 'success' | 'warning' | 'info'
  }>(),
  {
    tone: 'primary',
  },
)

const safeProgress = computed(() => Math.max(0, Math.min(100, props.progress)))
const toneClass = computed(() => (props.tone === 'primary' ? '' : props.tone))
</script>
