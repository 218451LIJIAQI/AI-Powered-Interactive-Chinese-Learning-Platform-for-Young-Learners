<template>
  <p v-if="text" class="ai-notice-line" data-testid="ai-notice">
    <IconGlyph name="info" :size="14" />
    <span>{{ text }}</span>
  </p>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'

/** The admin's AI notice (Admin AI settings), shown with every AI answer. */
defineProps<{ text: string }>()
</script>

<style scoped>
/* Scoped: Admin AI settings previews the notice without loading ai.css (same look as .ai-footnote there). */
.ai-notice-line {
  display: flex;
  align-items: flex-start;
  gap: var(--space-1-5);
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
}

.ai-notice-line .icon-glyph {
  margin-top: var(--space-0-5);
}
</style>
