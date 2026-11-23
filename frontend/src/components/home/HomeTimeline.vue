<template>
  <ol class="home-timeline" :aria-label="label">
    <li v-for="item in items" :key="item.key" class="home-timeline-item">
      <span class="home-timeline-when">{{ item.when }}</span>
      <span class="home-timeline-node" aria-hidden="true" />
      <div class="home-timeline-body">
        <p class="home-timeline-title">{{ item.title }}</p>
        <p class="type-caption">{{ item.meta }}</p>
        <RouterLink class="btn btn-secondary btn-sm" :to="item.action.to">{{ item.action.label }}</RouterLink>
      </div>
    </li>
  </ol>
</template>

<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'

/**
 * A timeline in Home's rail, beside the next lesson: the rest of today ("Then / Anytime", or "Then / Anytime /
 * Optional" on a plan's first day), or a first week without a plan ("Then / Later / Anytime"). Each row has when, a
 * node on the line, what and its one action.
 */
export interface TimelineItem {
  key: string
  when: string
  title: string
  meta: string
  action: { label: string; to: RouteLocationRaw }
}

defineProps<{ items: readonly TimelineItem[]; label: string }>()
</script>
