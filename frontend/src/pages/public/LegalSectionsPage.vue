<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

type LegalSection = { title: string; points: string[] }

/**
 * Shared layout for the four legal-style marketing pages
 * (Terms, Privacy, Child Safety, AI Disclaimer).
 *
 * Each page picks its own root i18n namespace (e.g. `legal.terms`,
 * `legal.privacy`) and the component reads the standard
 * `eyebrow` / `title` / `description` / `sections` shape from it.
 *
 * Centralising this layout means visual tweaks (typography, spacing,
 * card styling) only need to be made in one place.
 */
const props = defineProps<{
  /**
   * Root i18n namespace for this legal page. The namespace must expose
   * `eyebrow`, `title`, `description` strings and a `sections` array of
   * `{ title, points: string[] }`.
   */
  i18nKey: string
}>()

const { t, tm } = useI18n()

const sections = computed(() => (tm(`${props.i18nKey}.sections`) as LegalSection[]) ?? [])
</script>

<template>
  <div class="page-stack">
    <el-card class="simple-page-card legal-page-card" shadow="hover">
      <p class="section-label">{{ t(`${i18nKey}.eyebrow`) }}</p>
      <h1>{{ t(`${i18nKey}.title`) }}</h1>
      <p>{{ t(`${i18nKey}.description`) }}</p>
    </el-card>

    <section class="legal-grid">
      <el-card
        v-for="section in sections"
        :key="section.title"
        class="dashboard-panel legal-section-card"
        shadow="hover"
      >
        <h2>{{ section.title }}</h2>
        <ul class="legal-list">
          <li v-for="point in section.points" :key="point">{{ point }}</li>
        </ul>
      </el-card>
    </section>
  </div>
</template>
