import { computed, toValue, type MaybeRefOrGetter } from 'vue'

import type { LegalSection } from '@/components/public/LegalDocument.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { resolveAiProviderLabel, usePublicPlatformSettings } from '@/composables/usePublicPlatformSettings'
import { aiDisclosureMessages } from '@/plugins/aiDisclosureMessages'
import { aiSafetyMessages, shownAiNotice } from '@/plugins/aiSafetyMessages'
import { responsibleAiMessages } from '@/plugins/responsibleAiMessages'

/** The day the guidance last changed (`yyyy-MM-dd`), shown under the title of the public page. */
export const RESPONSIBLE_AI_UPDATED = '2026-11-19'

const BUNDLES = {
  responsibleAi: responsibleAiMessages,
  aiDisclosure: aiDisclosureMessages,
  aiSafety: aiSafetyMessages,
}

/**
 * The public /responsible-ai page and the learner's AI Helper drawer share these seven sections.
 * The data section follows the AI provider reported by the server. Call this in a component's setup
 * to load the public platform settings on mount.
 *
 * Its messages live in their own (isolated) i18n scope, so the component that shows the guidance can keep its own
 * `useLocalMessages` (vue-i18n keeps one local scope per component).
 */
export function useResponsibleAiGuidance(hasLiveVideo: MaybeRefOrGetter<boolean>) {
  const { t } = useLocalMessages(BUNDLES, 'isolated')
  const { settings, aiProvider } = usePublicPlatformSettings()

  /** The notice learners really see with every AI answer, by the same rule as the AI Helper (see `shownAiNotice`). */
  const shownNotice = computed(() => shownAiNotice(settings.value?.aiDisclaimerText, t))

  /**
   * How the text/photo provider handles requests. Pronunciation has its own disclosure in the same section.
   */
  const dataParagraphs = computed<string[]>(() => {
    const ai = aiProvider.value
    if (ai.kind === 'off') return [t('aiDisclosure.off')]

    const what = t(ai.kind === 'other' ? 'responsibleAi.dataWhatText' : 'responsibleAi.dataWhat')
    const minimise = t('responsibleAi.dataMinimise')
    if (ai.kind === 'unknown') return [what, t('responsibleAi.dataUnknown'), minimise]

    let target: string
    let policy: string
    if (ai.kind === 'openai') {
      target = t('responsibleAi.targetOpenAi')
      policy = t('aiDisclosure.openAi')
    } else {
      const provider = resolveAiProviderLabel(ai.provider, t)
      target = t('responsibleAi.targetOther', { provider })
      policy = t('aiDisclosure.other', { provider })
    }
    if (ai.model) target = t('responsibleAi.targetModel', { target, model: ai.model })
    return [what, t('responsibleAi.dataTarget', { target }), minimise, policy]
  })

  const title = computed(() => t('responsibleAi.title'))

  const sections = computed<LegalSection[]>(() => [
    { id: 'responsible-ai-role', title: t('responsibleAi.roleTitle'), paragraphs: [t('responsibleAi.roleCopy')] },
    {
      id: 'responsible-ai-limits',
      title: t('responsibleAi.limitsTitle'),
      note: { label: t('responsibleAi.disclaimerLabel'), text: shownNotice.value },
      paragraphs: [t('responsibleAi.limitsCopy')],
    },
    { id: 'responsible-ai-bounds', title: t('responsibleAi.boundsTitle'), paragraphs: [t('responsibleAi.boundsCopy')] },
    { id: 'responsible-ai-voice', title: t('responsibleAi.voiceTitle'), paragraphs: [t('responsibleAi.voiceCopy')] },
    {
      id: 'responsible-ai-data',
      title: t('responsibleAi.dataTitle'),
      paragraphs: [
        ...dataParagraphs.value,
        t('aiDisclosure.pronunciation'),
        ...(toValue(hasLiveVideo) ? [t('aiDisclosure.liveVideo')] : []),
      ],
    },
    { id: 'responsible-ai-safety', title: t('responsibleAi.safetyTitle'), paragraphs: [t('responsibleAi.safetyCopy')] },
    { id: 'responsible-ai-report', title: t('responsibleAi.reportTitle'), paragraphs: [t('responsibleAi.reportCopy')] },
  ])

  return { title, sections }
}
