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
      <button class="btn btn-secondary public-course-card-button" type="button" @click="$emit('view')">
        View Details
      </button>
      <button
        class="btn btn-accent public-course-card-button public-course-card-button--accent"
        type="button"
        @click="$emit('action')"
      >
        {{ actionLabel }}
      </button>
    </div>
  </article>
</template>

<script setup lang="ts">
/**
 * Public-facing course card used on the landing page and the
 * catalogue grid. Shows the {@link CourseSceneThumb} cover, title,
 * teacher, content type, pricing, and a CTA chip. Routes to the
 * course detail page on click.
 *
 * Free vs. paid courses share the same layout — the price chip is
 * the only visual hint of the access type so the rest of the
 * card feels consistent across the catalogue.
 */
import { computed } from 'vue'

import CourseSceneThumb from '@/components/common/CourseSceneThumb.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import type { PublicCourseSummary } from '@/types/api'
import { formatCurrency } from '@/utils/format'

interface Props {
  course: PublicCourseSummary
  actionLabel?: string
}

const props = withDefaults(defineProps<Props>(), {
  actionLabel: 'Add to Cart',
})

defineEmits<{
  view: []
  action: []
}>()

const descriptionText = computed<string>(
  () => props.course.shortDescription || 'Beginner-friendly Mandarin learning content.',
)
const teacherName = computed<string>(() => props.course.teacher.displayName)
const priceValue = computed<string>(() =>
  formatCurrency(props.course.price, props.course.currencyCode).replace(/\.00$/, ''),
)
const contentTypeLabel = computed(() => (props.course.type === 'PACKAGE' ? 'Course package' : 'Single lesson'))
const categoryLabel = computed(() => props.course.category.name)

const levelLabel = computed(() => {
  if (props.course.level.id <= 2) return 'Beginner'
  if (props.course.level.id === 3) return 'Intermediate'
  return props.course.level.name
})

const modeLabel = computed(() => {
  if (props.course.learningType === 'LIVE') return 'Live Support'
  if (props.course.learningType === 'RECORDED') return 'Recorded'
  return 'Hybrid'
})

const modePillClass = computed(() => {
  if (props.course.learningType === 'LIVE') return 'public-mini-pill--violet'
  if (props.course.learningType === 'RECORDED') return 'public-mini-pill--purple'
  return 'public-mini-pill--gold'
})

const thumbVariant = computed(() => {
  switch (props.course.id) {
    case 'PACKAGE-1':
      return 'panda'
    case 'LESSON-2':
      return 'family'
    case 'LESSON-3':
      return 'pagoda'
    default:
      return 'travel'
  }
})
</script>
