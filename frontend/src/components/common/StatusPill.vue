<template>
  <span class="status-pill" :class="[tone, { 'has-icon': icon }]">
    <IconGlyph v-if="icon" :name="icon" :size="14" aria-hidden="true" />
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import IconGlyph from './IconGlyph.vue'

/** Semantic status label: the text carries the meaning, the tone colour reinforces it. */
export type PillTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral'

interface Props {
  label: string
  tone?: PillTone
  /** Optional icon shown before the label (replaces the default status dot). */
  icon?: string
}

withDefaults(defineProps<Props>(), {
  tone: 'info',
  icon: '',
})
</script>
