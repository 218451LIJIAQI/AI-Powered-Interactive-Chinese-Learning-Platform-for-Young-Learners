<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    /** Target value. If a string is passed (e.g. "Beginner"), it is rendered as-is. */
    value: number | string
    /** Animation duration in milliseconds. */
    duration?: number
    /** Optional suffix (e.g. "%") rendered after the number. */
    suffix?: string
  }>(),
  {
    duration: 900,
    suffix: '',
  },
)

const display = ref<string>('')
let frame = 0
let startTs = 0
let startValue = 0
let endValue = 0

function easeOutCubic(t: number) {
  return 1 - Math.pow(1 - t, 3)
}

function step(now: number) {
  if (!startTs) startTs = now
  const elapsed = now - startTs
  const t = Math.min(elapsed / props.duration, 1)
  const eased = easeOutCubic(t)
  const current = Math.round(startValue + (endValue - startValue) * eased)
  display.value = `${current}${props.suffix}`
  if (t < 1) {
    frame = requestAnimationFrame(step)
  }
}

function animateTo(next: number) {
  cancelAnimationFrame(frame)
  startTs = 0
  // Parse current numeric value from display (without suffix)
  const numericPart = display.value.replace(props.suffix ?? '', '')
  startValue = Number.isFinite(Number(numericPart)) ? Number(numericPart) : 0
  endValue = next
  // If the user prefers reduced motion, jump straight to the target.
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reduce) {
    display.value = `${endValue}${props.suffix}`
    return
  }
  frame = requestAnimationFrame(step)
}

watch(
  () => props.value,
  (next) => {
    if (typeof next === 'number') {
      animateTo(next)
    } else {
      display.value = String(next)
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
})
</script>

<template>
  <span class="kid-counter">{{ display }}</span>
</template>
