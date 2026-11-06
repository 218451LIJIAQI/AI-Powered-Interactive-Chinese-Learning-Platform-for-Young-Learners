<template>
  <div class="practice-meter">
    <ol class="practice-dots" :class="{ 'is-squares': squares }" :aria-label="listLabel">
      <li v-for="(dot, position) in dots" :key="position" :class="`is-${dot}`" />
    </ol>
    <span class="practice-counter" aria-live="polite">{{ label }}</span>
  </div>
</template>

<script setup lang="ts">
/**
 * A stage's progress row: one mark per card, question or stroke on the left, its count in words on the right. A
 * current mark is larger, a seen or done one filled.
 */
export type PracticeDot = 'ahead' | 'current' | 'seen'

withDefaults(
  defineProps<{
    dots: readonly PracticeDot[]
    label: string
    /** Accessible name of the marks. */
    listLabel: string
    /** Rounded squares instead of dots, for the strokes of a character. */
    squares?: boolean
  }>(),
  { squares: false },
)
</script>
