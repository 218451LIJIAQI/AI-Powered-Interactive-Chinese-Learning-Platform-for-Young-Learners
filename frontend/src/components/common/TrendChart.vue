<template>
  <svg class="mini-chart" viewBox="0 0 320 160" preserveAspectRatio="none" role="img" aria-hidden="true">
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
/**
 * Inline sparkline chart used on every persona's dashboard hero and
 * the admin analytics surface. Drawn as a simple SVG polyline so
 * there is no external charting library dependency (which would
 * dwarf the rest of the component bundle).
 *
 * The chart normalises the value range itself so callers don't
 * have to pre-process the analytics response — pass the trend
 * points and the chart figures out the y-axis scaling.
 */
import { computed } from 'vue'

import type { AnalyticsTrendPoint } from '@/types/api'

interface ChartPoint {
  readonly x: number
  readonly y: number
}

interface Props {
  values: AnalyticsTrendPoint[]
  stroke?: string
}

const props = withDefaults(defineProps<Props>(), {
  stroke: '#6a49ff',
})

const SVG_WIDTH = 320
const SVG_PADDING_TOP = 50
const SVG_PADDING_BOTTOM = 130

const points = computed<ChartPoint[]>(() => {
  const vals = props.values
  if (vals.length === 0) return []

  let max = -Infinity
  let min = Infinity
  for (const item of vals) {
    if (item.value > max) max = item.value
    if (item.value < min) min = item.value
  }
  if (max < 1) max = 1
  const spread = Math.max(max - min, 1)
  const count = vals.length

  return vals.map((item, index) => {
    const x = count === 1 ? SVG_WIDTH / 2 : (SVG_WIDTH / (count - 1)) * index
    const normalized = (item.value - min) / spread
    const y = SVG_PADDING_BOTTOM - normalized * (SVG_PADDING_BOTTOM - SVG_PADDING_TOP)
    return { x, y }
  })
})

const polylinePoints = computed<string>(() =>
  points.value.map((p) => `${p.x},${p.y}`).join(' '),
)
</script>
