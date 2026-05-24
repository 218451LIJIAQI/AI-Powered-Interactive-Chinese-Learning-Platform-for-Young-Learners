<template>
  <div class="row">
    <div
      class="ring"
      :class="toneClass"
      :style="{ '--progress': progressValue }"
      role="progressbar"
      :aria-valuenow="progressValue"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="`${title}: ${label}`"
    >
      <span aria-hidden="true">{{ label }}</span>
    </div>
    <div class="stack">
      <strong>{{ title }}</strong>
      <span class="muted">{{ subtitle }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Circular progress indicator used on student progress cards and
 * the home dashboard hero. The `--progress` CSS custom property
 * drives the SVG fill so the visual stays in sync with the
 * displayed percentage. Pairs with a title + optional subtitle to
 * form a self-contained KPI tile.
 */
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
