<template>
  <LegalDocument :title="t('legal.privacy.title')" :intro="t('legal.privacy.intro')" :sections="sections">
    <template #lead>
      <p class="legal-official-title" data-testid="official-title">{{ t('legal.officialTitle') }}</p>
    </template>
    <template #links>
      <RouterLink class="btn btn-link" :to="{ name: 'terms' }">{{ t('legal.termsLink') }}</RouterLink>
      <RouterLink class="btn btn-link" :to="{ name: 'responsible-ai' }">{{ t('legal.responsibleAiLink') }}</RouterLink>
    </template>
  </LegalDocument>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import LegalDocument from '@/components/public/LegalDocument.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { legalMessages } from '@/plugins/legalMessages'

const { t } = useLocalMessages({ legal: legalMessages })

/** The AI section keeps the id `ai-provider` (linked as /privacy#ai-provider). */
const SECTIONS = [
  { id: 'privacy-data', title: 'legal.privacy.dataTitle', copy: 'legal.privacy.dataCopy' },
  { id: 'privacy-purpose', title: 'legal.privacy.purposeTitle', copy: 'legal.privacy.purposeCopy' },
  { id: 'ai-provider', title: 'legal.privacy.aiTitle', copy: 'legal.privacy.aiCopy' },
  { id: 'privacy-minimisation', title: 'legal.privacy.minimisationTitle', copy: 'legal.privacy.minimisationCopy' },
  { id: 'privacy-files', title: 'legal.privacy.filesTitle', copy: 'legal.privacy.filesCopy' },
  { id: 'privacy-retention', title: 'legal.privacy.retentionTitle', copy: 'legal.privacy.retentionCopy' },
  { id: 'privacy-rights', title: 'legal.privacy.rightsTitle', copy: 'legal.privacy.rightsCopy' },
  { id: 'privacy-law', title: 'legal.privacy.lawTitle', copy: 'legal.privacy.lawCopy' },
  { id: 'privacy-third-parties', title: 'legal.privacy.thirdPartiesTitle', copy: 'legal.privacy.thirdPartiesCopy' },
  { id: 'privacy-contact', title: 'legal.privacy.contactTitle', copy: 'legal.privacy.contactCopy' },
] as const

const sections = computed(() => SECTIONS.map(({ id, title, copy }) => ({ id, title: t(title), paragraphs: [t(copy)] })))
</script>
