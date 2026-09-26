<template>
  <div class="page-stack public-course-detail-page">
    <button class="btn btn-ghost public-course-detail-back" type="button" @click="goBackToCatalog">
      {{ t('courseDetail.back') }}
    </button>

    <DataNotice
      :using-fallback="resource.usingFallback.value"
      :error="noticeError"
      :success="actionSuccess"
      :fallback-title="t('courseDetail.fallbackTitle')"
      :fallback-message="t('courseDetail.fallbackMessage')"
      :error-title="t('courseDetail.errorTitle')"
      :success-title="t('courseDetail.successTitle')"
    />

    <section class="hero-card light public-course-detail-hero">
      <span class="public-course-detail-cloud public-course-detail-cloud--one" />
      <span class="public-course-detail-cloud public-course-detail-cloud--two" />
      <div class="public-course-detail-hero-grid">
        <div class="hero-copy light public-course-detail-copy">
          <div class="public-course-detail-kicker">
            <span>{{ t('courseDetail.courseEyebrow') }}</span>
            <span>{{ course.category.name }}</span>
          </div>
          <div class="row public-course-detail-tags">
            <StatusPill :label="course.level.name" tone="primary" />
            <StatusPill :label="learningTypeLabel" tone="info" />
            <StatusPill :label="accessTypeLabel" tone="warning" />
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
              <strong>{{ t('courseDetail.teacher') }}</strong>
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
          <strong>{{ t('courseDetail.inside') }}</strong>
        </div>
        <p class="muted">{{ course.basicContent || t('courseDetail.basicFallback') }}</p>
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
                <span class="muted">{{ t('courseDetail.includedItem', { order: item.displayOrder }) }}</span>
              </div>
            </div>
            <span class="tag">{{ packageItemTypeLabel(item.type) }}</span>
          </div>
        </div>
      </div>

      <aside class="stack public-course-detail-side">
        <div class="panel stack public-course-detail-cta-card">
          <strong>{{ t('courseDetail.ctaTitle') }}</strong>
          <p>{{ displayedCallToAction }}</p>
        </div>
        <div class="panel stack public-course-detail-alignment-card">
          <strong>{{ t('courseDetail.expectTitle') }}</strong>
          <ul>
            <li>{{ t('courseDetail.expectMaterials') }}</li>
            <li>{{ t('courseDetail.expectFeedback') }}</li>
            <li>{{ t('courseDetail.expectCheckout') }}</li>
            <li>{{ t('courseDetail.expectMeeting') }}</li>
          </ul>
        </div>
        <div class="panel stack public-course-detail-support-card">
          <strong>{{ t('courseDetail.supportTitle') }}</strong>
          <p>{{ t('courseDetail.supportCopy') }}</p>
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
import { useI18n } from 'vue-i18n'

