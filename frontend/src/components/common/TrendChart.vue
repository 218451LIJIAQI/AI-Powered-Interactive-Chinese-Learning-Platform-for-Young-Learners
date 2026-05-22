<template>
  <svg class="mini-chart" viewBox="0 0 320 160" preserveAspectRatio="none">
    <path
      d="M0 130 Q80 126 160 108 T320 78"
      fill="none"
      :stroke="stroke"
      stroke-linecap="round"
      stroke-linejoin="round"
      stroke-width="4"
      opacity="0.12"
    />
    <polyline
      :points="polylinePoints"
      fill="none"
      :stroke="stroke"
      stroke-width="4"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle
      v-for="point in points"
      :key="point.x"
      :cx="point.x"
      :cy="point.y"
      r="4.5"
      :fill="stroke"
      opacity="0.92"
    />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { AnalyticsTrendPoint } from '@/types/api'

const props = withDefaults(
  defineProps<{
    values: AnalyticsTrendPoint[]
    stroke?: string
  }>(),
  {
    stroke: '#6a49ff',
  },
)

const points = computed(() => {
  if (props.values.length === 0) return []

  const max = Math.max(...props.values.map((item) => item.value), 1)
  const min = Math.min(...props.values.map((item) => item.value))
  const spread = Math.max(max - min, 1)

  return props.values.map((item, index) => {
    const x = props.values.length === 1 ? 160 : (320 / (props.values.length - 1)) * index
    const normalized = (item.value - min) / spread
    const y = 130 - normalized * 80
    return { x, y }
  })
})

const polylinePoints = computed(() => points.value.map((point) => `${point.x},${point.y}`).join(' '))
</script>
