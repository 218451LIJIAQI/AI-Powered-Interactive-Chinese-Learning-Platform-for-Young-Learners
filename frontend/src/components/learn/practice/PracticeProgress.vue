<template>
  <p class="practice-counter" aria-live="polite" data-testid="practice-counter">{{ label }}</p>
</template>

<script setup lang="ts">
/**
 * Where the learner is in a stage, once and as text, at its top left: "Card 4 of 10", "Question 1 of 5", "2 of 4
 * paired". No dots or squares repeat it.
 */
defineProps<{ label: string }>()
</script>
