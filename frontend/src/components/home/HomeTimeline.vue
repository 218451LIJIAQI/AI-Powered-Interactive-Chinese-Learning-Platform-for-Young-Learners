<template>
  <ol class="home-timeline" :aria-label="label">
    <li v-for="item in items" :key="item.key" class="home-timeline-item" :class="{ 'is-now': item.highlight }">
      <span class="home-timeline-when">{{ item.when }}</span>
      <div class="home-timeline-body">
        <p class="home-timeline-title">{{ item.title }}</p>
        <p class="home-timeline-meta">{{ item.meta }}</p>
        <div v-if="item.actions?.length" class="home-timeline-actions">
          <RouterLink v-for="action in item.actions" :key="action.label" class="btn btn-link" :to="action.to">
            {{ action.label }}<IconGlyph name="chevronRight" :size="14" />
          </RouterLink>
        </div>
      </div>
    </li>
  </ol>
</template>

<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'

/**
 * Home's day as a timeline of hairline rows ("Now / Next / 4:00 PM / Anytime", or "First / Then / Anytime /
 * Optional"): when on the left, what and its quiet text links on the right. The highlighted row is the one to do now;
 * its action is Home's big Continue, so it carries none of its own.
 */
export interface TimelineItem {
  key: string
  when: string
  title: string
  meta: string
  highlight?: boolean
  actions?: { label: string; to: RouteLocationRaw }[]
}

defineProps<{ items: readonly TimelineItem[]; label: string }>()
</script>
