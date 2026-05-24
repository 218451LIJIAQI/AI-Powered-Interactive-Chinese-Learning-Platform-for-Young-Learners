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
/**
 * Single KPI/metric tile used across every persona dashboard:
 * label on top, big numeric value, supporting subtitle below, and
 * an IconGlyph badge in the corner. Kept presentational —
 * the parent page computes the values and passes them in.
 */
import IconGlyph from './IconGlyph.vue'

interface Props {
  label: string
  value: string | number
  subtitle: string
  icon: string
}

defineProps<Props>()
</script>
