<template>
  <article class="public-course-card">
    <div class="public-course-card-illustration">
      <CourseSceneThumb class="public-course-card-thumb" :size="130" :variant="thumbVariant" />
    </div>

    <div class="public-course-card-main">
      <div class="public-course-card-head">
        <div class="public-course-card-title-group">
          <h3>{{ course.title }}</h3>
          <div class="public-course-card-tags">
            <span class="public-mini-pill public-mini-pill--green">{{ levelLabel }}</span>
            <span class="public-mini-pill" :class="modePillClass">{{ modeLabel }}</span>
          </div>
        </div>
        <div class="public-course-card-price">
          <strong>{{ priceValue }}</strong>
        </div>
      </div>

      <p class="public-course-card-description">{{ descriptionText }}</p>

      <div class="public-course-card-meta">
        <span class="public-course-card-teacher">
          <PortraitAvatar :size="30" kind="female" />
          {{ teacherName }}
        </span>
        <span>{{ contentTypeLabel }}</span>
        <span>{{ categoryLabel }}</span>
      </div>
    </div>

    <div class="public-course-card-actions">
      <button class="btn btn-secondary public-course-card-button" type="button" @click="viewCourse">View Details</button>
      <button
        class="btn btn-accent public-course-card-button public-course-card-button--accent"
        type="button"
        @click="runAction"
      >
        {{ actionLabel }}
      </button>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import CourseSceneThumb from '@/components/common/CourseSceneThumb.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import type { LearningType, ManagedContentType, PublicCourseSummary } from '@/types/api'
import { formatCurrency } from '@/utils/format'

interface Props {
  course: PublicCourseSummary
  actionLabel?: string
}

const props = withDefaults(defineProps<Props>(), {
  actionLabel: 'Add to Cart',
})

const emit = defineEmits<{
  view: []
  action: []
}>()

const COURSE_TYPE_LABELS: Record<ManagedContentType, string> = {
  LESSON: 'Single lesson',
  MODULE: 'Learning module',
  PACKAGE: 'Course package',
}

const LEARNING_TYPE_META: Record<LearningType, { readonly label: string; readonly pillClass: string }> = {
  LIVE: { label: 'Live Support', pillClass: 'public-mini-pill--violet' },
  RECORDED: { label: 'Recorded', pillClass: 'public-mini-pill--purple' },
  HYBRID: { label: 'Hybrid', pillClass: 'public-mini-pill--gold' },
}

const THUMB_VARIANTS: Record<string, 'family' | 'pagoda' | 'panda' | 'travel'> = {
  'PACKAGE-1': 'panda',
  'LESSON-2': 'family',
  'LESSON-3': 'pagoda',
}

const descriptionText = computed<string>(
  () => props.course.shortDescription || 'Beginner-friendly Mandarin learning content.',
)
const teacherName = computed<string>(() => props.course.teacher.displayName)
const priceValue = computed<string>(() =>
  formatCurrency(props.course.price, props.course.currencyCode).replace(/\.00$/, ''),
)
const contentTypeLabel = computed(() => COURSE_TYPE_LABELS[props.course.type])
const categoryLabel = computed(() => props.course.category.name)

const levelLabel = computed(() => {
  if (props.course.level.id <= 2) return 'Beginner'
  if (props.course.level.id === 3) return 'Intermediate'
  return props.course.level.name
})

const learningTypeMeta = computed(() => LEARNING_TYPE_META[props.course.learningType])
const modeLabel = computed(() => learningTypeMeta.value.label)
const modePillClass = computed(() => learningTypeMeta.value.pillClass)

const thumbVariant = computed(() => THUMB_VARIANTS[props.course.id] ?? 'travel')

function viewCourse(): void {
  emit('view')
}

function runAction(): void {
  emit('action')
}
</script>
