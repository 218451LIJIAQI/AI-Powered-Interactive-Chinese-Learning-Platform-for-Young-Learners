<template>
  <div class="public-page public-landing-page">
    <section class="public-hero-card">
      <div class="public-hero-skyline public-hero-skyline--left" />
      <div class="public-hero-skyline public-hero-skyline--right" />
      <div class="public-hero-bamboo public-hero-bamboo--left" />
      <div class="public-hero-bamboo public-hero-bamboo--right" />
      <div class="public-hero-cloud public-hero-cloud--1" />
      <div class="public-hero-cloud public-hero-cloud--2" />
      <div class="public-hero-cloud public-hero-cloud--3" />

      <div class="public-hero-grid">
        <div class="public-hero-copy">
          <div class="public-hero-chip">
            <span>{{ t('landing.youngLearners') }}</span>
            <span>{{ t('landing.ageLine') }}</span>
          </div>

          <h1>
            {{ t('landing.heroPrefixOne') }} <span class="accent-purple">{{ t('landing.heroEmphasisOne') }}</span
            >{{ t('landing.heroSuffixOne') }}<br />
            {{ t('landing.heroPrefixTwo') }} <span class="accent-orange">{{ t('landing.heroEmphasisTwo') }}</span
            >{{ t('landing.heroSuffixTwo') }}
          </h1>

          <p>{{ t('landing.heroDescription') }}</p>

          <div class="public-hero-actions">
            <button class="btn btn-primary public-hero-button" type="button" @click="openCatalog">
              <span>{{ t('landing.exploreCourses') }}</span>
            </button>
            <button class="btn btn-accent public-hero-button public-hero-button--gold" type="button" @click="openAuth">
              <span>{{ t('landing.startLearning') }}</span>
            </button>
          </div>

          <div class="public-hero-checks">
            <span>{{ t('landing.ages') }}</span>
            <span>{{ t('landing.beginnerFriendly') }}</span>
            <span>{{ t('landing.confidence') }}</span>
          </div>

          <div class="public-hero-proof">
            <article v-for="item in heroProof" :key="item.label">
              <strong>{{ item.value }}</strong>
              <span>{{ item.label }}</span>
            </article>
          </div>
        </div>

        <div class="public-hero-visual">
          <div class="public-hero-speech">
            <span>Ni hao!</span>
            <small>{{ t('landing.hello') }}</small>
          </div>
          <PandaIllustration class="public-hero-panda" :size="228" outfit="#6d47ff" accent="#f6c44f" />
        </div>
      </div>
    </section>

    <section class="public-feature-strip">
      <PublicFeatureCard
        v-for="feature in featureStrip"
        :key="feature.title"
        :icon="feature.icon"
        :title="feature.title"
        :description="feature.description"
        :tone="feature.tone"
      />
    </section>

    <section id="how-it-works" class="public-journey-strip" :aria-label="t('landing.howItWorks')">
      <article v-for="step in familyJourney" :key="step.title" class="public-journey-card">
        <span class="public-journey-step">{{ step.step }}</span>
        <strong>{{ step.title }}</strong>
        <p>{{ step.copy }}</p>
      </article>
    </section>

    <section class="public-home-content">
      <div class="public-home-main">
        <DataNotice
          :using-fallback="featuredCoursesResource.usingFallback.value"
          :error="featuredCoursesResource.error.value"
          :fallback-title="t('landing.previewTitle')"
          :fallback-message="t('landing.previewMessage')"
          :error-title="t('landing.unavailableTitle')"
        />

        <section class="panel public-home-panel">
          <div class="public-section-head">
            <h2>{{ t('landing.featuredCourses') }}</h2>
            <button class="public-link-button" type="button" @click="openCatalog">{{ t('landing.viewAll') }}</button>
          </div>

          <div v-if="featuredCourses.length" class="public-featured-grid">
            <article v-for="(course, index) in featuredCourses" :key="course.id" class="public-featured-card">
              <button
                class="public-featured-card-button"
                type="button"
                :aria-label="t('landing.openCourse', { title: course.title })"
                @click="viewCourse(course.id)"
              >
                <div class="public-featured-thumb">
                  <CourseSceneThumb :size="158" :variant="featuredThumb(index)" />
                  <span class="public-featured-level">{{ course.level.name }}</span>
                </div>

                <div class="public-featured-copy">
                  <h3>{{ course.title }}</h3>
                  <p>{{ featuredCopy(course) }}</p>
                </div>

                <div class="public-featured-meta">
                  <span>{{ course.category.name }}</span>
                  <span>{{ course.teacher.displayName }}</span>
                </div>
              </button>
            </article>
          </div>
          <div v-else class="public-featured-empty">{{ t('landing.noCourses') }}</div>

          <div class="public-carousel-dots">
            <span class="is-active" />
            <span />
            <span />
          </div>
        </section>
      </div>

      <aside class="public-home-side">
        <PublicInfoBanner
          icon="shield"
          :title="t('landing.safetyCardTitle')"
          :description="t('landing.safetyCardCopy')"
          :badge="t('landing.safetyCardBadge')"
          tone="green"
        />
        <PublicInfoBanner
          icon="verified"
          :title="t('landing.teacherCardTitle')"
          :description="t('landing.teacherCardCopy')"
          :badge="t('landing.teacherCardBadge')"
          tone="purple"
        />
        <PublicInfoBanner
          icon="globe"
          :title="t('landing.webCardTitle')"
          :description="t('landing.webCardCopy')"
          :badge="t('landing.webCardBadge')"
          tone="blue"
        />
      </aside>
    </section>

    <section id="safety" class="public-safety-banner">
      <div class="public-safety-icon">
        <IconGlyph name="shield" :size="32" />
      </div>
      <div class="public-safety-copy">
        <strong>{{ t('landing.safetyBannerTitle') }}</strong>
        <p>{{ t('landing.safetyBannerCopy') }}</p>
      </div>
      <div class="public-safety-scene">
        <div class="public-safety-hill" />
        <div class="public-safety-pagoda" />
      </div>
    </section>

    <section id="support" class="panel public-support-panel">
      <div class="public-section-head">
        <div>
          <span class="legal-eyebrow">{{ t('landing.support') }}</span>
          <h2>{{ t('landing.supportTitle') }}</h2>
        </div>
        <button class="btn btn-primary" type="button" @click="openSupportSignIn">
          {{ t('landing.signInSupport') }}
        </button>
      </div>
      <div class="public-support-grid">
        <article v-for="item in supportOptions" :key="item.title" class="public-support-card">
          <strong>{{ item.title }}</strong>
          <p>{{ item.copy }}</p>
        </article>
      </div>
    </section>

    <footer id="about" class="public-footer">
      <div class="public-footer-brand">
        <AppBrand />
        <div class="public-footer-fish"><IconGlyph name="language" :size="20" /></div>
      </div>

      <div class="public-footer-links">
        <div class="public-footer-column">
          <strong>{{ t('landing.explore') }}</strong>
          <a href="/courses" @click.prevent="openCatalog">{{ t('landing.courses') }}</a>
          <a href="#how-it-works">{{ t('landing.howItWorksLink') }}</a>
        </div>
        <div class="public-footer-column">
          <strong>{{ t('landing.company') }}</strong>
          <a href="#about">{{ t('landing.aboutUs') }}</a>
          <a href="#support">{{ t('landing.contactUs') }}</a>
        </div>
        <div class="public-footer-column">
          <strong>{{ t('landing.safety') }}</strong>
          <a href="#safety">{{ t('landing.childSafety') }}</a>
          <a href="/privacy" @click.prevent="openPrivacy">{{ t('landing.privacy') }}</a>
          <a href="/terms" @click.prevent="openTerms">{{ t('landing.terms') }}</a>
        </div>
        <div class="public-footer-column">
          <strong>{{ t('landing.support') }}</strong>
          <a href="#support">{{ t('landing.helpCenter') }}</a>
          <a href="#support">{{ t('landing.contactSupport') }}</a>
        </div>
      </div>

      <div class="public-footer-meta">
        <div class="public-footer-copy">
          <span>&copy; {{ copyrightYear }} MandarinSpark. {{ t('landing.rights') }}</span>
          <span>{{ t('landing.madeForLearners') }}</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { publicApi } from '@/api/services'
