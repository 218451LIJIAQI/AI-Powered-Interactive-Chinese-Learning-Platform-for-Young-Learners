<template>
  <div class="page-stack public-course-detail-page">
    <button class="btn btn-ghost public-course-detail-back" type="button" @click="router.back()">Back to catalog</button>

    <DataNotice :using-fallback="resource.usingFallback.value" :error="actionError || resource.error.value" :success="actionSuccess" />

    <section class="hero-card light public-course-detail-hero">
      <span class="public-course-detail-cloud public-course-detail-cloud--one" />
      <span class="public-course-detail-cloud public-course-detail-cloud--two" />
      <div class="public-course-detail-hero-grid">
        <div class="hero-copy light public-course-detail-copy">
          <div class="public-course-detail-kicker">
            <span>MandarinSpark Course</span>
            <span>{{ course.category.name }}</span>
          </div>
          <div class="row public-course-detail-tags">
            <StatusPill :label="course.level.name" tone="primary" />
            <StatusPill :label="course.learningType" tone="info" />
            <StatusPill :label="course.accessType" tone="warning" />
          </div>
          <h2>{{ course.title }}</h2>
          <p>{{ course.description }}</p>
          <div class="hero-actions public-course-detail-actions">
            <button class="btn btn-accent" type="button" :disabled="primaryActionDisabled" @click="handlePrimaryAction">
              {{ primaryActionLabel }}
            </button>
            <button class="btn btn-secondary" type="button" :disabled="secondaryActionDisabled" @click="handleSecondaryAction">
              {{ secondaryActionLabel }}
            </button>
          </div>
        </div>
        <div class="public-course-detail-showcase">
          <CourseSceneThumb :size="240" :variant="courseSceneVariant" />
          <div class="public-course-detail-price-card">
            <div class="row-between">
              <strong>Teacher</strong>
              <StatusPill :label="course.teacher.verified ? 'Verified' : 'Public'" tone="success" />
            </div>
            <p>{{ course.teacher.displayName }}</p>
            <strong>{{ course.accessType === 'FREE' ? 'Free' : formatCurrency(course.price) }}</strong>
            <span>{{ course.accessType === 'FREE' ? 'Start learning after signing in.' : 'Secure checkout unlocks student access.' }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="public-course-detail-summary">
      <article v-for="item in summaryCards" :key="item.label">
        <span><IconGlyph :name="item.icon" :size="20" /></span>
        <div>
          <strong>{{ item.value }}</strong>
          <small>{{ item.label }}</small>
        </div>
      </article>
    </section>

    <section class="split-grid public-course-detail-body">
      <div class="panel stack public-course-detail-inside-card">
        <div class="student-section-heading">
          <span><IconGlyph name="learn" :size="20" /></span>
          <strong>What's inside</strong>
        </div>
        <p class="muted">{{ course.basicContent || 'Structured beginner material plus interactive practice.' }}</p>
        <div class="list public-course-detail-item-list">
          <div v-for="item in course.packageItems" :key="`${item.type}-${item.contentId}`" class="list-item public-course-detail-item">
            <div class="row">
              <div class="media-badge small">{{ item.type === 'MODULE' ? '模' : '课' }}</div>
              <div class="stack">
                <strong>{{ item.title }}</strong>
                <span class="muted">Included item {{ item.displayOrder }}</span>
              </div>
            </div>
            <span class="tag">{{ item.type }}</span>
          </div>
        </div>
      </div>

      <aside class="stack public-course-detail-side">
        <div class="panel stack public-course-detail-cta-card">
          <strong>Course call to action</strong>
          <p>{{ course.callToAction || 'Begin with one lesson, one activity, and one confident step at a time.' }}</p>
        </div>
        <div class="panel stack public-course-detail-alignment-card">
          <strong>Proposal alignment</strong>
          <ul>
            <li>Structured beginner Mandarin materials</li>
            <li>Teacher-guided content and feedback</li>
            <li>Purchases continue through the authenticated student checkout flow</li>
            <li>No built-in video conferencing; live support uses external meeting links</li>
          </ul>
        </div>
        <div class="panel stack public-course-detail-support-card">
          <strong>Need more support?</strong>
          <p>AI helper suggestions are optional and student-facing only.</p>
          <button class="btn btn-primary" type="button" @click="goToAiHelperSignIn">Open AI Helper</button>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * Single course / module / package detail page accessible from the
 * catalogue or a marketing link.
 *
 * <h2>Visibility</h2>
 * <p>The endpoint returns content only for PUBLISHED items authored
 * by APPROVED teachers — so an unauthenticated visitor cannot see
 * draft or hidden material via URL guessing.</p>
 *
 * <h2>Paid vs. free</h2>
 * <p>Paid course pages display only price + teaser; the lesson body
 * is locked behind the checkout flow. The action button routes
 * through {@link AuthPage} when the visitor is not signed in.</p>
 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { publicApi, studentApi } from '@/api/services'
import CourseSceneThumb from '@/components/common/CourseSceneThumb.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { publicCourseDetails } from '@/demo/data'
import { useResource } from '@/composables/useResource'
import { useAuthStore } from '@/stores/auth'
import type { CheckoutResponse, PublicCourseDetail } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatCurrency } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const courseId = computed(() => String(route.params.courseId))
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const submittingBuyNow = ref(false)
const submittingCart = ref(false)

