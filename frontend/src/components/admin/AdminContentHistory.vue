<template>
  <p v-if="!events.length" class="admin-meta">{{ t('classHistory.empty') }}</p>
  <AdminEventList v-else :events="rows" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { classHistoryMessages } from '@/plugins/classHistoryMessages'
import type { ContentEvent } from '@/types/classes'
import { formatDateTime } from '@/utils/format'

import AdminEventList from './AdminEventList.vue'

/** What happened to a teacher's class or lesson, newest first, with the admin's reasons and what each update changed. */
const props = defineProps<{ events: readonly ContentEvent[] }>()

const { t } = useLocalMessages({ classHistory: classHistoryMessages })

const EVENT_KEYS: Readonly<Record<string, string>> = {
  CLASS_CREATED: 'classHistory.event.CLASS_CREATED',
  CLASS_SUBMITTED: 'classHistory.event.CLASS_SUBMITTED',
  CLASS_APPROVED: 'classHistory.event.CLASS_APPROVED',
  CLASS_CHANGES_REQUESTED: 'classHistory.event.CLASS_CHANGES_REQUESTED',
  CLASS_DETAILS_SUBMITTED: 'classHistory.event.CLASS_DETAILS_SUBMITTED',
  CLASS_DETAILS_APPROVED: 'classHistory.event.CLASS_DETAILS_APPROVED',
  CLASS_DETAILS_CHANGES_REQUESTED: 'classHistory.event.CLASS_DETAILS_CHANGES_REQUESTED',
  CLASS_ARCHIVED: 'classHistory.event.CLASS_ARCHIVED',
  CLASS_RESTORED: 'classHistory.event.CLASS_RESTORED',
  LESSON_CREATED: 'classHistory.event.LESSON_CREATED',
  LESSON_SUBMITTED: 'classHistory.event.LESSON_SUBMITTED',
  LESSON_APPROVED: 'classHistory.event.LESSON_APPROVED',
  LESSON_CHANGES_REQUESTED: 'classHistory.event.LESSON_CHANGES_REQUESTED',
  REVISION_SUBMITTED: 'classHistory.event.REVISION_SUBMITTED',
  REVISION_APPROVED: 'classHistory.event.REVISION_APPROVED',
  REVISION_CHANGES_REQUESTED: 'classHistory.event.REVISION_CHANGES_REQUESTED',
  LESSON_ARCHIVED: 'classHistory.event.LESSON_ARCHIVED',
  LESSON_RESTORED: 'classHistory.event.LESSON_RESTORED',
}

const rows = computed(() =>
  props.events.map((event) => ({
    key: event.id,
    title: t(EVENT_KEYS[event.type] ?? 'classHistory.change.other', { title: event.subjectTitle ?? '' }),
    meta: t('classHistory.byOn', { name: event.actorName ?? '—', date: formatDateTime(event.createdAt) }),
    note: event.note,
    changes: event.changes,
  })),
)
</script>
