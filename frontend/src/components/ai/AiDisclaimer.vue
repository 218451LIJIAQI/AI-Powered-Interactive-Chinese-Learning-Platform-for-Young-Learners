<template>
  <p class="ai-disclaimer" role="note" data-testid="ai-disclaimer">
    <IconGlyph class="ai-disclaimer-icon" name="shield" :size="16" />
    <span class="ai-disclaimer-copy">
      <span>{{ disclaimerText }}</span>
      <RouterLink v-if="showGuidanceLink" class="ai-disclaimer-link" :to="{ name: 'responsible-ai' }">{{
        t('aiSafety.guidanceLink')
      }}</RouterLink>
    </span>
  </p>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'

/**
 * AI disclaimer shown next to every AI tool (D19, FR-AI-09). Pass the admin-configured
 * platformSettings.aiDisclaimerText when available; the translated default is used otherwise.
 */
const props = withDefaults(defineProps<{ text?: string | null; showGuidanceLink?: boolean }>(), {
  text: null,
  showGuidanceLink: true,
})

const { t } = useI18n({
  messages: {
    en: { aiSafety: aiSafetyMessages.en },
    ms: { aiSafety: aiSafetyMessages.ms },
    'zh-CN': { aiSafety: aiSafetyMessages['zh-CN'] },
  },
})

const disclaimerText = computed(() => props.text?.trim() || t('aiSafety.disclaimer'))
</script>

<style scoped>
/* Icon column + text column: the shield never wraps onto its own line, however narrow the host card is. */
.ai-disclaimer {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: var(--space-2);
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.ai-disclaimer-icon {
  margin-top: var(--space-0-5);
  color: var(--text-brand);
}

.ai-disclaimer-copy {
  min-width: 0;
  overflow-wrap: anywhere;
}

.ai-disclaimer-link {
  margin-left: var(--space-1-5);
  color: var(--text-link);
  font-weight: var(--font-weight-bold);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.ai-disclaimer-link:hover {
  color: var(--text-link-hover);
}
</style>
