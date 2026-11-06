<template>
  <ol class="admin-events">
    <li v-for="event in events" :key="event.key">
      <strong class="admin-event-title">{{ event.title }}</strong>
      <span class="admin-event-meta">{{ event.meta }}</span>
      <p v-if="event.note" class="admin-event-note">{{ event.note }}</p>
      <ContentChangeList v-if="event.changes?.length" class="admin-changes" :changes="event.changes" />
    </li>
  </ol>
</template>

<script setup lang="ts">
import ContentChangeList from '@/components/classes/ContentChangeList.vue'
import type { ContentChange } from '@/types/classes'

/** An item's history as divider rows: what happened, who and when, an optional note or list of changes. */
export interface AdminEvent {
  readonly key: string | number
  readonly title: string
  readonly meta: string
  readonly note?: string | null
  readonly changes?: readonly ContentChange[]
}

defineProps<{ events: readonly AdminEvent[] }>()
</script>
