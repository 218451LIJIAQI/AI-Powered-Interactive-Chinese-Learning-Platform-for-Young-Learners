<template>
  <LegalDocument :title="t('safety.title')" :intro="t('safety.intro')" :sections="sections">
    <template #links>
      <RouterLink class="btn btn-link" :to="{ name: 'responsible-ai' }">{{ t('safety.responsibleAiLink') }}</RouterLink>
      <RouterLink class="btn btn-link" :to="{ name: 'privacy' }">{{ t('safety.privacyLink') }}</RouterLink>
      <a class="btn btn-link" href="#report">{{ t('safety.reportLink') }}</a>
    </template>
  </LegalDocument>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import LegalDocument, { type LegalSection } from '@/components/public/LegalDocument.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { safetyMessages } from '@/plugins/safetyMessages'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const { t } = useLocalMessages({ safety: safetyMessages })

/** Section ids are linked from elsewhere: #teacher-expectations (teacher verification) and #report. */
const SECTIONS = [
  {
    id: 'keep-safe',
    title: 'safety.keepSafeTitle',
    bullets: ['safety.keepSafeTeachers', 'safety.keepSafeReview', 'safety.keepSafeAi', 'safety.keepSafeReports'],
  },
  {
    id: 'live-sessions',
    title: 'safety.liveTitle',
    bullets: ['safety.liveLink', 'safety.liveCamera', 'safety.liveRespect'],
  },
  { id: 'personal-details', title: 'safety.detailsTitle', bullets: ['safety.detailsNever', 'safety.detailsFree'] },
  { id: 'who-for', title: 'safety.audienceTitle', bullets: ['safety.audienceAdults', 'safety.audienceUnder18'] },
  {
    id: 'teacher-expectations',
    title: 'safety.teachersTitle',
    bullets: ['safety.teachersPlatform', 'safety.teachersFeedback', 'safety.teachersMoney'],
  },
  {
    id: 'report',
    title: 'safety.reportTitle',
    bullets: ['safety.reportSignedIn', 'safety.reportVisitor', 'safety.reportEmergency'],
  },
] as const

/** Signed-in students and teachers report through Help & support; everyone else through the Help page form. */
const reportLink = computed(() =>
  auth.isAuthenticated && auth.activeRole !== 'ADMIN'
    ? { label: t('safety.openSupport'), to: `/${auth.activeRole?.toLowerCase()}/support` }
    : { label: t('safety.openHelpForm'), to: '/help#contact' },
)

const sections = computed<LegalSection[]>(() =>
  SECTIONS.map(({ id, title, bullets }) => ({
    id,
    title: t(title),
    bullets: bullets.map((bullet) => t(bullet)),
    links: id === 'report' ? [reportLink.value] : undefined,
  })),
)
</script>
