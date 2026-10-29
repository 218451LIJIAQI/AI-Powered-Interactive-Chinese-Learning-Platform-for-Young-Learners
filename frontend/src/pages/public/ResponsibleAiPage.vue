<template>
  <LegalDocument :title="t('responsibleAi.title')" :intro="t('responsibleAi.intro')" :sections="sections">
    <template #lead>
      <p class="legal-official-title">{{ t('legal.officialTitle') }}</p>
    </template>
    <template #links>
      <RouterLink class="btn btn-link" :to="{ name: 'terms' }">{{ t('legal.termsLink') }}</RouterLink>
      <RouterLink class="btn btn-link" :to="{ name: 'privacy' }">{{ t('legal.privacyLink') }}</RouterLink>
      <RouterLink class="btn btn-link" :to="helperRoute" data-testid="ai-helper-link">{{ helperLabel }}</RouterLink>
    </template>
    <template #footer>
      <aside class="callout legal-note">
        <IconGlyph name="info" :size="18" />
        <span>
          <strong>{{ t('responsibleAi.disclaimerLabel') }}</strong>
          <span>{{ t('responsibleAi.disclaimer') }}</span>
        </span>
      </aside>
    </template>
  </LegalDocument>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import LegalDocument from '@/components/public/LegalDocument.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { legalMessages } from '@/plugins/legalMessages'
import { responsibleAiMessages } from '@/plugins/responsibleAiMessages'
import { useAuthStore } from '@/stores/auth'
import { loadPublicPlatformSettings } from '@/utils/publicPlatformSettings'
import { aiHelperEntryRoute } from '@/utils/role'

const auth = useAuthStore()
const { t } = useLocalMessages({ responsibleAi: responsibleAiMessages, legal: legalMessages })

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

const isStudent = computed(() => auth.isAuthenticated && auth.activeRole === 'STUDENT')
const helperRoute = computed(() => aiHelperEntryRoute(isStudent.value))
const helperLabel = computed(() =>
  isStudent.value ? t('responsibleAi.openHelper') : t('responsibleAi.signInForHelper'),
)

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

const sections = computed(() =>
  SECTIONS.map(({ id, title, copy }) => ({ id, title: t(title), paragraphs: [copy ? t(copy) : dataCopy()] })),
)
</script>
