<template>
  <section class="plan-center" aria-live="polite" data-testid="level-check-done">
    <h2 class="focus-headline">{{ title }}</h2>
    <p v-if="lead" class="plan-center-lead" data-testid="level-check-lead">{{ lead }}</p>
  </section>
</template>

<script setup lang="ts">
/**
 * The end of a level check, whose headline is the result: the setup's "Your starting level: HSK 1 (Basic)", or a
 * retake's outcome ("You're still at HSK 1 (Basic)") with what it means for the plan. How the answers are scored stays
 * out of the learner's way.
 */
defineProps<{
  title: string
  /** One line under the headline. */
  lead?: string
}>()
</script>
