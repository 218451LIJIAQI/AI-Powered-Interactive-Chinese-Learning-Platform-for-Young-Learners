<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    count?: number
    palette?: string[]
  }>(),
  {
    count: 8,
  },
)

const defaultPalette = ['\u2728', '\u2b50', '\ud83c\udf1f', '\ud83d\udcab', '\ud83c\udf88']

function rand(min: number, max: number) {
  return Math.random() * (max - min) + min
}

const sparkles = computed(() => {
  const palette = props.palette && props.palette.length ? props.palette : defaultPalette
  return Array.from({ length: props.count }).map((_, idx) => ({
    id: idx,
    emoji: palette[idx % palette.length],
    top: `${rand(2, 90)}%`,
    left: `${rand(2, 96)}%`,
    fontSize: `${rand(0.9, 1.8)}rem`,
    delay: `${rand(0, 3)}s`,
    duration: `${rand(3.6, 5.4)}s`,
  }))
})
</script>

<template>
  <div class="kid-sparkles" aria-hidden="true">
    <span
      v-for="item in sparkles"
      :key="item.id"
      class="kid-sparkle"
      :style="{
        top: item.top,
        left: item.left,
        fontSize: item.fontSize,
        animationDelay: item.delay,
        animationDuration: item.duration,
      }"
    >
      {{ item.emoji }}
    </span>
  </div>
</template>
