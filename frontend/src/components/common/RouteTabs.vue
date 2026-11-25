<template>
  <div class="route-tabs-row">
    <nav class="route-tabs" :aria-label="label">
      <component
        :is="tab.isDisabled ? 'span' : RouterLink"
        v-for="tab in resolvedTabs"
        :key="tab.key"
        :to="tab.isDisabled ? undefined : tab.to"
        :role="tab.isDisabled ? 'link' : undefined"
        class="route-tab"
        :class="{ 'is-disabled': tab.isDisabled, 'is-active': tab.isCurrent }"
        :aria-disabled="tab.isDisabled ? 'true' : undefined"
        :aria-current="tab.isCurrent ? 'page' : undefined"
        :data-testid="tab.testId"
      >
        <IconGlyph v-if="tab.icon" :name="tab.icon" :size="16" />
        <span>{{ tab.label }}</span>
        <span v-if="tab.count" class="route-tab-count" :class="{ 'is-attention': tab.attention }">{{ tab.count }}</span>
      </component>
    </nav>
    <!-- Page-level actions or notes at the right end of the tab row. -->
    <div v-if="$slots.default" class="route-tabs-meta">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

import IconGlyph, { type IconName } from './IconGlyph.vue'

export interface RouteTab {
  readonly label: string
  readonly to: RouteLocationRaw
  readonly icon?: IconName
  /** A count shown after the label (items in the tab); zero, null or undefined shows no badge. */
  readonly count?: number | null
  /** The count is work waiting (a review queue), shown as a yellow badge; otherwise it is the dark count. */
  readonly attention?: boolean
  readonly testId?: string
}

/**
 * Each tab has its own route, so links and browser history take you to the right section. The tab for the current
 * path is marked as the current page.
 */
interface Props {
  tabs: readonly RouteTab[]
  /** Accessible name of the tab strip, e.g. "Classes sections". */
  label: string
  /** Disable switching to other tabs while an in-flight write owns the current tab panel. */
  disabled?: boolean
}

const props = defineProps<Props>()

const route = useRoute()
const router = useRouter()

const resolvedTabs = computed(() =>
  props.tabs.map((tab) => {
    const resolved = router.resolve(tab.to)
    const isCurrent = resolved.path === route.path
    return { ...tab, key: resolved.fullPath, isCurrent, isDisabled: Boolean(props.disabled && !isCurrent) }
  }),
)
</script>
