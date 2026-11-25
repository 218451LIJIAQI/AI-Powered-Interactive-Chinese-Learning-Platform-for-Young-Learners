<template>
  <LegalDocument :title="t('legal.privacy.title')" :sections="sections" :updated="UPDATED" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import LegalDocument from '@/components/public/LegalDocument.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import {
  configuredTavusDeploymentId,
  resolveAiProviderLabel,
  usePublicPlatformSettings,
} from '@/composables/usePublicPlatformSettings'
import { aiDisclosureMessages } from '@/plugins/aiDisclosureMessages'
import { legalMessages } from '@/plugins/legalMessages'

const { t } = useLocalMessages({ legal: legalMessages, aiDisclosure: aiDisclosureMessages })
const { aiProvider } = usePublicPlatformSettings()
const hasLiveVideo = Boolean(configuredTavusDeploymentId())

/** The day this notice last changed. */
const UPDATED = '2026-11-19'

/** `ai-provider` says what really leaves the server, by the provider the server reports (aiParagraphs). */
const SECTIONS = [
  { id: 'privacy-data', key: 'data' },
  { id: 'privacy-purpose', key: 'purpose' },
  { id: 'ai-provider', key: 'ai' },
  { id: 'privacy-minimisation', key: 'minimisation' },
  { id: 'privacy-retention', key: 'retention' },
  { id: 'privacy-rights', key: 'rights' },
  { id: 'privacy-law', key: 'law' },
  { id: 'privacy-third-parties', key: 'thirdParties' },
  { id: 'privacy-contact', key: 'contact' },
] as const

/** OpenAI-specific handling only for OpenAI; nothing certain before the server has answered. */
const aiCopy = computed(() => {
  const ai = aiProvider.value
  switch (ai.kind) {
    case 'openai':
      return t('legal.privacy.aiOpenAi')
    case 'other':
      return t('legal.privacy.aiOther', { provider: resolveAiProviderLabel(ai.provider, t) })
    case 'off':
      return t('aiDisclosure.off')
    default:
      return t('legal.privacy.aiUnknown')
  }
})

/** Pronunciation scoring and configured live calls have their own providers and can work without the text service. */
const aiParagraphs = computed(() => [
  aiCopy.value,
  t('aiDisclosure.pronunciation'),
  ...(hasLiveVideo ? [t('aiDisclosure.liveVideo')] : []),
  ...(aiProvider.value.kind === 'off' ? [] : [t('legal.privacy.aiXiaomei')]),
])

const sections = computed(() =>
  SECTIONS.map(({ id, key }) => ({
    id,
    title: t(`legal.privacy.${key}Title`),
    paragraphs: key === 'ai' ? aiParagraphs.value : [t(`legal.privacy.${key}Copy`)],
  })),
)
</script>
