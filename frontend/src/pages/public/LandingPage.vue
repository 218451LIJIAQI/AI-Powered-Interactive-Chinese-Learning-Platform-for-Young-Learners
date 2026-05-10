<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import PageHero from '../../components/common/PageHero.vue'
import PlaceholderCard from '../../components/common/PlaceholderCard.vue'

const { t } = useI18n()

const featureCards = computed(() => [
  {
    title: t('landing.featureAiTutorTitle'),
    description: t('landing.featureAiTutorDescription'),
  },
  {
    title: t('landing.featurePracticeTitle'),
    description: t('landing.featurePracticeDescription'),
  },
  {
    title: t('landing.featureTeacherTitle'),
    description: t('landing.featureTeacherDescription'),
  },
])
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
      <PlaceholderCard :title="t('landing.rewardFeatureTitle')" :description="t('landing.rewardFeatureDescription')" />
      <PlaceholderCard :title="t('landing.teacherFeatureTitle')" :description="t('landing.teacherFeatureDescription')" />
      <PlaceholderCard :title="t('landing.adminFeatureTitle')" :description="t('landing.adminFeatureDescription')" />
    </section>
  </div>
</template>
