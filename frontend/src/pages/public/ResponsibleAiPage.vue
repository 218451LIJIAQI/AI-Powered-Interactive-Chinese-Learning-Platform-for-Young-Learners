<template>
  <LegalDocument
    :eyebrow="t('responsibleAi.eyebrow')"
    :title="t('responsibleAi.title')"
    :intro="t('responsibleAi.intro')"
    :back-label="t('responsibleAi.back')"
    :toc-label="t('responsibleAi.onThisPage')"
    :sections="sections"
  >
    <template #lead>
      <p class="legal-official-title">{{ t('responsibleAi.officialTitle') }}</p>
    </template>
    <template #links>
      <RouterLink class="btn btn-link" :to="{ name: 'privacy' }">{{ t('responsibleAi.privacyLink') }}</RouterLink>
      <RouterLink class="btn btn-link" :to="{ name: 'student-ai-helper' }">{{
        t('responsibleAi.aiHelperLink')
      }}</RouterLink>
    </template>
  </LegalDocument>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'

import LegalDocument from '@/components/public/LegalDocument.vue'
import { responsibleAiMessages } from '@/plugins/responsibleAiMessages'

const { t } = useI18n({
  messages: {
    en: { responsibleAi: responsibleAiMessages.en },
    ms: { responsibleAi: responsibleAiMessages.ms },
    'zh-CN': { responsibleAi: responsibleAiMessages['zh-CN'] },
  },
})

const sections = computed(() =>
  (['role', 'limits', 'bounds', 'feedback', 'data', 'safety', 'report'] as const).map((key) => ({
    id: `responsible-ai-${key}`,
    title: t(`responsibleAi.${key}Title`),
    copy: t(`responsibleAi.${key}Copy`),
  })),
)
</script>
