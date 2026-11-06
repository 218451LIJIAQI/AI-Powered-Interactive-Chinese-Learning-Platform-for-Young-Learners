<template>
  <LegalDocument :title="t('responsibleAi.title')" :sections="sections" />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import LegalDocument, { type LegalSection } from '@/components/public/LegalDocument.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { responsibleAiMessages } from '@/plugins/responsibleAiMessages'
import { loadPublicPlatformSettings } from '@/utils/publicPlatformSettings'

const { t } = useLocalMessages({ responsibleAi: responsibleAiMessages })

const SECTIONS = [
  { id: 'responsible-ai-role', title: 'responsibleAi.roleTitle', copy: 'responsibleAi.roleCopy' },
  { id: 'responsible-ai-limits', title: 'responsibleAi.limitsTitle', copy: 'responsibleAi.limitsCopy' },
  { id: 'responsible-ai-bounds', title: 'responsibleAi.boundsTitle', copy: 'responsibleAi.boundsCopy' },
  { id: 'responsible-ai-voice', title: 'responsibleAi.voiceTitle', copy: 'responsibleAi.voiceCopy' },
  // The copy names the configured model: see dataCopy.
  { id: 'responsible-ai-data', title: 'responsibleAi.dataTitle', copy: null },
  { id: 'responsible-ai-safety', title: 'responsibleAi.safetyTitle', copy: 'responsibleAi.safetyCopy' },
  { id: 'responsible-ai-report', title: 'responsibleAi.reportTitle', copy: 'responsibleAi.reportCopy' },
] as const

/** The AI model configured on the server (`app.ai.model-name`); null while loading or when unset. */
const aiModelName = ref<string | null>(null)

onMounted(async () => {
  const settings = await loadPublicPlatformSettings()
  aiModelName.value = settings?.aiModelName?.trim() || null
})

/** The data section names the configured model; without one it names no model (the provider stays replaceable). */
function dataCopy(): string {
  return aiModelName.value
    ? t('responsibleAi.dataCopy', { model: aiModelName.value })
    : t('responsibleAi.dataCopyNoModel')
}

const sections = computed<LegalSection[]>(() =>
  SECTIONS.map(({ id, title, copy }) => ({
    id,
    title: t(title),
    note:
      id === 'responsible-ai-limits'
        ? { label: t('responsibleAi.disclaimerLabel'), text: t('responsibleAi.disclaimer') }
        : undefined,
    paragraphs: [copy ? t(copy) : dataCopy()],
  })),
)
</script>
