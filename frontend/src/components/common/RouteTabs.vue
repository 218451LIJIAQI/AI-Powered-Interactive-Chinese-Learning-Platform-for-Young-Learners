<template>
  <nav class="route-tabs" :aria-label="label">
    <RouterLink
      v-for="tab in tabs"
      :key="tabKey(tab)"
      :to="tab.to"
      class="route-tab"
      :class="{ 'is-active': isActive(tab) }"
      :aria-current="isActive(tab) ? 'page' : undefined"
      :data-testid="tab.testId"
    >
      <IconGlyph v-if="tab.icon" :name="tab.icon" :size="16" />
      <span>{{ tab.label }}</span>
      <span
        v-if="tab.count !== undefined && tab.count !== null"
        class="route-tab-count"
        :class="{ 'is-attention': tab.attention }"
        >{{ tab.count }}</span
      >
    </RouterLink>
  </nav>
</template>

<script setup lang="ts">
import { RouterLink, useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

import IconGlyph from './IconGlyph.vue'

export interface RouteTab {
  readonly label: string
  readonly to: RouteLocationRaw
  readonly icon?: string
  /** A count shown after the label (items in the tab); hidden when null or undefined. */
  readonly count?: number | null
  /** The count is work waiting (a review queue), shown as a yellow badge; otherwise it is a quiet total. */
  readonly attention?: boolean
  readonly testId?: string
}

/**
 * Underline tabs where each tab is its own route (deep-linkable, works with Back): Classes' My classes / Find classes
 * / Live sessions, Content's Review queue / Teacher classes / Learning path / Practice library, and so on. The tab
 * whose route resolves to the current path is marked as the current page.
 */
interface Props {
  tabs: readonly RouteTab[]
  /** Accessible name of the tab strip, e.g. "Classes sections". */
  label: string
}

defineProps<Props>()

const route = useRoute()
const router = useRouter()

function tabKey(tab: RouteTab): string {
  return router.resolve(tab.to).fullPath
}

function isActive(tab: RouteTab): boolean {
  return router.resolve(tab.to).path === route.path
}
</script>
