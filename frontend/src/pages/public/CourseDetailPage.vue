<template>
  <div class="public-page course-detail-page">
    <div class="course-detail-toolbar">
      <button class="btn btn-ghost btn-sm" type="button" @click="goBackToCatalog">
        <IconGlyph name="chevronLeft" :size="16" />
        {{ t('courseDetail.back') }}
      </button>
    </div>

    <DataNotice
      :using-fallback="resource.usingFallback.value"
      :fallback-title="t('courseDetail.fallbackTitle')"
      :fallback-message="t('courseDetail.fallbackMessage')"
    />

    <!-- The course title is the page heading once loaded; until then keep one h1 for assistive tech. -->
    <h1 v-if="resource.loading.value || resource.error.value" class="sr-only">
      {{ t('courseDetail.courseEyebrow') }}
    </h1>

    <StatePanel
      v-if="resource.loading.value"
      tone="loading"
      icon="progress"
      :title="t('courseDetail.loadingTitle')"
      :message="t('courseDetail.loadingMessage')"
    />

    <StatePanel
      v-else-if="resource.error.value"
      tone="error"
      icon="alert"
      :title="t('courseDetail.loadErrorTitle')"
      :message="resource.error.value"
    >
      <template #actions>
        <button class="btn btn-primary" type="button" @click="resource.reload">
          {{ t('courseDetail.retry') }}
        </button>
      </template>
    </StatePanel>

    <template v-else>
      <section class="hero-card light course-detail-hero" aria-labelledby="course-detail-title">
        <div class="course-detail-hero-copy">
          <span class="page-eyebrow">{{ t('courseDetail.courseEyebrow') }}</span>
          <h1 id="course-detail-title">{{ course.title }}</h1>
          <div class="course-detail-tags">
            <span class="chip">{{ course.category.name }}</span>
            <StatusPill :label="course.level.name" tone="primary" />
            <StatusPill :label="learningTypeLabel" tone="info" />
          </div>
          <p class="course-detail-description">{{ course.description }}</p>
          <div class="course-detail-actions">
            <button
              class="btn btn-primary btn-lg"
              type="button"
              :disabled="actionsDisabled"
              @click="handlePrimaryAction"
            >
              {{ primaryActionLabel }}
            </button>
            <button
              class="btn btn-secondary btn-lg"
              type="button"
              :disabled="actionsDisabled"
              @click="handleSecondaryAction"
            >
              {{ secondaryActionLabel }}
            </button>
          </div>
        </div>

        <div class="course-detail-showcase">
          <div class="course-detail-thumb" aria-hidden="true">
            <CourseSceneThumb :size="200" :variant="courseSceneVariant" />
          </div>
          <div class="course-detail-teacher-card">
            <PortraitAvatar :size="40" :name="course.teacher.displayName" />
            <div class="course-detail-teacher-copy">
              <span>{{ t('courseDetail.teacher') }}</span>
              <strong>{{ course.teacher.displayName }}</strong>
            </div>
            <StatusPill :label="teacherVerificationLabel" :tone="course.teacher.verified ? 'success' : 'neutral'" />
          </div>
        </div>
      </section>

      <ul class="course-detail-facts">
        <li v-for="item in summaryCards" :key="item.label">
          <span class="course-detail-fact-icon" aria-hidden="true"><IconGlyph :name="item.icon" :size="20" /></span>
          <div>
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </div>
        </li>
      </ul>

      <div class="course-detail-layout">
        <section class="panel course-detail-inside" aria-labelledby="course-detail-inside-title">
          <div class="card-header">
            <div class="course-detail-card-heading">
              <span class="course-detail-card-icon" aria-hidden="true"><IconGlyph name="learn" :size="20" /></span>
              <h2 id="course-detail-inside-title" class="card-title">{{ t('courseDetail.inside') }}</h2>
            </div>
          </div>
          <p class="course-detail-copy">{{ course.basicContent || t('courseDetail.basicFallback') }}</p>
          <ol v-if="course.packageItems.length" class="course-detail-items">
            <li v-for="item in course.packageItems" :key="`${item.type}-${item.contentId}`" class="course-detail-item">
              <span class="course-detail-item-icon" aria-hidden="true">
                <IconGlyph :name="packageItemIcon(item.type)" :size="18" />
              </span>
              <div class="course-detail-item-copy">
                <strong>{{ item.title }}</strong>
                <span>{{ t('courseDetail.includedItem', { order: item.displayOrder }) }}</span>
              </div>
              <span class="tag">{{ packageItemTypeLabel(item.type) }}</span>
            </li>
          </ol>
        </section>

        <aside class="course-detail-side">
          <section class="panel course-detail-side-card course-detail-cta-card">
            <h2 class="card-title">{{ t('courseDetail.ctaTitle') }}</h2>
            <p>{{ displayedCallToAction }}</p>
          </section>
          <section class="panel course-detail-side-card">
            <h2 class="card-title">{{ t('courseDetail.expectTitle') }}</h2>
            <ul class="course-detail-expect">
              <li>
                <IconGlyph name="status" :size="16" /><span>{{ t('courseDetail.expectMaterials') }}</span>
              </li>
              <li>
                <IconGlyph name="status" :size="16" /><span>{{ t('courseDetail.expectFeedback') }}</span>
              </li>
              <li>
                <IconGlyph name="status" :size="16" /><span>{{ t('courseDetail.expectMeeting') }}</span>
              </li>
            </ul>
          </section>
          <section class="panel course-detail-side-card">
            <h2 class="card-title">{{ t('courseDetail.supportTitle') }}</h2>
            <p>{{ t('courseDetail.supportCopy') }}</p>
            <button
              class="btn btn-secondary btn-block"
              type="button"
              :disabled="studentOnlyViewer"
              @click="goToAiHelperSignIn"
            >
              <IconGlyph name="ai" :size="16" />
              {{ aiHelperButtonLabel }}
            </button>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import { publicApi } from '@/api/services'