import CourseSceneThumb from '@/components/common/CourseSceneThumb.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import PublicFeatureCard from '@/components/public/PublicFeatureCard.vue'
import PublicInfoBanner from '@/components/public/PublicInfoBanner.vue'
import { useResource } from '@/composables/useResource'
import { publicCourses } from '@/demo/data'
import { landingMessages } from '@/plugins/landingMessages'
import '@/styles/landing.css'
import type { PublicCourseSummary } from '@/types/api'

type CourseSceneVariant = 'panda' | 'family' | 'pagoda' | 'travel' | 'general'
type FeatureTone = 'blue' | 'gold' | 'green' | 'orange' | 'pink' | 'purple'

const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { landing: landingMessages.en },
    ms: { landing: landingMessages.ms },
    zh: { landing: landingMessages.zh },
  },
})
const copyrightYear = new Date().getFullYear()

const previewFeaturedCourses = publicCourses.slice(0, 3)
const featuredCoursesResource = useResource(() => publicApi.listCourses({ size: 3, sort: 'newest' }), {
  items: previewFeaturedCourses,
  page: 0,
  size: 3,
  totalItems: previewFeaturedCourses.length,
  totalPages: 1,
})
const liveFeaturedCourses = computed(() => featuredCoursesResource.data.value?.items ?? [])
const featuredCourses = computed<PublicCourseSummary[]>(() =>
  liveFeaturedCourses.value.length
    ? liveFeaturedCourses.value
    : featuredCoursesResource.usingFallback.value
      ? previewFeaturedCourses
      : [],
)

