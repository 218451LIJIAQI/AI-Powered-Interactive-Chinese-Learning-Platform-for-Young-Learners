<template>
  <article class="public-course-card">
    <div class="public-course-card-media" aria-hidden="true">
      <CourseSceneThumb :size="112" :variant="thumbVariant" />
    </div>

    <div class="public-course-card-body">
      <div class="public-course-card-tags">
        <span class="chip public-course-card-level">{{ levelLabel }}</span>
        <span class="tag" :class="modePillClass">{{ modeLabel }}</span>
      </div>
      <h3 :id="titleId">{{ course.title }}</h3>
      <p class="public-course-card-description">{{ descriptionText }}</p>
      <ul class="public-course-card-meta">
        <li class="public-course-card-teacher">
          <PortraitAvatar :size="24" :name="teacherName" />
          <span>{{ teacherName }}</span>
        </li>
        <li>{{ contentTypeLabel }}</li>
        <li>{{ categoryLabel }}</li>
      </ul>
    </div>

    <div class="public-course-card-actions">
      <button
        class="btn btn-secondary public-course-card-button"
        type="button"
        :aria-describedby="titleId"
        @click="viewCourse"
      >
        {{ t('catalog.viewCourse') }}
        <IconGlyph name="chevronRight" :size="16" />
      </button>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import CourseSceneThumb from '@/components/common/CourseSceneThumb.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import { catalogMessages } from '@/plugins/catalogMessages'
import type { LearningType, ManagedContentType, PublicCourseSummary } from '@/types/api'

interface Props {
  course: PublicCourseSummary
}

const props = defineProps<Props>()
const { t } = useI18n({
  messages: {
    en: { catalog: catalogMessages.en },
    ms: { catalog: catalogMessages.ms },
    'zh-CN': { catalog: catalogMessages['zh-CN'] },
  },
})

const emit = defineEmits<{
  view: []
}>()

const COURSE_TYPE_KEYS: Record<ManagedContentType, string> = {
  LESSON: 'lessonType',
  MODULE: 'moduleType',
  PACKAGE: 'packageType',
}

const LEARNING_TYPE_META: Record<LearningType, { readonly labelKey: string; readonly pillClass: string }> = {
  LIVE: { labelKey: 'liveSupport', pillClass: 'is-live' },
  RECORDED: { labelKey: 'recorded', pillClass: 'is-recorded' },
  HYBRID: { labelKey: 'hybrid', pillClass: 'is-hybrid' },
}

const THUMB_VARIANTS: Record<string, 'people' | 'pagoda' | 'panda' | 'travel'> = {
  'PACKAGE-1': 'panda',
  'LESSON-2': 'people',
  'LESSON-3': 'pagoda',
}

const descriptionText = computed<string>(() => props.course.shortDescription || t('catalog.defaultDescription'))
const teacherName = computed<string>(() => props.course.teacher.displayName)
const contentTypeLabel = computed(() => t(`catalog.${COURSE_TYPE_KEYS[props.course.type]}`))
const categoryLabel = computed(() => props.course.category.name)

const levelLabel = computed(() => {
  if (props.course.level.id <= 2) return t('catalog.beginner')
  if (props.course.level.id === 3) return t('catalog.intermediate')
  return props.course.level.name
})

const learningTypeMeta = computed(() => LEARNING_TYPE_META[props.course.learningType])
const modeLabel = computed(() => t(`catalog.${learningTypeMeta.value.labelKey}`))
const modePillClass = computed(() => learningTypeMeta.value.pillClass)

const titleId = computed(() => `course-card-${props.course.id}-title`)
const thumbVariant = computed(() => THUMB_VARIANTS[props.course.id] ?? 'travel')

function viewCourse(): void {
  emit('view')
}
</script>

<style scoped>
.public-course-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-5);
  min-width: 0;
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-card);
  box-shadow: var(--shadow-sm);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard);
}

.public-course-card:hover {
  border-color: var(--border-interactive);
  box-shadow: var(--shadow-md);
}

.public-course-card-media {
  display: grid;
  place-items: center;
  padding: var(--space-2);
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, var(--color-primary-50), var(--color-info-soft));
}

.public-course-card-body {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.public-course-card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1-5);
}

.public-course-card-level {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.tag.is-live {
  border-color: var(--color-primary-200);
  color: var(--color-primary-700);
}

.tag.is-recorded {
  border-color: var(--color-info-border);
  color: var(--color-info-text);
}

.tag.is-hybrid {
  border-color: var(--color-accent-300);
  color: var(--color-accent-900);
}

.public-course-card-body h3 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.public-course-card-description {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
  text-wrap: pretty;
}

.public-course-card-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-4);
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

.public-course-card-meta li + li::before {
  content: '·';
  margin-right: var(--space-4);
  color: var(--text-disabled);
}

.public-course-card-teacher {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-primary);
}

.public-course-card-actions {
  display: flex;
}

@media (max-width: 640px) {
  .public-course-card {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-3);
  }

  .public-course-card-media {
    justify-self: stretch;
  }

  .public-course-card-button {
    width: 100%;
  }
}
</style>
