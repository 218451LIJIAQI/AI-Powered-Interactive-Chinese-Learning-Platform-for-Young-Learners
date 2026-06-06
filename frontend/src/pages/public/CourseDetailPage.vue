<template>
  <div class="page-stack public-course-detail-page">
    <button class="btn btn-ghost public-course-detail-back" type="button" @click="goBackToCatalog">
      Back to catalog
    </button>

    <DataNotice
      :using-fallback="resource.usingFallback.value"
      :error="noticeError"
      :success="actionSuccess"
    />

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
            <button
              class="btn btn-secondary"
              type="button"
              :disabled="secondaryActionDisabled"
              @click="handleSecondaryAction"
            >
              {{ secondaryActionLabel }}
            </button>
          </div>
        </div>
        <div class="public-course-detail-showcase">
          <CourseSceneThumb :size="240" :variant="courseSceneVariant" />
          <div class="public-course-detail-price-card">
            <div class="row-between">
              <strong>Teacher</strong>
              <StatusPill :label="teacherVerificationLabel" tone="success" />
            </div>
            <p>{{ course.teacher.displayName }}</p>
            <strong>{{ priceLabel }}</strong>
            <span>{{ priceHelp }}</span>
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
          <div
            v-for="item in course.packageItems"
            :key="`${item.type}-${item.contentId}`"
            class="list-item public-course-detail-item"
          >
            <div class="row">
              <IconGlyph :name="packageItemIcon(item.type)" :size="18" />
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
          <p>{{ displayedCallToAction }}</p>
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
          <button class="btn btn-primary" type="button" :disabled="studentOnlyViewer" @click="goToAiHelperSignIn">
            {{ aiHelperButtonLabel }}
          </button>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup lang="ts">
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
import { safeHttpUrl } from '@/utils/url'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const courseId = computed(() => String(route.params.courseId))
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const submittingBuyNow = ref(false)
const submittingCart = ref(false)
const studentOnlyViewer = computed(() => auth.isAuthenticated && !auth.hasRole('STUDENT'))
const canUseStudentActions = computed(() => auth.isAuthenticated && auth.hasRole('STUDENT'))

type PackageItemIcon = 'content' | 'learn'

function readDemoCourseDetail(): PublicCourseDetail | null {
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
  currencyCode: 'MYR',
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

const course = computed(() => resource.data.value ?? emptyCourse.value)
const noticeError = computed(() => actionError.value || resource.error.value)
const displayedCallToAction = computed(() => {
  if (canUseStudentActions.value) {
    return course.value.accessType === 'FREE'
      ? 'Start this lesson from your student learning dashboard.'
      : 'Complete secure checkout to unlock this course in your student account.'
  }
  if (studentOnlyViewer.value) return 'Students can enrol and purchase through a student account.'
  return course.value.callToAction || 'Begin with one lesson, one activity, and one confident step at a time.'
})
const courseSceneVariant = computed<'family' | 'general' | 'pagoda' | 'panda' | 'travel'>(() => {
  const title = course.value.title.toLowerCase()
  if (title.includes('family')) return 'family'
  if (title.includes('travel')) return 'travel'
  if (title.includes('culture')) return 'pagoda'
  if (title.includes('panda')) return 'panda'
  return 'general'
})
const teacherVerificationLabel = computed(() => (course.value.teacher.verified ? 'Verified' : 'Public'))
const priceLabel = computed(() =>
  course.value.accessType === 'FREE' ? 'Free' : formatCurrency(course.value.price, course.value.currencyCode),
)
const priceHelp = computed(() =>
  course.value.accessType === 'FREE'
    ? 'Start learning after signing in.'
    : 'Secure checkout unlocks student access.',
)
const aiHelperButtonLabel = computed(() => (studentOnlyViewer.value ? 'Student AI only' : 'Open AI Helper'))
const summaryCards = computed(() => [
  { icon: 'trophy', label: 'Level', value: course.value.level.name },
  { icon: 'globe', label: 'Category', value: course.value.category.name },
  { icon: 'content', label: 'Included items', value: String(course.value.packageItems.length) },
  { icon: 'teacher', label: 'Teacher', value: teacherVerificationLabel.value },
])
const primaryActionLabel = computed(() => {
  if (studentOnlyViewer.value)
    return course.value.accessType === 'FREE' ? 'Student access only' : 'Student checkout only'
  if (!auth.isAuthenticated) return course.value.accessType === 'FREE' ? 'Sign in to start' : 'Sign in to purchase'
  if (course.value.accessType === 'FREE') return 'Start learning'
  return submittingBuyNow.value ? 'Starting checkout...' : 'Buy now'
})
const secondaryActionLabel = computed(() => {
  if (studentOnlyViewer.value) return course.value.accessType === 'FREE' ? 'Student AI only' : 'Student checkout only'
  if (!auth.isAuthenticated) return 'Sign in and return'
  if (course.value.accessType === 'FREE') return 'Ask AI Helper'
  return submittingCart.value ? 'Adding...' : 'Add to cart'
})
const primaryActionDisabled = computed(
  () => studentOnlyViewer.value || submittingBuyNow.value || submittingCart.value || course.value.contentId <= 0,
)
const secondaryActionDisabled = computed(
  () => studentOnlyViewer.value || submittingBuyNow.value || submittingCart.value || course.value.contentId <= 0,
)
const courseReadOnly = computed(() => resource.usingFallback.value)

function goToAuth(): void {
  void router.push({ name: 'auth', query: { redirect: route.fullPath } })
}

function goToStudentLearn(): void {
  void router.push({ name: 'student-learn' })
}

function goBackToCatalog(): void {
  if (window.history.length > 1) {
    router.back()
  } else {
    void router.push({ name: 'catalog' })
  }
}

function handleCheckoutResponse(response: CheckoutResponse): void {
  if (!response.paymentAvailable) {
    actionError.value = response.message || 'Payment provider is unavailable. Please try again later.'
    return
  }

  const validatedUrl = safeHttpUrl(response.checkoutUrl)
  if (validatedUrl) {
    window.location.assign(validatedUrl)
    return
  }

  if (response.paymentId != null) {
    openPaymentSuccess(response.paymentId)
    return
  }

  actionError.value = 'Checkout completed but no redirect or payment reference was returned. Please contact support.'
}

async function handlePrimaryAction(): Promise<void> {
  if (submittingBuyNow.value || submittingCart.value) return

  actionError.value = null
  actionSuccess.value = null

  if (!canUseStudentActions.value) {
    if (studentOnlyViewer.value) return
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

async function handleSecondaryAction(): Promise<void> {
  if (submittingBuyNow.value || submittingCart.value) return

  actionError.value = null
  actionSuccess.value = null

  if (!canUseStudentActions.value) {
    if (studentOnlyViewer.value) return
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

function goToAiHelperSignIn(): void {
  if (studentOnlyViewer.value) return

  if (canUseStudentActions.value) {
    openStudentAiHelper()
    return
  }

  void router.push({ name: 'auth', query: { redirect: '/student/ai-helper' } })
}

function openStudentAiHelper(): void {
  void router.push('/student/ai-helper')
}

function openPaymentSuccess(paymentId: number): void {
  void router.push({ name: 'payment-success', query: { paymentId: String(paymentId) } })
}

function packageItemIcon(type: string): PackageItemIcon {
  return type === 'MODULE' ? 'content' : 'learn'
}
</script>
