<template>
  <span class="ai-result-label" data-testid="ai-result-label">
    <IconGlyph name="ai" :size="14" />
    {{ t('aiSafety.aiAssisted') }}
  </span>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'

const { t } = useLocalMessages({ aiSafety: aiSafetyMessages })
</script>

<style scoped>
.ai-result-label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  width: fit-content;
  max-width: 100%;
  padding: var(--space-0-5) var(--space-2-5);
  border: 1px solid var(--color-info-border);
  border-radius: var(--radius-pill);
  background: var(--color-info-soft);
  color: var(--color-info-text);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  line-height: var(--line-height-normal);
}
</style>
