<template>
  <svg class="mini-chart" :viewBox="viewBox" preserveAspectRatio="none" role="img" :aria-label="ariaLabel">
    <!-- The chart stretches to its card (preserveAspectRatio="none"); non-scaling strokes keep line widths even and
         the points round (each point is a zero-length round-capped segment, not a circle that would stretch). -->
    <!-- The baseline (the series minimum) as a hairline: the only guide the chart draws. -->
    <line
      x1="0"
      :y1="Y_MIN"
      :x2="SVG_WIDTH"
      :y2="Y_MIN"
      stroke="currentColor"
      stroke-width="1"
      opacity="0.2"
      vector-effect="non-scaling-stroke"
    />
    <polyline
      :points="polylinePoints"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      vector-effect="non-scaling-stroke"
    />
    <path
      v-for="point in points"
      :key="point.key"
      :d="`M${point.x} ${point.y}h0`"
      stroke="currentColor"
      :stroke-width="POINT_DIAMETER"
      stroke-linecap="round"
      vector-effect="non-scaling-stroke"
    />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { AnalyticsTrendPoint } from '@/types/api'

interface Props {
  values: AnalyticsTrendPoint[]
  label: string
}

const props = defineProps<Props>()

const POINT_DIAMETER = 7
const SVG_HEIGHT = 160
const SVG_WIDTH = 320
const Y_MAX = 50
const Y_MIN = 130

const viewBox = `0 0 ${SVG_WIDTH} ${SVG_HEIGHT}`

const points = computed(() => {
  const numericValues = props.values.map((item) => (Number.isFinite(item.value) ? item.value : 0))
  const max = Math.max(1, ...numericValues)
  const min = Math.min(...numericValues)
  const spread = Math.max(max - min, 1)
  const count = props.values.length

  return props.values.map((item, index) => {
    const x = count === 1 ? SVG_WIDTH / 2 : (SVG_WIDTH / (count - 1)) * index
    const normalized = (numericValues[index] - min) / spread
    const y = Y_MIN - normalized * (Y_MIN - Y_MAX)
    return { key: `${item.label}-${index}`, x, y }
  })
})

// Language-neutral and carries the data, so screen readers get the values in every locale.
const ariaLabel = computed<string>(
  () => `${props.label}: ${props.values.map((point) => `${point.label} ${point.value}`).join(', ')}`,
)
const polylinePoints = computed<string>(() => points.value.map((p) => `${p.x},${p.y}`).join(' '))
</script>
