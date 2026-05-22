<template>
  <article class="metric-card">
    <div class="metric-top">
      <div>
        <div class="metric-label">{{ label }}</div>
        <div class="metric-value">{{ value }}</div>
      </div>
      <div class="page-title-icon metric-card-icon">
        <IconGlyph :name="icon" :size="22" />
      </div>
    </div>
    <div class="metric-change">{{ subtitle }}</div>
  </article>
</template>

<script setup lang="ts">
import IconGlyph from './IconGlyph.vue'

defineProps<{
  label: string
  value: string
  subtitle: string
  icon: string
}>()
</script>
