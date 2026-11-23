<template>
  <footer class="focus-footer" :class="tone ? `is-${tone}` : undefined">
    <div class="focus-footer-inner" :class="{ 'has-middle': $slots.middle }">
      <div class="focus-footer-start"><slot name="start" /></div>
      <div v-if="$slots.middle" class="focus-footer-middle"><slot name="middle" /></div>
      <p v-else-if="$slots.default" class="focus-footer-note"><slot /></p>
      <span v-else aria-hidden="true" />
      <div class="focus-footer-end"><slot name="end" /></div>
    </div>
  </footer>
</template>

<script setup lang="ts">
/**
 * The footer of every focus flow (setup, level check, lesson player, review, class activity and quiz): a rule, then a
 * split row of large buttons, [Back / Skip] in `start` and [Continue / Check] in `end`, with an optional short note in
 * the middle. `middle` takes a block instead of the note (the hint of a lesson question: it appears here, so the
 * question above never moves). `tone` turns the footer into the result band shown after an answer or at the end of an
 * activity.
 */
defineProps<{ tone?: 'correct' | 'wrong' | 'done' | null }>()
</script>
