<template>
  <p v-if="!events.length" class="type-caption">{{ t('classHistory.empty') }}</p>
  <ol v-else class="content-history">
    <li v-for="event in events" :key="event.id" class="content-history-item">
      <span class="content-history-icon" :class="`is-${event.actorRole === 'ADMIN' ? 'admin' : 'teacher'}`">
        <IconGlyph :name="iconOf(event.type)" :size="16" />
      </span>
      <div class="content-history-copy">
        <strong>{{ t(eventKey(event.type), { title: event.subjectTitle ?? '' }) }}</strong>
        <span class="type-caption">{{
          t('classHistory.byOn', { name: event.actorName ?? '—', date: formatDateTime(event.createdAt) })
        }}</span>
        <p
          v-if="event.note"
          class="content-history-note"
          :class="{ 'is-warning': event.type.endsWith('CHANGES_REQUESTED') }"
        >
          {{ event.note }}
        </p>
        <ContentChangeList v-if="event.changes.length" :changes="event.changes" />
      </div>
    </li>
  </ol>
</template>

<script setup lang="ts">
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classHistoryMessages } from '@/plugins/classHistoryMessages'
import type { ContentEvent } from '@/types/classes'
import { formatDateTime } from '@/utils/format'

import ContentChangeList from './ContentChangeList.vue'

/** What happened to a class and its lessons, newest first, with the admin's reasons and what each revision changed. */
defineProps<{ events: readonly ContentEvent[] }>()

const { t } = useLocalMessages({ classHistory: classHistoryMessages })

const EVENT_KEYS: Record<string, string> = {
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

function eventKey(type: string): string {
  return EVENT_KEYS[type] ?? 'classHistory.change.other'
}

function iconOf(type: string): string {
  if (type.endsWith('APPROVED')) return 'check'
  if (type.endsWith('CHANGES_REQUESTED')) return 'alert'
  if (type.endsWith('SUBMITTED')) return 'send'
  if (type.endsWith('ARCHIVED')) return 'archive'
  if (type.endsWith('RESTORED')) return 'refresh'
  return 'plus'
}
</script>
