<template>
  <LegalDocument :title="t('legal.terms.title')" :sections="sections" :updated="UPDATED" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import LegalDocument from '@/components/public/LegalDocument.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { legalMessages } from '@/plugins/legalMessages'

const { t } = useLocalMessages({ legal: legalMessages })

/** The day these terms last changed. */
const UPDATED = '2026-05-01'

const SECTIONS = ['accounts', 'learning', 'free', 'safety', 'support'] as const

const sections = computed(() =>
  SECTIONS.map((key) => ({
    id: `terms-${key}`,
    title: t(`legal.terms.${key}Title`),
    paragraphs: [t(`legal.terms.${key}Copy`)],
  })),
)
</script>
