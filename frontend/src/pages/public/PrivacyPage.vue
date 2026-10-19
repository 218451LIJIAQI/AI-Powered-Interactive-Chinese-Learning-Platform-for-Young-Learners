<template>
  <LegalDocument
    eyebrow="MandarinSpark"
    :title="t('legal.privacy.title')"
    :intro="t('legal.privacy.intro')"
    :back-label="t('legal.back')"
    :toc-label="t('legal.onThisPage')"
    :sections="sections"
  >
    <template #lead>
      <p class="legal-official-title" data-testid="official-title">{{ t('legal.officialTitle') }}</p>
    </template>
    <template #links>
      <RouterLink class="btn btn-link" :to="{ name: 'responsible-ai' }">{{ t('legal.responsibleAiLink') }}</RouterLink>
    </template>
  </LegalDocument>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'

import LegalDocument from '@/components/public/LegalDocument.vue'
import { legalMessages } from '@/plugins/legalMessages'

const { t } = useI18n({
  messages: {
    en: { legal: legalMessages.en },
    ms: { legal: legalMessages.ms },
    'zh-CN': { legal: legalMessages['zh-CN'] },
  },
})

const sections = computed(() =>
  (
    ['data', 'purpose', 'ai', 'minimisation', 'files', 'retention', 'rights', 'law', 'contact', 'thirdParties'] as const
  ).map((key) => ({
    id: key === 'ai' ? 'ai-provider' : `privacy-${key}`,
    title: t(`legal.privacy.${key}Title`),
    copy: t(`legal.privacy.${key}Copy`),
  })),
)
</script>
