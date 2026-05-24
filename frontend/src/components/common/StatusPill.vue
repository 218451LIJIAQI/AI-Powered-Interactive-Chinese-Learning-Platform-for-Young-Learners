<template>
  <span class="status-pill" :class="toneClass">{{ label }}</span>
</template>

<script setup lang="ts">
/**
 * Small coloured pill used everywhere a record needs a quick
 * status indicator (lesson state, payment state, teacher status,
 * AI report state, etc.). The five tones map to the design-token
 * palette in main.css so the look stays consistent across every
 * surface that renders a pill.
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    label: string
    tone?: 'success' | 'warning' | 'danger' | 'info' | 'primary'
  }>(),
  {
    tone: 'primary',
  },
)

const toneClass = computed(() => props.tone)
</script>
