<template>
  <RouteTabs :tabs="tabs" :label="t('adminContentTabs.label')"
    ><template v-if="$slots.default" #default><slot /></template
  ></RouteTabs>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'

import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminContentTabsMessages } from '@/plugins/adminContentTabsMessages'

import { useAdminContentCounts } from './useAdminContentCounts'

/**
 * The admin Content sections as underline tabs with their counts: Review queue (the items waiting for a decision, a
 * yellow badge), Teacher classes, Learning path and Practice library. The Review queue page passes its own count (null
 * while it loads), so the badge follows its decisions.
 */
const props = defineProps<{ reviewQueue?: number | null }>()

const { t } = useLocalMessages({ adminContentTabs: adminContentTabsMessages })
const { counts, reloadCounts } = useAdminContentCounts()

onMounted(() => void reloadCounts(props.reviewQueue === undefined))

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('adminContentTabs.reviewQueue'),
    to: { name: 'admin-content-review' },
    icon: 'checklist',
    count: props.reviewQueue ?? counts.value.review,
    attention: true,
    testId: 'content-tab-review',
  },
  {
    label: t('adminContentTabs.teacherClasses'),
    to: { name: 'admin-teacher-classes' },
    icon: 'classes',
    count: counts.value.classes,
    testId: 'content-tab-classes',
  },
  {
    label: t('adminContentTabs.learningPath'),
    to: { name: 'admin-learning-path' },
    icon: 'route',
    count: counts.value.units,
    testId: 'content-tab-learning-path',
  },
  {
    label: t('adminContentTabs.practiceLibrary'),
    to: { name: 'admin-practice-library' },
    icon: 'practice',
    count: counts.value.practice,
    testId: 'content-tab-practice',
  },
])
</script>
