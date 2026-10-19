<template>
  <span class="ai-result-label" :class="{ 'is-feedback': feedback }" data-testid="ai-result-label">
    <IconGlyph name="ai" :size="14" />
    {{ feedback ? t('aiSafety.aiAssistedFeedback') : t('aiSafety.aiAssisted') }}
  </span>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'

withDefaults(defineProps<{ feedback?: boolean }>(), { feedback: false })

const { t } = useI18n({
  messages: {
    en: { aiSafety: aiSafetyMessages.en },
    ms: { aiSafety: aiSafetyMessages.ms },
    'zh-CN': { aiSafety: aiSafetyMessages['zh-CN'] },
  },
})
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

.ai-result-label.is-feedback {
  border-color: var(--color-warning-border);
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}
</style>
