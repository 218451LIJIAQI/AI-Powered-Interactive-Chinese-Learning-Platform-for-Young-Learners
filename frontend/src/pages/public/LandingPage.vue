<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import PlaceholderCard from '../../components/common/PlaceholderCard.vue'

const { t } = useI18n()

const featureCards = [
  {
    title: 'AI Tutor',
    description: 'A friendly learning guide will become a core feature in later phases.',
  },
  {
    title: 'Interactive Practice',
    description: 'Flashcards, quizzes, tracing, and listening experiences will live here.',
  },
  {
    title: 'Teacher-led Learning',
    description: 'Live, recorded, and hybrid learning content will be added in later phases.',
  },
]
</script>

<template>
  <div class="page-stack">
    <PageHero :eyebrow="t('landing.badge')" :title="t('landing.title')" :description="t('landing.subtitle')" />

    <section class="hero-grid">
      <el-card class="feature-banner" shadow="never">
        <div class="feature-banner__content">
          <div>
            <p class="section-label">Phase 1 Foundation</p>
            <h2>Frontend structure, backend foundation, and local development setup are ready.</h2>
            <p>
              This starter already includes role-based layouts, routing, i18n, theme support, backend health checks,
              and MySQL development setup.
            </p>
          </div>
          <div class="feature-banner__actions">
            <el-button type="primary" size="large">{{ t('common.startLearning') }}</el-button>
            <el-button size="large" plain>{{ t('common.learnMore') }}</el-button>
          </div>
        </div>
      </el-card>

      <div class="placeholder-grid">
        <PlaceholderCard
          v-for="card in featureCards"
          :key="card.title"
          :title="card.title"
          :description="card.description"
        />
      </div>
    </section>
  </div>
</template>
