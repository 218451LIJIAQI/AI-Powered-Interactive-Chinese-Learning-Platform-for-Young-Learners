<template>
  <LegalDocument
    eyebrow="MandarinSpark"
    :title="t('legal.terms.title')"
    :intro="t('legal.terms.intro')"
    :back-label="t('legal.back')"
    :toc-label="t('legal.onThisPage')"
    :sections="sections"
  >
    <template #links>
      <RouterLink class="btn btn-link" :to="{ name: 'privacy' }">{{ t('legal.privacyLink') }}</RouterLink>
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
  (['accounts', 'learning', 'access', 'safety', 'support'] as const).map((key) => ({
    id: `terms-${key}`,
    title: t(`legal.terms.${key}Title`),
    copy: t(`legal.terms.${key}Copy`),
  })),
)
</script>
