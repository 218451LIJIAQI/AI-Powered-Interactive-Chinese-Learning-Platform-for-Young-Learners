<template>
  <div class="admin-insights-plot">
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
        v-if="points.length > 1"
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
      >
        <title>{{ point.axisLabel }}</title>
      </path>
    </svg>
    <ol class="admin-insights-axis" aria-hidden="true">
      <li
        v-for="point in points"
        :key="point.key"
        :class="{ 'is-quiet': point.quiet }"
        :style="{ left: `${point.ratio * 100}%` }"
      >
        {{ point.axisLabel }}
      </li>
    </ol>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface TrendPoint {
  readonly key: string
  readonly label: string
  readonly value: number
  readonly axisLabel: string
  readonly quiet?: boolean
}

const props = defineProps<{
  values: readonly TrendPoint[]
  label: string
}>()

const POINT_DIAMETER = 7
const SVG_HEIGHT = 160
const SVG_WIDTH = 320
/** Where the series maximum and minimum are drawn (SVG y grows downwards, so the maximum has the smaller y). */
const Y_MAX = 50
const Y_MIN = 130

const viewBox = `0 0 ${SVG_WIDTH} ${SVG_HEIGHT}`

/** Horizontal position (0–1) shared by both the SVG plot and the X-axis labels beneath it. */
function pointRatio(index: number, count: number): number {
  return count <= 1 ? 0.5 : index / (count - 1)
}

const points = computed(() => {
  if (!props.values.length) return []
  const values = props.values.map((item) => item.value)
  const min = Math.min(...values)
  const span = Math.max(...values) - min
  const spread = span > 0 ? span : 1
  const count = values.length

  return props.values.map((item, index) => {
    const ratio = pointRatio(index, count)
    const normalized = (item.value - min) / spread
    const y = Y_MIN - normalized * (Y_MIN - Y_MAX)
    return {
      key: item.key,
      ratio,
      x: ratio * SVG_WIDTH,
      y,
      axisLabel: item.axisLabel,
      quiet: Boolean(item.quiet),
    }
  })
})

// Language-neutral and carries the data, so screen readers get the values in every locale.
const ariaLabel = computed<string>(() =>
  props.values.length
    ? `${props.label}: ${props.values.map((point) => `${point.label} ${point.value}`).join(', ')}`
    : props.label,
)
const polylinePoints = computed<string>(() => points.value.map((p) => `${p.x},${p.y}`).join(' '))
</script>
