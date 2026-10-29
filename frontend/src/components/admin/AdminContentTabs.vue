<template>
  <RouteTabs :tabs="tabs" :label="t('adminContentTabs.label')" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { shellApi } from '@/api/shell'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { adminContentTabsMessages } from '@/plugins/adminContentTabsMessages'

/**
 * The admin Content sections as underline tabs: Review queue, Teacher classes, Learning path and Practice library.
 * Only the review queue carries a count, the items waiting for a decision (a yellow badge, hidden at zero): the Review
 * queue page passes its own (null while it loads), every other Content page reads the sidebar's nav count.
 */
interface Props {
  reviewQueue?: number | null
}

const props = defineProps<Props>()

const { t } = useLocalMessages({ adminContentTabs: adminContentTabsMessages })
const navCounts = useResource(() => shellApi.navCounts(), props.reviewQueue === undefined)

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('adminContentTabs.reviewQueue'),
    to: { name: 'admin-content-review' },
    icon: 'checklist',
    count: (props.reviewQueue ?? navCounts.data.value?.reviewQueue) || null,
    attention: true,
    testId: 'content-tab-review',
  },
  {
    label: t('adminContentTabs.teacherClasses'),
    to: { name: 'admin-teacher-classes' },
    icon: 'classes',
    testId: 'content-tab-classes',
  },
  {
    label: t('adminContentTabs.learningPath'),
    to: { name: 'admin-learning-path' },
    icon: 'route',
    testId: 'content-tab-learning-path',
  },
  {
    label: t('adminContentTabs.practiceLibrary'),
    to: { name: 'admin-practice-library' },
    icon: 'practice',
    testId: 'content-tab-practice',
  },
])
</script>
