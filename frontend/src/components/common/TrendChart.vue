<template>
  <svg class="mini-chart" :viewBox="viewBox" preserveAspectRatio="none" role="img" :aria-label="ariaLabel">
    <path
      :d="backgroundPath"
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
      :key="point.key"
      :cx="point.x"
      :cy="point.y"
      :r="POINT_RADIUS"
      :fill="stroke"
      opacity="0.92"
    />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { AnalyticsTrendPoint } from '@/types/api'

interface ChartPoint {
  readonly key: string
  readonly x: number
  readonly y: number
}

interface Props {
  values: AnalyticsTrendPoint[]
  stroke?: string
  label?: string
}

const props = withDefaults(defineProps<Props>(), {
  label: 'Trend chart',
  stroke: '#6a49ff',
})

const POINT_RADIUS = 4.5
const SVG_HEIGHT = 160
const SVG_WIDTH = 320
const Y_MAX = 50
const Y_MIN = 130

const backgroundPath = `M0 ${Y_MIN} Q80 126 160 108 T${SVG_WIDTH} 78`
const viewBox = `0 0 ${SVG_WIDTH} ${SVG_HEIGHT}`

const points = computed<ChartPoint[]>(() => {
  if (props.values.length === 0) return []

  const numericValues = props.values.map((item) => (Number.isFinite(item.value) ? item.value : 0))
  let max = -Infinity
  let min = Infinity
  for (const value of numericValues) {
    if (value > max) max = value
    if (value < min) min = value
  }
  if (max < 1) max = 1
  const spread = Math.max(max - min, 1)
  const count = props.values.length

  return props.values.map((item, index) => {
    const x = count === 1 ? SVG_WIDTH / 2 : (SVG_WIDTH / (count - 1)) * index
    const normalized = (numericValues[index] - min) / spread
    const y = Y_MIN - normalized * (Y_MIN - Y_MAX)
    return { key: `${item.label}-${index}`, x, y }
  })
})

const ariaLabel = computed<string>(() => `${props.label}: ${props.values.length} points`)
const polylinePoints = computed<string>(() => points.value.map((p) => `${p.x},${p.y}`).join(' '))
</script>
