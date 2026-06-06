<template>
  <span class="status-pill" :class="tone">{{ label }}</span>
</template>

<script setup lang="ts">
type PillTone = 'success' | 'warning' | 'danger' | 'info' | 'primary'

interface Props {
  label: string
  tone?: PillTone
}

withDefaults(defineProps<Props>(), {
  tone: 'primary',
})
</script>