const featureStrip = computed(
  () =>
    [
      {
        icon: 'status',
        title: t('landing.featureBasicsTitle'),
        description: t('landing.featureBasicsCopy'),
        tone: 'purple',
      },
      {
        icon: 'play',
        title: t('landing.featureActivitiesTitle'),
        description: t('landing.featureActivitiesCopy'),
        tone: 'green',
      },
      {
        icon: 'ai',
        title: t('landing.featureAiTitle'),
        description: t('landing.featureAiCopy'),
        tone: 'blue',
      },
      {
        icon: 'live',
        title: t('landing.featureLiveTitle'),
        description: t('landing.featureLiveCopy'),
        tone: 'orange',
      },
      {
        icon: 'trophy',
        title: t('landing.featureRewardsTitle'),
        description: t('landing.featureRewardsCopy'),
        tone: 'gold',
      },
      {
        icon: 'teacher',
        title: t('landing.featureTeacherTitle'),
        description: t('landing.featureTeacherCopy'),
        tone: 'pink',
      },
    ] satisfies Array<{ icon: string; title: string; description: string; tone: FeatureTone }>,
)

const heroProof = computed(() => [
  { value: t('landing.proofPathValue'), label: t('landing.proofPathLabel') },
  { value: t('landing.proofAiValue'), label: t('landing.proofAiLabel') },
  { value: t('landing.proofRewardsValue'), label: t('landing.proofRewardsLabel') },
])

const familyJourney = computed(() => [
  {
    step: '1',
    title: t('landing.journeyExploreTitle'),
    copy: t('landing.journeyExploreCopy'),
  },
  {
    step: '2',
    title: t('landing.journeyLearnTitle'),
    copy: t('landing.journeyLearnCopy'),
  },
  {
    step: '3',
    title: t('landing.journeySupportTitle'),
    copy: t('landing.journeySupportCopy'),
  },
])

const supportOptions = computed(() => [
  {
    title: t('landing.supportFamiliesTitle'),
    copy: t('landing.supportFamiliesCopy'),
  },
  {
    title: t('landing.supportTeachersTitle'),
    copy: t('landing.supportTeachersCopy'),
  },
  {
    title: t('landing.supportAdminsTitle'),
    copy: t('landing.supportAdminsCopy'),
  },
])

function viewCourse(id: string): void {
  void router.push(`/courses/${id}`)
}

const featuredThumbs = ['panda', 'family', 'pagoda'] as const

function featuredThumb(index: number): CourseSceneVariant {
  return featuredThumbs[index % featuredThumbs.length]
}

function featuredCopy(course: PublicCourseSummary): string {
  return (
    course.shortDescription?.trim() ||
    t('landing.courseFallback', {
      teacher: course.teacher.displayName,
      category: course.category.name.toLowerCase(),
    })
  )
}

function openCatalog(): void {
  void router.push('/courses')
}

function openAuth(): void {
  void router.push('/auth')
}

function openSupportSignIn(): void {
  void router.push({ path: '/auth', query: { tab: 'signin' } })
}

function openPrivacy(): void {
  void router.push('/privacy')
}

function openTerms(): void {
  void router.push('/terms')
}
</script>
