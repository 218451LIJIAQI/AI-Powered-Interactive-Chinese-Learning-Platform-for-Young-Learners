<template>
  <p class="ai-notice-line">
    <span>{{ text }}</span>
    <button
      class="ai-notice-link"
      type="button"
      aria-haspopup="dialog"
      data-testid="ai-responsible-link"
      @click="guidanceOpen = true"
    >
      <IconGlyph name="info" :size="14" />{{ t('aiSafety.responsibleAi') }}
    </button>
  </p>
  <AiResponsibleAiDrawer v-if="guidanceOpen" :live-video="liveVideo" @close="guidanceOpen = false" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'

import AiResponsibleAiDrawer from './AiResponsibleAiDrawer.vue'

/**
 * The admin's AI notice (Admin AI settings), once on every AI screen, with the Responsible AI guidance beside it. The
 * guidance opens in a drawer over the page (X2.22): a signed-in learner stays in the AI Helper.
 */
defineProps<{ text: string; liveVideo?: boolean }>()

const { t } = useI18n()
const guidanceOpen = ref(false)
</script>

<style scoped>
.ai-notice-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-1) var(--space-3);
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-snug);
}

.ai-notice-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--text-primary);
  font: inherit;
  font-weight: var(--font-weight-strong);
  white-space: nowrap;
  cursor: pointer;
}

.ai-notice-link:hover {
  color: var(--text-link-hover);
  text-decoration: underline;
}

.ai-notice-link:focus-visible {
  outline: var(--border-width-focus) solid var(--focus-ring-color);
  outline-offset: var(--focus-ring-offset);
}
</style>
