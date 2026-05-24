<template>
  <span class="status-pill" :class="tone">{{ label }}</span>
</template>

<script setup lang="ts">
/**
 * Small coloured pill used everywhere a record needs a quick
 * status indicator (lesson state, payment state, teacher status,
 * AI report state, etc.). The five tones map to the design-token
 * palette in main.css so the look stays consistent across every
 * surface that renders a pill.
 */
type PillTone = 'success' | 'warning' | 'danger' | 'info' | 'primary'

interface Props {
  label: string
  tone?: PillTone
}

withDefaults(defineProps<Props>(), {
  tone: 'primary',
})
</script>
