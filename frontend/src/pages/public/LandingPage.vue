<template>
  <div class="public-page landing-page">
    <div v-if="accountDeleted" class="alert alert-success" role="status" data-testid="account-deleted-notice">
      <IconGlyph name="correct" :size="18" />
      <span>{{ t('landing.accountDeleted') }}</span>
    </div>

    <section class="panel landing-hero" aria-labelledby="landing-title">
      <div class="landing-hero-copy">
        <span class="tag">
          <IconGlyph name="students" :size="12" />
          {{ t('landing.audience') }}
        </span>
        <h1 id="landing-title" class="landing-title">{{ t('landing.heroTitle') }}</h1>
        <div class="btn-group">
          <RouterLink class="btn btn-accent btn-lg" :to="startRoute">
            {{ auth.isAuthenticated ? t('landing.openMyHome') : t('landing.startLearning') }}
            <IconGlyph name="chevronRight" :size="18" />
          </RouterLink>
          <RouterLink class="btn btn-secondary btn-lg" to="/classes">{{ t('landing.exploreClasses') }}</RouterLink>
        </div>
        <ul class="landing-facts">
          <li v-for="fact in FACTS" :key="fact.label">
            <IconGlyph :name="fact.icon" :size="14" />
            {{ t(fact.label) }}
          </li>
        </ul>
      </div>

      <figure class="landing-word">
        <span class="landing-word-tiles" lang="zh-CN">
          <span class="hanzi-tile landing-word-tile">你</span>
          <span class="hanzi-tile landing-word-tile">好</span>
        </span>
        <figcaption class="landing-word-gloss">
          <span lang="zh-Latn-pinyin">nǐ hǎo</span> · <span>{{ t('landing.greetingMeaning') }}</span>
        </figcaption>
      </figure>
    </section>

    <section id="how-it-works" class="panel landing-how" aria-labelledby="landing-how-title">
      <h2 id="landing-how-title" class="section-title">{{ t('landing.howTitle') }}</h2>
      <ul class="landing-how-list">
        <li v-for="step in STEPS" :key="step.title" class="landing-how-item">
          <span class="seal landing-how-seal" aria-hidden="true"><IconGlyph :name="step.icon" :size="24" /></span>
          <h3 class="card-title">{{ t(step.title) }}</h3>
          <p>{{ t(step.copy) }}</p>
        </li>
      </ul>
    </section>

    <section class="landing-featured" aria-labelledby="landing-featured-title">
      <div class="section-header">
        <h2 id="landing-featured-title" class="section-title">{{ t('landing.featuredTitle') }}</h2>
        <RouterLink class="btn btn-link" :to="{ name: 'classes' }">
          {{ t('landing.featuredViewAll') }}
          <IconGlyph name="chevronRight" :size="16" />
        </RouterLink>
      </div>
      <FeaturedClasses />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, type RouteLocationRaw } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import FeaturedClasses from '@/components/public/FeaturedClasses.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { landingMessages } from '@/plugins/landingMessages'
import { useAuthStore } from '@/stores/auth'
import { defaultRouteForRole } from '@/utils/role'

/** After an account is deleted, the Account page brings the user here with this notice. */
const ACCOUNT_DELETED_NOTICE = 'account-deleted'

const FACTS = [
  { label: 'landing.factFree', icon: 'check' },
  { label: 'landing.factVerified', icon: 'verified' },
  { label: 'landing.factPlan', icon: 'route' },
] as const

const STEPS = [
  { icon: 'route', title: 'landing.learnTitle', copy: 'landing.learnCopy' },
  { icon: 'target', title: 'landing.practiceTitle', copy: 'landing.practiceCopy' },
  { icon: 'about', title: 'landing.aiTitle', copy: 'landing.aiCopy' },
  { icon: 'students', title: 'landing.classesTitle', copy: 'landing.classesCopy' },
] as const

const route = useRoute()
const auth = useAuthStore()
const { t } = useLocalMessages({ landing: landingMessages })

const startRoute = computed<RouteLocationRaw>(() =>
  auth.isAuthenticated
    ? defaultRouteForRole(auth.activeRole)
    : { path: '/auth', query: { tab: 'register', role: 'student' } },
)
const accountDeleted = computed(() => route.query.notice === ACCOUNT_DELETED_NOTICE)
</script>
