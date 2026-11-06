<template>
  <footer class="focus-footer" :class="tone ? `is-${tone}` : undefined">
    <div class="focus-footer-inner">
      <div class="focus-footer-start"><slot name="start" /></div>
      <p v-if="$slots.default" class="focus-footer-note"><slot /></p>
      <span v-else aria-hidden="true" />
      <div class="focus-footer-end"><slot name="end" /></div>
    </div>
  </footer>
</template>

<script setup lang="ts">
/**
 * The footer of every focus flow (setup, level check, lesson player, review, class activity and quiz): a rule, then a
 * split row of large buttons, [Back / Skip] in `start` and [Continue / Check] in `end`, with an optional short note in
 * the middle. `tone` turns it into the result band shown after an answer or at the end of an activity.
 */
withDefaults(defineProps<{ tone?: 'correct' | 'wrong' | 'done' | null }>(), { tone: null })
</script>
