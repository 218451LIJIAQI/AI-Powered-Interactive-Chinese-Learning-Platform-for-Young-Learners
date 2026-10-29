<template>
  <LegalDocument :title="t('legal.terms.title')" :intro="t('legal.terms.intro')" :sections="sections">
    <template #links>
      <RouterLink class="btn btn-link" :to="{ name: 'privacy' }">{{ t('legal.privacyLink') }}</RouterLink>
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

const SECTIONS = [
  { id: 'terms-accounts', title: 'legal.terms.accountsTitle', copy: 'legal.terms.accountsCopy' },
  { id: 'terms-learning', title: 'legal.terms.learningTitle', copy: 'legal.terms.learningCopy' },
  { id: 'terms-free', title: 'legal.terms.freeTitle', copy: 'legal.terms.freeCopy' },
  { id: 'terms-safety', title: 'legal.terms.safetyTitle', copy: 'legal.terms.safetyCopy' },
  { id: 'terms-support', title: 'legal.terms.supportTitle', copy: 'legal.terms.supportCopy' },
] as const

const sections = computed(() => SECTIONS.map(({ id, title, copy }) => ({ id, title: t(title), paragraphs: [t(copy)] })))
</script>
