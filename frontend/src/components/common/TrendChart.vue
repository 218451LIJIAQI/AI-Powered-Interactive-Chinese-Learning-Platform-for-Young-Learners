<template>
  <svg
    class="mini-chart"
    :viewBox="viewBox"
    preserveAspectRatio="none"
    role="img"
    :aria-label="ariaLabel"
    :style="{ color: stroke }"
  >
    <!-- The chart stretches to its card (preserveAspectRatio="none"); non-scaling strokes keep line widths even and
         the points round (each point is a zero-length round-capped segment, not a circle that would stretch). -->
    <path
      :d="backgroundPath"
      fill="none"
      stroke="currentColor"
      stroke-linecap="round"
      stroke-linejoin="round"
      stroke-width="4"
      opacity="0.12"
      vector-effect="non-scaling-stroke"
    />
    <polyline
      :points="polylinePoints"
      fill="none"
      stroke="currentColor"
      stroke-width="3"
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

interface ChartPoint {
  readonly key: string
  readonly x: number
  readonly y: number
}

interface Props {
  values: AnalyticsTrendPoint[]
  stroke?: string
  label: string
}

const props = withDefaults(defineProps<Props>(), {
  /** Any CSS colour, including a token such as var(--color-success). */
  stroke: 'var(--color-primary-500)',
})

const POINT_DIAMETER = 9
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

// Language-neutral and carries the data, so screen readers get the values in every locale.
const ariaLabel = computed<string>(
  () => `${props.label}: ${props.values.map((point) => `${point.label} ${point.value}`).join(', ')}`,
)
const polylinePoints = computed<string>(() => points.value.map((p) => `${p.x},${p.y}`).join(' '))
</script>