import CourseSceneThumb from '@/components/common/CourseSceneThumb.vue'
import type { CourseSceneVariant } from '@/components/common/courseSceneVariant'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import { publicCourseDetails } from '@/demo/data'
import { courseDetailMessages } from '@/plugins/courseDetailMessages'
import { useResource } from '@/composables/useResource'
import { useAuthStore } from '@/stores/auth'
import type { PublicCourseDetail } from '@/types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n({
  messages: {
    en: { courseDetail: courseDetailMessages.en },
    ms: { courseDetail: courseDetailMessages.ms },
    'zh-CN': { courseDetail: courseDetailMessages['zh-CN'] },
  },
})
const courseId = computed(() => String(route.params.courseId))
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
  status: 'DRAFT' as const,
  basicContent: t('courseDetail.unavailableContent'),
  packageItems: [],
}))

const resource = useResource(
  () => publicApi.getCourseDetail(courseId.value),
  () => readDemoCourseDetail() ?? emptyCourse.value,
)

// The route component is reused for /courses/a -> /courses/b, so a new id must refetch
// (an undefined id means the user is leaving this route).
watch(
  () => route.params.courseId,
  (next) => {
    if (typeof next === 'string' && next) void resource.reload()
  },
)

const course = computed(() => resource.data.value ?? emptyCourse.value)
const displayedCallToAction = computed(() => {
  if (canUseStudentActions.value) return t('courseDetail.startFromDashboard')
  if (studentOnlyViewer.value) return t('courseDetail.studentAccountRequired')
  // Translated in every interface language (FR-I18N-05); contentId 0 marks the unavailable placeholder.
  return course.value.contentId === 0 ? t('courseDetail.unavailableAction') : t('courseDetail.defaultAction')
})
const courseSceneVariant = computed<CourseSceneVariant>(() => {
  const title = course.value.title.toLowerCase()
  if (title.includes('conversation') || title.includes('campus')) return 'people'
  if (title.includes('travel')) return 'travel'
  if (title.includes('culture')) return 'pagoda'
  if (title.includes('panda')) return 'panda'
  return 'general'
})
const teacherVerificationLabel = computed(() =>
  course.value.teacher.verified ? t('courseDetail.verified') : t('courseDetail.public'),
)
const learningTypeLabel = computed(() => {
  const key = { RECORDED: 'recorded', LIVE: 'live', HYBRID: 'hybrid' }[course.value.learningType]
  return t(`courseDetail.${key}`)
})
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
  if (studentOnlyViewer.value) return t('courseDetail.studentAccessOnly')
  if (!auth.isAuthenticated) return t('courseDetail.signInStart')
  return t('courseDetail.startLearning')
})
const secondaryActionLabel = computed(() => {
  if (studentOnlyViewer.value) return t('courseDetail.studentAiOnly')
  if (!auth.isAuthenticated) return t('courseDetail.signInReturn')
  return t('courseDetail.askAi')
})
const actionsDisabled = computed(() => studentOnlyViewer.value || course.value.contentId <= 0)
const startLessonId = computed<number | null>(() => {
  if (course.value.type === 'LESSON') return course.value.contentId > 0 ? course.value.contentId : null
  const firstLesson = course.value.packageItems.find((item) => item.type === 'LESSON')
  return firstLesson && firstLesson.contentId > 0 ? firstLesson.contentId : null
})

function goToAuth(): void {
  void router.push({ name: 'auth', query: { redirect: route.fullPath } })
}

function goToStudentLearn(): void {
  const lessonId = startLessonId.value
  if (lessonId == null) {
    void router.push({ name: 'student-learn' })
    return
  }
  void router.push({ name: 'student-learn', query: { lessonId: String(lessonId) } })
}

function goBackToCatalog(): void {
  if (window.history.length > 1) {
    router.back()
  } else {
    void router.push({ name: 'catalog' })
  }
}

function handlePrimaryAction(): void {
  if (!canUseStudentActions.value) {
    if (studentOnlyViewer.value) return
    goToAuth()
    return
  }

  goToStudentLearn()
}

function handleSecondaryAction(): void {
  if (!canUseStudentActions.value) {
    if (studentOnlyViewer.value) return
    goToAuth()
    return
  }

  goToAiHelperSignIn()
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

function packageItemIcon(type: string): PackageItemIcon {
  return type === 'MODULE' ? 'content' : 'learn'
}

function packageItemTypeLabel(type: string): string {
  const key = { LESSON: 'lesson', MODULE: 'module', PACKAGE: 'package' }[type]
  return key ? t(`courseDetail.${key}`) : type
}
</script>
