<template>
  <RouteTabs :tabs="tabs" :label="t('nav.contentSections')"
    ><template v-if="$slots.default" #default><slot /></template
  ></RouteTabs>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'

import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'

import { contentCounts, ensureCurrentCountSession, reloadContentCounts } from './adminContentCounts'

/**
 * The admin Content sections as underline tabs with their counts: Classes (classes and lessons), Learning path (units)
 * and Practice library (activities).
 */
const props = defineProps<{
  /** Skip fetching the classes count when the host page already loads unfiltered classes and syncs it. */
  skipClasses?: boolean
  /** Skip fetching the full learning path when the host page already loads it and syncs the unit count. */
  skipUnits?: boolean
  /** Skip fetching the practice count when the host page already loads unfiltered practice sets and syncs it. */
  skipPractice?: boolean
}>()

const { t } = useI18n()

ensureCurrentCountSession()
onMounted(
  () =>
    void reloadContentCounts({
      skipClasses: props.skipClasses,
      skipUnits: props.skipUnits,
      skipPractice: props.skipPractice,
    }),
)

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('nav.classes'),
    to: { name: 'admin-classes' },
    icon: 'classes',
    count: contentCounts.value.classes,
  },
  {
    label: t('nav.learningPath'),
    to: { name: 'admin-learning-path' },
    icon: 'route',
    count: contentCounts.value.units,
  },
  {
    label: t('nav.practiceLibrary'),
    to: { name: 'admin-practice-library' },
    icon: 'practice',
    count: contentCounts.value.practice,
  },
])
</script>
