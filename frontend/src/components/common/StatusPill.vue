<template>
  <span class="status-pill" :class="[tone, { 'has-icon': icon }]">
    <IconGlyph v-if="icon" :name="icon" :size="14" aria-hidden="true" />
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import IconGlyph from './IconGlyph.vue'

/**
 * A status badge: the text carries the meaning, the tone reinforces it. `strong` = needs YOUR action now, `success` /
 * `info` / `neutral` = a normal or positive state, `warning` / `danger` = something is wrong, `outline` = inactive or
 * not yet (Draft, Not started, Cancelled, Archived).
 */
export type PillTone = 'strong' | 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'outline'

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
