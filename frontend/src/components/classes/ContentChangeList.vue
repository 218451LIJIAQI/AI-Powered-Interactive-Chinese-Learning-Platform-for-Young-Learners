<template>
  <ul class="content-change-list">
    <li v-for="(change, index) in changes" :key="index">{{ changeText(change) }}</li>
  </ul>
</template>

<script setup lang="ts">
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classHistoryMessages } from '@/plugins/classHistoryMessages'
import type { ContentChange } from '@/types/classes'

/** What a revision changed, worded from each change's kind, name and counts. */
defineProps<{ changes: readonly ContentChange[] }>()

const { t } = useLocalMessages({ classHistory: classHistoryMessages })

const CHANGE_KEYS: Record<string, string> = {
  DETAILS: 'classHistory.change.DETAILS',
  KEY_PHRASES_ADDED: 'classHistory.change.KEY_PHRASES_ADDED',
  KEY_PHRASES_REMOVED: 'classHistory.change.KEY_PHRASES_REMOVED',
  KEY_PHRASES_CHANGED: 'classHistory.change.KEY_PHRASES_CHANGED',
  VIDEO_ADDED: 'classHistory.change.VIDEO_ADDED',
  VIDEO_REMOVED: 'classHistory.change.VIDEO_REMOVED',
  VIDEO_REPLACED: 'classHistory.change.VIDEO_REPLACED',
  VIDEO_CHANGED: 'classHistory.change.VIDEO_CHANGED',
  ACTIVITY_ADDED: 'classHistory.change.ACTIVITY_ADDED',
  ACTIVITY_CHANGED: 'classHistory.change.ACTIVITY_CHANGED',
  ACTIVITY_REMOVED: 'classHistory.change.ACTIVITY_REMOVED',
  QUIZ_ADDED: 'classHistory.change.QUIZ_ADDED',
  QUIZ_REMOVED: 'classHistory.change.QUIZ_REMOVED',
  QUIZ_SETTINGS: 'classHistory.change.QUIZ_SETTINGS',
  QUESTIONS_ADDED: 'classHistory.change.QUESTIONS_ADDED',
  QUESTIONS_REMOVED: 'classHistory.change.QUESTIONS_REMOVED',
  QUESTIONS_CHANGED: 'classHistory.change.QUESTIONS_CHANGED',
  TITLE: 'classHistory.change.TITLE',
  DESCRIPTION: 'classHistory.change.DESCRIPTION',
  LEVEL: 'classHistory.change.LEVEL',
  TOPIC: 'classHistory.change.TOPIC',
  LEARNING_TYPE: 'classHistory.change.LEARNING_TYPE',
}

function changeText(change: ContentChange): string {
  return t(CHANGE_KEYS[change.kind] ?? 'classHistory.change.other', {
    title: change.title ?? '',
    count: change.count ?? 0,
    total: change.total ?? 0,
  })
}
</script>
