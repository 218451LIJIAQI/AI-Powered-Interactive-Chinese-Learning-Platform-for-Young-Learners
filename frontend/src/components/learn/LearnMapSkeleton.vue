<template>
  <!-- The Learn map while the path loads: the same ground and road, flat discs and label bars, nothing to press. -->
  <div class="learn-map is-loading" :style="{ height: `${height}px` }" aria-hidden="true">
    <div class="learn-map-ground">
      <svg class="learn-map-road" :height="height">
        <path class="learn-map-road-edge" :d="road" />
        <path class="learn-map-road-surface" :d="road" />
        <path class="learn-map-road-dashes" :d="road" />
      </svg>
    </div>
    <div
      v-for="(point, index) in points"
      :key="index"
      class="learn-level-placeholder"
      :style="{ '--x': `${point.x}px`, '--y': `${point.y}px` }"
    >
      <span class="skeleton learn-level-placeholder-disc" />
      <span class="skeleton learn-level-placeholder-label" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { levelPoint, mapHeight, roadPath } from './learnMap'

/** Six levels: what fits the column at a laptop window. */
const LEVELS = 6

const height = mapHeight(LEVELS)
const road = roadPath(LEVELS)
const points = Array.from({ length: LEVELS }, (_, index) => levelPoint(index))
</script>
