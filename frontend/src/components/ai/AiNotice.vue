<template>
  <p v-if="text" class="ai-notice-line" data-testid="ai-notice">
    <IconGlyph name="info" :size="14" />
    <span>
      {{ text }}
      <RouterLink v-if="guidance" class="ai-notice-link" :to="{ name: 'responsible-ai' }">{{
        t('aiHelper.responsibleAi')
      }}</RouterLink>
    </span>
  </p>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'

/** The admin's AI notice (Admin AI settings), once on every AI screen; `guidance` adds the Responsible AI link. */
defineProps<{ text: string; guidance?: boolean }>()

const { t } = useLocalMessages({ aiHelper: aiHelperMessages })
</script>

<style scoped>
/* Scoped: Admin AI settings previews the notice without loading ai.css. */
.ai-notice-line {
  display: flex;
  align-items: flex-start;
  gap: var(--space-1-5);
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: 18px;
}

.ai-notice-line .icon-glyph {
  margin-top: var(--space-0-5);
}

.ai-notice-link {
  color: var(--text-primary);
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
}

.ai-notice-link:hover {
  color: var(--text-link-hover);
  text-decoration: underline;
}
</style>
