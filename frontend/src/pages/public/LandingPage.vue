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
            <span>Young Learners</span>
            <span>For Young Learners Ages 7-12</span>
          </div>

          <h1>
            Spark a love for <span class="accent-purple">Mandarin</span>.<br />
            Open a world of <span class="accent-orange">possibilities</span>.
          </h1>

          <p>MandarinSpark makes learning Mandarin fun, effective, and safe for young learners anywhere, anytime.</p>

          <div class="public-hero-actions">
            <button class="btn btn-primary public-hero-button" type="button" @click="router.push('/courses')">
              <span>Explore Courses</span>
            </button>
            <button
              class="btn btn-accent public-hero-button public-hero-button--gold"
              type="button"
              @click="router.push('/auth')"
            >
              <span>Start Learning</span>
            </button>
          </div>

          <div class="public-hero-checks">
            <span>Ages 7-12</span>
            <span>Beginner Friendly</span>
            <span>Learn with Confidence</span>
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
            <small>Hello!</small>
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

    <section id="how-it-works" class="public-journey-strip" aria-label="How MandarinSpark works">
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
          fallback-title="Preview featured courses"
          fallback-message="These featured courses are sample catalog records for the current preview session."
          error-title="Featured courses unavailable"
        />

        <section class="panel public-home-panel">
          <div class="public-section-head">
            <h2>Featured Courses</h2>
            <button class="public-link-button" type="button" @click="router.push('/courses')">View All Courses</button>
          </div>

          <div v-if="featuredCourses.length" class="public-featured-grid">
            <article v-for="(course, index) in featuredCourses" :key="course.id" class="public-featured-card">
              <button
                class="public-featured-card-button"
                type="button"
                :aria-label="`Open featured course: ${course.title}`"
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
          <div v-else class="public-featured-empty">
            Featured courses will appear here after teachers publish public content.
          </div>

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
          title="Safe & Secure for Kids"
          description="We follow child-safe design standards to protect every learner's experience."
          badge="Safe"
          tone="green"
        />
        <PublicInfoBanner
          icon="verified"
          title="Verified Teachers You Can Trust"
          description="All teachers go through a strict verification process before publishing public content."
          badge="Verified"
          tone="purple"
        />
        <PublicInfoBanner
          icon="globe"
          title="Web-Based Learning"
          description="MandarinSpark works in the browser so families, learners, teachers, and admins can stay connected from one place."
          badge="Web"
          tone="blue"
        />
      </aside>
    </section>

    <section id="safety" class="public-safety-banner">
      <div class="public-safety-icon">
        <IconGlyph name="shield" :size="32" />
      </div>
      <div class="public-safety-copy">
        <strong>A safe, supportive learning environment</strong>
        <p>We follow strict child-safety standards to ensure every learner can grow with confidence.</p>
      </div>
      <div class="public-safety-scene">
        <div class="public-safety-hill" />
        <div class="public-safety-pagoda" />
      </div>
    </section>

    <section id="support" class="panel public-support-panel">
      <div class="public-section-head">
        <div>
          <span class="legal-eyebrow">Support</span>
          <h2>Need help using MandarinSpark?</h2>
        </div>
        <button class="btn btn-primary" type="button" @click="router.push({ path: '/auth', query: { tab: 'signin' } })">
          Sign in for support
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
          <strong>Explore</strong>
          <a href="/courses" @click.prevent="router.push('/courses')">Courses</a>
          <a href="#how-it-works">How It Works</a>
        </div>
        <div class="public-footer-column">
          <strong>Company</strong>
          <a href="#about">About Us</a>
          <a href="#support">Contact Us</a>
        </div>
        <div class="public-footer-column">
          <strong>Safety</strong>
          <a href="#safety">Child Safety</a>
          <a href="/privacy" @click.prevent="router.push('/privacy')">Privacy Policy</a>
          <a href="/terms" @click.prevent="router.push('/terms')">Terms</a>
        </div>
        <div class="public-footer-column">
          <strong>Support</strong>
          <a href="#support">Help Center</a>
          <a href="#support">Contact Support</a>
        </div>
      </div>

      <div class="public-footer-meta">
        <div class="public-footer-copy">
          <span>&copy; {{ copyrightYear }} MandarinSpark. All rights reserved.</span>
          <span>Made for young learners.</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
