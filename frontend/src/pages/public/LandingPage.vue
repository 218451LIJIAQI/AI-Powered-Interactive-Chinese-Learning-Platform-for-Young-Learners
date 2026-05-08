<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import PlaceholderCard from '../../components/common/PlaceholderCard.vue'

const { t } = useI18n()

const featureCards = [
  {
    title: 'AI Tutor',
    description: 'Friendly AI tutoring, vocabulary help, and sentence guidance are central to the learning experience.',
  },
  {
    title: 'Interactive Practice',
    description: 'Flashcards, quizzes, tracing, matching games, and listening activities support playful repetition.',
  },
  {
    title: 'Teacher-led Learning',
    description: 'Teacher-created live, recorded, and hybrid lessons will fit into a moderated learning platform.',
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
            <p class="section-label">{{ t('landing.phaseBadge') }}</p>
            <h2>{{ t('landing.phaseTitle') }}</h2>
            <p>
              {{ t('landing.phaseDescription') }}
            </p>
          </div>
          <div class="feature-banner__actions">
            <router-link :to="{ name: 'register' }">
              <el-button type="primary" size="large">{{ t('common.startLearning') }}</el-button>
            </router-link>
            <router-link :to="{ name: 'courses' }">
              <el-button size="large" plain>{{ t('common.learnMore') }}</el-button>
            </router-link>
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

    <section class="placeholder-grid">
      <PlaceholderCard title="Gamified rewards" description="Points, badges, levels, and daily streaks will keep progress encouraging and child-friendly." />
      <PlaceholderCard title="Teacher dashboards" description="Teachers will manage future lessons, student progress, and AI-assisted content tools from dedicated workspaces." />
      <PlaceholderCard title="Admin oversight" description="Admins will review approvals, safety signals, AI usage, and operational health with protected controls." />
    </section>
  </div>
</template>
