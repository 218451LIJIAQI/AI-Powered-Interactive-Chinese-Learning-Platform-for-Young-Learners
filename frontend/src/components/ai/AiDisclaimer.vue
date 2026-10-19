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
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { publicApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { appEnv } from '@/config/env'
import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'

/**
 * AI disclaimer shown next to every AI tool (D19, FR-AI-09, FR-PUB-04). A parent may pass the admin-configured
 * platformSettings.aiDisclaimerText; otherwise the component loads it once per page load from the public settings.
 * The translated default is used when no admin text is available.
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

const adminText = ref<string | null>(null)

onMounted(async () => {
  if (props.text != null) return
  adminText.value = await loadAdminDisclaimer()
})

const disclaimerText = computed(() => props.text?.trim() || adminText.value || t('aiSafety.disclaimer'))
</script>

<script lang="ts">
let sharedAdminDisclaimer: Promise<string | null> | null = null

/** Loads the admin disclaimer once and shares it between instances; preview builds keep the default text. */
function loadAdminDisclaimer(): Promise<string | null> {
  if (appEnv.useDemoData && appEnv.enablePreviewMode) return Promise.resolve(null)
  if (!sharedAdminDisclaimer) {
    sharedAdminDisclaimer = Promise.resolve()
      .then(() => publicApi.platformSettings())
      .then((settings) => settings.aiDisclaimerText?.trim() || null)
      .catch(() => {
        sharedAdminDisclaimer = null
        return null
      })
  }
  return sharedAdminDisclaimer
}
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
