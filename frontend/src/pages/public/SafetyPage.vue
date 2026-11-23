<template>
  <LegalDocument :title="t('safety.title')" :sections="sections">
    <template #meta>
      <RouterLink class="btn btn-link" :to="CONTACT_FORM">
        {{ t('safety.reportConcern') }}
        <IconGlyph name="chevronRight" :size="16" />
      </RouterLink>
    </template>
    <!-- "Use the Contact us form on the Help page…": the words that name the form are the link to it. -->
    <template #report-form>
      <I18nT keypath="safety.reportForm" :i18n="sentenceScope">
        <template #link>
          <RouterLink :to="CONTACT_FORM" data-testid="safety-help-form">{{ t('safety.reportFormLink') }}</RouterLink>
        </template>
      </I18nT>
    </template>
  </LegalDocument>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { I18nT, type Composer } from 'vue-i18n'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import LegalDocument, { type LegalSection } from '@/components/public/LegalDocument.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { safetyMessages } from '@/plugins/safetyMessages'

/**
 * Every concern goes through the Help page's message form (support answers by e-mail), signed in or not, which opens
 * on the topic "Safety concern".
 */
const CONTACT_FORM = '/help?topic=SAFETY#contact'

const i18n = useLocalMessages({ safety: safetyMessages })
const { t } = i18n
/**
 * `I18nT` is drawn inside LegalDocument, where it would look up that component's messages: it is handed this page's
 * scope instead (the prop takes the composer without its message types).
 */
const sentenceScope = i18n as unknown as Composer

/** The bullet that sends people to the Help page's form: the `report-form` slot draws it, with the link. */
const REPORT_FORM = { slot: 'report-form' } as const

const SECTIONS = [
  {
    id: 'keep-safe',
    title: 'safety.keepSafeTitle',
    bullets: ['safety.keepSafeClasses', 'safety.keepSafeAi', 'safety.keepSafeReports'],
  },
  { id: 'personal-details', title: 'safety.detailsTitle', bullets: ['safety.detailsNever', 'safety.detailsFree'] },
  { id: 'who-for', title: 'safety.audienceTitle', bullets: ['safety.audienceAdults', 'safety.audienceUnder18'] },
  { id: 'report', title: 'safety.reportConcern', bullets: [REPORT_FORM, 'safety.reportEmergency'] },
] as const

const sections = computed<LegalSection[]>(() =>
  SECTIONS.map(({ id, title, bullets }) => ({
    id,
    title: t(title),
    bullets: bullets.map((bullet) => (typeof bullet === 'string' ? t(bullet) : bullet)),
  })),
)
</script>
