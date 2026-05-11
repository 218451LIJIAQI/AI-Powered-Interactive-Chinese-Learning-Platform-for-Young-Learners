<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const features = computed(() => [
  {
    eyebrow: '01',
    title: t('landing.featureAiTutorTitle'),
    description: t('landing.featureAiTutorDescription'),
  },
  {
    eyebrow: '02',
    title: t('landing.featurePracticeTitle'),
    description: t('landing.featurePracticeDescription'),
  },
  {
    eyebrow: '03',
    title: t('landing.featureTeacherTitle'),
    description: t('landing.featureTeacherDescription'),
  },
  {
    eyebrow: '04',
    title: t('landing.rewardFeatureTitle'),
    description: t('landing.rewardFeatureDescription'),
  },
  {
    eyebrow: '05',
    title: t('landing.teacherFeatureTitle'),
    description: t('landing.teacherFeatureDescription'),
  },
  {
    eyebrow: '06',
    title: t('landing.adminFeatureTitle'),
    description: t('landing.adminFeatureDescription'),
  },
])
</script>

<template>
  <div class="landing">
    <!-- Hero: oversized typographic headline with single CTA pair, no decorative
         containers. The eyebrow is a quiet uppercase label — brand signal,
         not a colored pill. -->
    <section class="landing-hero">
      <p class="landing-hero__eyebrow">{{ t('landing.badge') }}</p>
      <h1 class="landing-hero__title">{{ t('landing.title') }}</h1>
      <p class="landing-hero__subtitle">{{ t('landing.subtitle') }}</p>
      <div class="landing-hero__actions">
        <router-link :to="{ name: 'register' }">
          <el-button type="primary" size="large">{{ t('common.startLearning') }}</el-button>
        </router-link>
        <router-link :to="{ name: 'courses' }">
          <el-button size="large" text>
            {{ t('common.learnMore') }} →
          </el-button>
        </router-link>
      </div>
    </section>

    <hr class="landing-divider" />

    <!-- Feature grid: 3 columns of typography-driven items. No box per item;
         a hairline rule between rows defines the structure. -->
    <section class="landing-features">
      <article v-for="feature in features" :key="feature.title" class="landing-feature">
        <span class="landing-feature__eyebrow">{{ feature.eyebrow }}</span>
        <h3 class="landing-feature__title">{{ feature.title }}</h3>
        <p class="landing-feature__description">{{ feature.description }}</p>
      </article>
    </section>
  </div>
</template>

<style scoped>
.landing {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 0 48px;
}

.landing-hero {
  padding: 48px 0 56px;
  max-width: 760px;
}

.landing-hero__eyebrow {
  margin: 0 0 20px;
  color: var(--text-tertiary);
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.landing-hero__title {
  margin: 0 0 20px;
  font-size: clamp(2rem, 5vw, 3.5rem);
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.03em;
  color: var(--text-primary);
}

.landing-hero__subtitle {
  margin: 0 0 32px;
  max-width: 600px;
  color: var(--text-secondary);
  font-size: clamp(1.05rem, 1.4vw, 1.2rem);
  line-height: 1.6;
}

.landing-hero__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.landing-divider {
  border: 0;
  border-top: 1px solid var(--border-color);
  margin: 0 0 56px;
}

.landing-features {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 56px 40px;
}

.landing-feature {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

.landing-feature__eyebrow {
  color: var(--text-tertiary);
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  font-variant-numeric: tabular-nums;
}

.landing-feature__title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}

.landing-feature__description {
  margin: 0;
  color: var(--text-secondary);
  font-size: 0.95rem;
  line-height: 1.55;
}

@media (max-width: 960px) {
  .landing-features {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 40px 24px;
  }
}

@media (max-width: 640px) {
  .landing-features {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .landing-hero {
    padding: 24px 0 40px;
  }
}
</style>
