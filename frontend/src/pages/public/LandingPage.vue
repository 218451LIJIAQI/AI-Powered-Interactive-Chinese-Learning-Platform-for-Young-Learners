<template>
  <div class="public-page public-landing-page">
    <section class="public-hero-card" aria-labelledby="landing-hero-title">
      <div class="public-hero-decor" aria-hidden="true">
        <span class="public-hero-bamboo public-hero-bamboo--left" />
        <span class="public-hero-bamboo public-hero-bamboo--right" />
        <span class="public-hero-cloud public-hero-cloud--1" />
        <span class="public-hero-cloud public-hero-cloud--2" />
        <span class="public-hero-cloud public-hero-cloud--3" />
      </div>

      <div class="public-hero-grid">
        <div class="public-hero-copy">
          <p class="public-hero-chip">
            <span class="public-hero-chip-label">{{ t('landing.audienceChip') }}</span>
            <span>{{ t('landing.ageLine') }}</span>
          </p>

          <h1 id="landing-hero-title" class="public-hero-title">
            {{ t('landing.heroPrefixOne') }} <span class="public-hero-emphasis">{{ t('landing.heroEmphasisOne') }}</span
            >{{ t('landing.heroSuffixOne') }}<br />
            {{ t('landing.heroPrefixTwo') }}
            <span class="public-hero-emphasis public-hero-emphasis--warm">{{ t('landing.heroEmphasisTwo') }}</span
            >{{ t('landing.heroSuffixTwo') }}
          </h1>

          <p class="public-hero-lead">{{ t('landing.heroDescription') }}</p>

          <div class="public-hero-actions">
            <button class="btn btn-accent btn-lg" type="button" @click="openAuth">
              {{ t('landing.startLearning') }}
              <IconGlyph name="chevronRight" :size="18" />
            </button>
            <button class="btn btn-secondary btn-lg" type="button" @click="openCatalog">
              {{ t('landing.exploreCourses') }}
            </button>
          </div>

          <ul class="public-hero-checks">
            <li v-for="check in heroChecks" :key="check">
              <IconGlyph name="status" :size="16" />
              <span>{{ check }}</span>
            </li>
          </ul>
        </div>

        <div class="public-hero-visual">
          <div class="public-hero-stage" aria-hidden="true">
            <div class="public-hero-speech">
              <span>Ni hao!</span>
              <small>{{ t('landing.hello') }}</small>
            </div>
            <PandaIllustration class="public-hero-panda" :size="228" outfit="#6d47ff" accent="#f6c44f" />
          </div>

          <ul class="public-hero-proof">
            <li v-for="item in heroProof" :key="item.label">
              <strong>{{ item.value }}</strong>
              <span>{{ item.label }}</span>
            </li>
          </ul>
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

    <section id="how-it-works" class="public-journey" aria-labelledby="landing-journey-title">
      <h2 id="landing-journey-title" class="section-title">{{ t('landing.howItWorks') }}</h2>
      <ol class="public-journey-strip">
        <li v-for="step in learnerJourney" :key="step.title" class="public-journey-card">
          <span class="public-journey-step" aria-hidden="true">{{ step.step }}</span>
          <div class="public-journey-copy">
            <h3>{{ step.title }}</h3>
            <p>{{ step.copy }}</p>
          </div>
        </li>
      </ol>
    </section>

    <section class="public-home-content">
      <div class="public-home-main">
        <DataNotice
          :using-fallback="featuredCoursesResource.usingFallback.value"
          :fallback-title="t('landing.previewTitle')"
          :fallback-message="t('landing.previewMessage')"
        />

        <section class="panel public-home-panel" aria-labelledby="landing-featured-title">
          <div class="section-header">
            <h2 id="landing-featured-title" class="section-title">{{ t('landing.featuredCourses') }}</h2>
            <button class="btn btn-link" type="button" @click="openCatalog">
              {{ t('landing.viewAll') }}
              <IconGlyph name="chevronRight" :size="16" />
            </button>
          </div>

          <StatePanel
            v-if="featuredCoursesResource.loading.value"
            tone="loading"
            icon="progress"
            :title="t('landing.loadingCourses')"
            :message="t('landing.loadingCoursesHint')"
            compact
          />
          <StatePanel
            v-else-if="featuredCoursesResource.error.value && !featuredCourses.length"
            tone="error"
            icon="alert"
            :title="t('landing.unavailableTitle')"
            :message="featuredCoursesResource.error.value"
            compact
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="featuredCoursesResource.reload">
                {{ t('landing.retry') }}
              </button>
            </template>
          </StatePanel>
          <ul v-else-if="featuredCourses.length" class="public-featured-grid">
            <li v-for="(course, index) in featuredCourses" :key="course.id" class="public-featured-card">
              <button
                class="public-featured-card-button"
                type="button"
                :aria-label="t('landing.openCourse', { title: course.title })"
                @click="viewCourse(course.id)"
              >
                <span class="public-featured-thumb" aria-hidden="true">
                  <CourseSceneThumb :size="132" :variant="featuredThumb(index)" />
                </span>
                <span class="public-featured-copy">
                  <span class="chip">{{ course.level.name }}</span>
                  <strong class="public-featured-title">{{ course.title }}</strong>
                  <span class="public-featured-description">{{ featuredCopy(course) }}</span>
                </span>
                <span class="public-featured-meta">
                  <span>{{ course.category.name }}</span>
                  <span>{{ course.teacher.displayName }}</span>
                </span>
              </button>
            </li>
          </ul>
          <p v-else class="public-featured-empty">
            <IconGlyph name="courses" :size="20" />
            <span>{{ t('landing.noCourses') }}</span>
          </p>
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

    <section id="safety" class="public-safety-banner" aria-labelledby="landing-safety-title">
      <span class="public-safety-icon" aria-hidden="true"><IconGlyph name="shield" :size="28" /></span>
      <div class="public-safety-copy">
        <h2 id="landing-safety-title">{{ t('landing.safetyBannerTitle') }}</h2>
        <p>{{ t('landing.safetyBannerCopy') }}</p>
      </div>
      <div class="public-safety-scene" aria-hidden="true">
        <span class="public-safety-hill" />
        <span class="public-safety-pagoda" />
      </div>
    </section>

    <section id="support" class="panel public-support-panel" aria-labelledby="landing-support-title">
      <div class="section-header">
        <div class="public-support-heading">
          <span class="page-eyebrow">{{ t('landing.support') }}</span>
          <h2 id="landing-support-title" class="section-title">{{ t('landing.supportTitle') }}</h2>
        </div>
        <button class="btn btn-primary" type="button" @click="openSupportSignIn">
          {{ t('landing.signInSupport') }}
        </button>
      </div>
      <ul class="public-support-grid">
        <li v-for="item in supportOptions" :key="item.title" class="public-support-card">
          <h3>{{ item.title }}</h3>
          <p>{{ item.copy }}</p>
        </li>
      </ul>
    </section>

    <footer id="about" class="public-footer">
      <div class="public-footer-top">
        <div class="public-footer-brand">
          <AppBrand />
          <strong class="public-footer-official-title" data-testid="official-title">{{
            t('landing.officialTitle')
          }}</strong>
          <p class="public-footer-about">{{ t('landing.aboutCopy') }}</p>
        </div>

        <div class="public-footer-links">
          <nav class="public-footer-column" :aria-label="t('landing.explore')">
            <h2>{{ t('landing.explore') }}</h2>
            <a href="/courses" @click.prevent="openCatalog">{{ t('landing.courses') }}</a>
            <a href="#how-it-works">{{ t('landing.howItWorksLink') }}</a>
          </nav>
          <nav class="public-footer-column" :aria-label="t('landing.company')">
            <h2>{{ t('landing.company') }}</h2>
            <a href="#about">{{ t('landing.aboutUs') }}</a>
            <a href="#support">{{ t('landing.contactUs') }}</a>
          </nav>
          <nav class="public-footer-column" :aria-label="t('landing.safety')">
            <h2>{{ t('landing.safety') }}</h2>
            <a href="/responsible-ai" @click.prevent="openResponsibleAi">{{ t('landing.responsibleAi') }}</a>
            <a href="/privacy" @click.prevent="openPrivacy">{{ t('landing.privacy') }}</a>
            <a href="/terms" @click.prevent="openTerms">{{ t('landing.terms') }}</a>
          </nav>
          <nav class="public-footer-column" :aria-label="t('landing.support')">
            <h2>{{ t('landing.support') }}</h2>
            <a href="#support">{{ t('landing.helpCenter') }}</a>
            <a href="#support">{{ t('landing.contactSupport') }}</a>
          </nav>
        </div>
      </div>

      <div class="public-footer-bottom">
        <span>&copy; {{ copyrightYear }} MandarinSpark. {{ t('landing.rights') }}</span>
        <span>{{ t('landing.madeForLearners') }}</span>
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
import type { CourseSceneVariant } from '@/components/common/courseSceneVariant'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import AppBrand from '@/components/layout/AppBrand.vue'
import PublicFeatureCard from '@/components/public/PublicFeatureCard.vue'
import PublicInfoBanner from '@/components/public/PublicInfoBanner.vue'
import { useResource } from '@/composables/useResource'
import { publicCourses } from '@/demo/data'
import { landingMessages } from '@/plugins/landingMessages'
import '@/styles/landing.css'
import type { PublicCourseSummary } from '@/types/api'

type FeatureTone = 'blue' | 'gold' | 'green' | 'orange' | 'pink' | 'purple'

const router = useRouter()
const { t } = useI18n({
  messages: {
    en: { landing: landingMessages.en },
    ms: { landing: landingMessages.ms },
    'zh-CN': { landing: landingMessages['zh-CN'] },
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
const featuredCourses = computed<PublicCourseSummary[]>(() => featuredCoursesResource.data.value?.items ?? [])

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

const heroChecks = computed(() => [t('landing.ageRange'), t('landing.beginnerFriendly'), t('landing.confidence')])

const heroProof = computed(() => [
  { value: t('landing.proofPathValue'), label: t('landing.proofPathLabel') },
  { value: t('landing.proofAiValue'), label: t('landing.proofAiLabel') },
  { value: t('landing.proofRewardsValue'), label: t('landing.proofRewardsLabel') },
])

const learnerJourney = computed(() => [
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
    title: t('landing.supportStudentsTitle'),
    copy: t('landing.supportStudentsCopy'),
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

const featuredThumbs = ['panda', 'people', 'pagoda'] as const

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

function openResponsibleAi(): void {
  void router.push('/responsible-ai')
}

function openTerms(): void {
  void router.push('/terms')
}
</script>