/**
 * Marketing landing page — the unauthenticated home route.
 *
 * <p>Composed entirely of {@link PublicFeatureCard},
 * {@link PublicInfoBanner}, {@link PandaIllustration} and other
 * pure-CSS visuals so the initial paint stays light and there are
 * no remote image dependencies for the first impression.</p>
 *
 * <p>Routes new visitors into {@link CatalogPage} for discovery and
 * {@link AuthPage} for sign-up / sign-in. The page is purely
 * presentational apart from a lightweight featured-course fetch
 * via {@link useResource} — no router-guard handshake.</p>
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'

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
import type { PublicCourseSummary } from '@/types/api'

type CourseSceneVariant = 'panda' | 'family' | 'pagoda' | 'travel' | 'general'

const router = useRouter()
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

const featureStrip = [
  {
    icon: 'status',
    title: 'Beginner Mandarin Made Easy',
    description: 'Start from the basics with step-by-step lessons.',
    tone: 'purple' as const,
  },
  {
    icon: 'play',
    title: 'Interactive Activities',
    description: 'Fun games and quizzes that build real skills.',
    tone: 'green' as const,
  },
  {
    icon: 'ai',
    title: 'AI-Assisted Support',
    description: 'Get help anytime with SparkBot AI Helper.',
    tone: 'blue' as const,
  },
  {
    icon: 'live',
    title: 'Live & Recorded Learning',
    description: 'Join live classes or learn at your own pace.',
    tone: 'orange' as const,
  },
  {
    icon: 'trophy',
    title: 'Progress Tracking & Rewards',
    description: 'Earn stars, badges, and celebrate your growth.',
    tone: 'gold' as const,
  },
  {
    icon: 'teacher',
    title: 'Teacher-Guided Learning',
    description: 'Learn with verified teachers who care about you.',
    tone: 'pink' as const,
  },
]

const heroProof = [
  { value: 'Live + recorded', label: 'Flexible learning paths for families' },
  { value: 'AI support', label: 'On-demand practice help between lessons' },
  { value: 'Rewards', label: 'Visible growth with stars, badges, and streaks' },
]

const familyJourney = [
  {
    step: '1',
    title: 'Explore with confidence',
    copy: 'Families can compare course styles, age fit, and teacher guidance before signing in.',
  },
  {
    step: '2',
    title: 'Learn in small wins',
    copy: 'Students move from lessons to practice, class support, and rewards without losing momentum.',
  },
  {
    step: '3',
    title: 'Stay supported',
    copy: 'Teachers, parents, and admins all see the progress signals they need to keep learners growing.',
  },
]

const supportOptions = [
  {
    title: 'Families and students',
    copy: 'Sign in to review learning progress, feedback, payments, refunds, rewards, and notifications from the student profile area.',
  },
  {
    title: 'Teachers',
    copy: 'Use the teacher profile, verification, class, content, feedback, and earnings pages to resolve account and teaching workflow questions.',
  },
  {
    title: 'Platform operations',
    copy: 'Admins can review users, teacher verification, moderation, audit logs, finance, notifications, and safety settings from the admin workspace.',
  },
]

function viewCourse(id: string) {
  router.push(`/courses/${id}`)
}

const featuredThumbs: CourseSceneVariant[] = ['panda', 'family', 'pagoda']

function featuredThumb(index: number): CourseSceneVariant {
  return featuredThumbs[index % featuredThumbs.length] ?? 'panda'
}

function featuredCopy(course: PublicCourseSummary) {
  return (
    course.shortDescription?.trim() ||
    `${course.teacher.displayName} teaches this ${course.category.name.toLowerCase()} course.`
  )
}
</script>
