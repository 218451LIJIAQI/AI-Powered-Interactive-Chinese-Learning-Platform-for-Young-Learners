<template>
  <span class="status-pill" :class="toneClass">{{ label }}</span>
</template>

<script setup lang="ts">
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
