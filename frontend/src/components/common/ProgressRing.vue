<template>
  <div class="row">
    <div class="ring" :class="toneClass" :style="{ '--progress': progressValue }">
      {{ label }}
    </div>
    <div class="stack">
      <strong>{{ title }}</strong>
      <span class="muted">{{ subtitle }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    progress: number
    title: string
    subtitle?: string
    tone?: 'primary' | 'success'
  }>(),
  {
    subtitle: '',
    tone: 'primary',
  },
)

const progressValue = computed(() => Math.max(0, Math.min(100, props.progress)))
const label = computed(() => `${Math.round(progressValue.value)}%`)
const toneClass = computed(() => (props.tone === 'success' ? 'success' : ''))
</script>
