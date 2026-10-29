<template>
  <RouteTabs :tabs="tabs" :label="t('teacherStudents.tabsLabel')" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { teacherStudentsApi } from '@/api/classes'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { teacherStudentsMessages } from '@/plugins/teacherStudentsMessages'

/** Students · Feedback history, with counts. */
const props = withDefaults(
  defineProps<{
    /** The roster page has no feedback list, so the feedback count is loaded here for it. */
    provides: 'students' | 'both'
    studentCount?: number | null
    feedbackCount?: number | null
  }>(),
  { studentCount: null, feedbackCount: null },
)

const { t } = useLocalMessages({ teacherStudents: teacherStudentsMessages })
const feedback = useResource(() => teacherStudentsApi.feedback(), props.provides !== 'both')

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('teacherStudents.tabStudents'),
    to: { name: 'teacher-students' },
    icon: 'students',
    count: props.studentCount,
  },
  {
    label: t('teacherStudents.tabHistory'),
    to: { name: 'teacher-feedback-history' },
    icon: 'chat',
    count: props.feedbackCount ?? feedback.data.value?.length ?? null,
  },
])
</script>
