<template>
  <p class="ai-disclaimer" role="note" data-testid="ai-disclaimer">
    <IconGlyph class="ai-disclaimer-icon" name="shield" :size="16" />
    <span class="ai-disclaimer-copy">
      <span>{{ disclaimerText }}</span>
      <RouterLink class="ai-disclaimer-link" :to="{ name: 'responsible-ai' }">{{
        t('aiSafety.guidanceLink')
      }}</RouterLink>
    </span>
  </p>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'
import { loadPublicPlatformSettings } from '@/utils/publicPlatformSettings'

/**
 * AI disclaimer of the teacher AI draft panel (FRAI08; the guidance link opens the responsible-AI page, FRPUB02).
 * It shows the admin-configured platformSettings.aiDisclaimerText, read from the public settings (loaded once per page
 * load and shared). The translated default is used when no admin text is available.
 */
const { t } = useLocalMessages({ aiSafety: aiSafetyMessages })

const adminText = ref<string | null>(null)

onMounted(async () => {
  const settings = await loadPublicPlatformSettings()
  adminText.value = settings?.aiDisclaimerText?.trim() || null
})

const disclaimerText = computed(() => adminText.value || t('aiSafety.disclaimer'))
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
