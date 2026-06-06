<template>
  <div class="row">
    <div
      class="ring"
      :class="toneClass"
      :style="ringStyle"
      role="progressbar"
      :aria-valuenow="progressValue"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="ariaLabel"
    >
      <span aria-hidden="true">{{ label }}</span>
    </div>
    <div class="stack">
      <strong>{{ title }}</strong>
      <span v-if="subtitle" class="muted">{{ subtitle }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type StyleValue } from 'vue'

type RingTone = 'primary' | 'success'

interface Props {
  progress: number
  title: string
  subtitle?: string
  tone?: RingTone
}

const props = withDefaults(defineProps<Props>(), {
  subtitle: '',
  tone: 'primary',
})

function clampProgress(value: number): number {
  return Number.isFinite(value) ? Math.round(Math.max(0, Math.min(100, value))) : 0
}

const progressValue = computed<number>(() => clampProgress(props.progress))
const label = computed<string>(() => `${progressValue.value}%`)
const ariaLabel = computed<string>(() => `${props.title}: ${label.value}`)
const ringStyle = computed<StyleValue>(() => ({ '--progress': progressValue.value }))
const toneClass = computed<string>(() => (props.tone === 'success' ? 'success' : ''))
</script>
