<template>
  <section class="public-page legal-page">
    <button class="btn btn-ghost legal-back-button" type="button" @click="goBack">{{ t('legal.back') }}</button>

    <article class="hero-card light legal-hero">
      <span class="legal-eyebrow">MandarinSpark</span>
      <h1>{{ t('legal.terms.title') }}</h1>
      <p>{{ t('legal.terms.intro') }}</p>
    </article>

    <section class="legal-grid">
      <article v-for="section in sections" :key="section.key" class="panel legal-card">
        <h2>{{ section.title }}</h2>
        <p>{{ section.copy }}</p>
      </article>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { legalMessages } from '@/plugins/legalMessages'

const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { legal: legalMessages.en },
    ms: { legal: legalMessages.ms },
    zh: { legal: legalMessages.zh },
  },
})

function goBack(): void {
  if (window.history.length > 1) {
    router.back()
  } else {
    void router.push('/')
  }
}

const sections = computed(() =>
  (['accounts', 'learning', 'payments', 'safety', 'support'] as const).map((key) => ({
    key,
    title: t(`legal.terms.${key}Title`),
    copy: t(`legal.terms.${key}Copy`),
  })),
)
</script>