import { publicApi, studentApi } from '@/api/services'
import CourseSceneThumb from '@/components/common/CourseSceneThumb.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { publicCourseDetails } from '@/demo/data'
import { courseDetailMessages } from '@/plugins/courseDetailMessages'
import { useResource } from '@/composables/useResource'
import { useAuthStore } from '@/stores/auth'
import type { CheckoutResponse, PublicCourseDetail } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatCurrency } from '@/utils/format'
import { safeHttpUrl } from '@/utils/url'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n({
  messages: {
    en: { courseDetail: courseDetailMessages.en },
    ms: { courseDetail: courseDetailMessages.ms },
    zh: { courseDetail: courseDetailMessages.zh },
  },
})
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
  title: t('courseDetail.unavailableTitle'),
  description: t('courseDetail.unavailableCopy'),
  teacher: {
    teacherId: 0,
    displayName: t('courseDetail.unavailableName'),
    verified: false,
  },
  level: {
    id: 0,
    name: t('courseDetail.unavailableName'),
    description: '',
  },
  category: {
    id: 0,
    name: t('courseDetail.unavailableName'),
    description: '',
  },
  learningType: 'RECORDED' as const,
  accessType: 'FREE' as const,
  price: 0,
  currencyCode: 'MYR',
  locked: true,
  status: 'DRAFT' as const,
  basicContent: t('courseDetail.unavailableContent'),
  packageItems: [],
  callToAction: t('courseDetail.unavailableAction'),
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
      ? t('courseDetail.startFromDashboard')
      : t('courseDetail.unlockWithCheckout')
  }
  if (studentOnlyViewer.value) return t('courseDetail.studentAccountRequired')
  return course.value.callToAction || t('courseDetail.defaultAction')
})
const courseSceneVariant = computed<'family' | 'general' | 'pagoda' | 'panda' | 'travel'>(() => {
  const title = course.value.title.toLowerCase()
  if (title.includes('family')) return 'family'
  if (title.includes('travel')) return 'travel'
  if (title.includes('culture')) return 'pagoda'
  if (title.includes('panda')) return 'panda'
  return 'general'
})
const teacherVerificationLabel = computed(() =>
  course.value.teacher.verified ? t('courseDetail.verified') : t('courseDetail.public'),
)
const priceLabel = computed(() =>
  course.value.accessType === 'FREE'
    ? t('courseDetail.free')
    : formatCurrency(course.value.price, course.value.currencyCode),
)
const priceHelp = computed(() =>
  course.value.accessType === 'FREE' ? t('courseDetail.freeHelp') : t('courseDetail.paidHelp'),
)
const learningTypeLabel = computed(() => {
  const key = { RECORDED: 'recorded', LIVE: 'live', HYBRID: 'hybrid' }[course.value.learningType]
  return t(`courseDetail.${key}`)
})
const accessTypeLabel = computed(() =>
  t(course.value.accessType === 'FREE' ? 'courseDetail.free' : 'courseDetail.paid'),
)
const aiHelperButtonLabel = computed(() =>
  studentOnlyViewer.value ? t('courseDetail.studentAiOnly') : t('courseDetail.openAi'),
)
const summaryCards = computed(() => [
  { icon: 'trophy', label: t('courseDetail.level'), value: course.value.level.name },
  { icon: 'globe', label: t('courseDetail.category'), value: course.value.category.name },
  { icon: 'content', label: t('courseDetail.includedItems'), value: String(course.value.packageItems.length) },
  { icon: 'teacher', label: t('courseDetail.teacher'), value: teacherVerificationLabel.value },
])
const primaryActionLabel = computed(() => {
  if (studentOnlyViewer.value)
    return course.value.accessType === 'FREE'
      ? t('courseDetail.studentAccessOnly')
      : t('courseDetail.studentCheckoutOnly')
  if (!auth.isAuthenticated)
    return course.value.accessType === 'FREE' ? t('courseDetail.signInStart') : t('courseDetail.signInPurchase')
  if (course.value.accessType === 'FREE') return t('courseDetail.startLearning')
  return submittingBuyNow.value ? t('courseDetail.startingCheckout') : t('courseDetail.buyNow')
})
const secondaryActionLabel = computed(() => {
  if (studentOnlyViewer.value)
    return course.value.accessType === 'FREE' ? t('courseDetail.studentAiOnly') : t('courseDetail.studentCheckoutOnly')
  if (!auth.isAuthenticated) return t('courseDetail.signInReturn')
  if (course.value.accessType === 'FREE') return t('courseDetail.askAi')
  return submittingCart.value ? t('courseDetail.adding') : t('courseDetail.addToCart')
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
    actionError.value = response.message || t('courseDetail.paymentUnavailable')
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

  actionError.value = t('courseDetail.missingCheckoutReference')
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
    actionError.value = t('courseDetail.previewCheckoutBlocked')
    return
  }

  submittingBuyNow.value = true
  try {
    const response = await studentApi.buyNow({ itemType: course.value.type, itemId: course.value.contentId })
    handleCheckoutResponse(response)
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('courseDetail.checkoutFailed'))
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
    actionError.value = t('courseDetail.previewCartBlocked')
    return
  }

  submittingCart.value = true
  try {
    await studentApi.addCartItem({ itemType: course.value.type, itemId: course.value.contentId })
    actionSuccess.value = t('courseDetail.addedToCart', { title: course.value.title })
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('courseDetail.addFailed'))
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

function packageItemTypeLabel(type: string): string {
  const key = { LESSON: 'lesson', MODULE: 'module', PACKAGE: 'package' }[type]
  return key ? t(`courseDetail.${key}`) : type
}
</script>