function readDemoCourseDetail() {
  return publicCourseDetails[courseId.value] ?? null
}

const emptyCourse = computed<PublicCourseDetail>(() => ({
  id: courseId.value,
  contentId: 0,
  type: 'LESSON' as const,
  title: 'Course unavailable',
  description: 'The requested course could not be loaded from the current API route.',
  teacher: {
    teacherId: 0,
    displayName: 'Unavailable',
    verified: false,
  },
  level: {
    id: 0,
    name: 'Unavailable',
    description: '',
  },
  category: {
    id: 0,
    name: 'Unavailable',
    description: '',
  },
  learningType: 'RECORDED' as const,
  accessType: 'FREE' as const,
  price: 0,
  locked: true,
  status: 'DRAFT' as const,
  basicContent: 'Course content is unavailable right now.',
  packageItems: [],
  callToAction: 'Try again after the API route becomes available.',
}))

const resource = useResource(
  () => {
    const previewDetail = readDemoCourseDetail()
    if (auth.isPreviewSession && previewDetail) {
      return Promise.resolve(previewDetail)
    }

    return publicApi.getCourseDetail(courseId.value)
  },
  () => readDemoCourseDetail() ?? emptyCourse.value,
)

const course = computed(
  () => resource.data.value ?? emptyCourse.value,
)
const courseSceneVariant = computed(() => {
  const title = course.value.title.toLowerCase()
  if (title.includes('family')) return 'family'
  if (title.includes('travel')) return 'travel'
  if (title.includes('culture')) return 'pagoda'
  if (title.includes('panda')) return 'panda'
  return 'general'
})
const summaryCards = computed(() => [
  { icon: 'trophy', label: 'Level', value: course.value.level.name },
  { icon: 'globe', label: 'Category', value: course.value.category.name },
  { icon: 'content', label: 'Included items', value: String(course.value.packageItems.length) },
  { icon: 'teacher', label: 'Teacher', value: course.value.teacher.verified ? 'Verified' : 'Public' },
])
const primaryActionLabel = computed(() => {
  if (!auth.isAuthenticated) return course.value.accessType === 'FREE' ? 'Sign in to start' : 'Sign in to purchase'
  if (course.value.accessType === 'FREE') return 'Start learning'
  return submittingBuyNow.value ? 'Starting checkout...' : 'Buy now'
})
const secondaryActionLabel = computed(() => {
  if (!auth.isAuthenticated) return 'Sign in and return'
  if (course.value.accessType === 'FREE') return 'Ask AI Helper'
  return submittingCart.value ? 'Adding...' : 'Add to cart'
})
const primaryActionDisabled = computed(() => submittingBuyNow.value || submittingCart.value || course.value.contentId <= 0)
const secondaryActionDisabled = computed(() => submittingBuyNow.value || submittingCart.value || course.value.contentId <= 0)
const courseReadOnly = computed(() => resource.usingFallback.value)

function goToAuth() {
  router.push({ name: 'auth', query: { redirect: route.fullPath } })
}

function goToStudentLearn() {
  router.push({ name: 'student-learn' })
}

function handleCheckoutResponse(response: CheckoutResponse) {
  if (!response.paymentAvailable) {
    actionError.value = response.message || 'Payment provider is unavailable. Please try again later.'
    return
  }

  if (response.checkoutUrl) {
    window.location.assign(response.checkoutUrl)
    return
  }

  router.push({ name: 'payment-success', query: { paymentId: String(response.paymentId) } })
}

async function handlePrimaryAction() {
  actionError.value = null
  actionSuccess.value = null

  if (!auth.isAuthenticated) {
    goToAuth()
    return
  }

  if (course.value.accessType === 'FREE') {
    goToStudentLearn()
    return
  }

  if (courseReadOnly.value) {
    actionError.value = 'Preview course data cannot start checkout. Refresh live course data before purchasing.'
    return
  }

  submittingBuyNow.value = true
  try {
    const response = await studentApi.buyNow({ itemType: course.value.type, itemId: course.value.contentId })
    handleCheckoutResponse(response)
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to start checkout for this course.')
  } finally {
    submittingBuyNow.value = false
  }
}

async function handleSecondaryAction() {
  actionError.value = null
  actionSuccess.value = null

  if (!auth.isAuthenticated) {
    goToAuth()
    return
  }

  if (course.value.accessType === 'FREE') {
    goToAiHelperSignIn()
    return
  }

  if (courseReadOnly.value) {
    actionError.value = 'Preview course data cannot be added to the cart. Refresh live course data first.'
    return
  }

  submittingCart.value = true
  try {
    await studentApi.addCartItem({ itemType: course.value.type, itemId: course.value.contentId })
    actionSuccess.value = `${course.value.title} was added to your cart.`
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to add this course to your cart.')
  } finally {
    submittingCart.value = false
  }
}

function goToAiHelperSignIn() {
  if (auth.isAuthenticated) {
    router.push('/student/ai-helper')
    return
  }

  router.push({ name: 'auth', query: { redirect: '/student/ai-helper' } })
}
</script>
