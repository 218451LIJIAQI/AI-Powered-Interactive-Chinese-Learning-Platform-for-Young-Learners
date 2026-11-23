<template>
  <RouteTabs :tabs="tabs" :label="t('classCatalogue.tabs.label')">
    <!-- Classes are extra practice: they give no XP and do not count for the daily goal or the streak. -->
    <span class="route-tabs-note">{{ t('classCatalogue.tabs.noXp') }}</span>
  </RouteTabs>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'

/**
 * My classes · Find classes, each with the number of rows its page lists: every joined class (an archived one too),
 * and the catalogue classes not joined yet. Counts appear as their respective data arrives.
 */
const props = defineProps<{ joinedCount?: number | null; catalogueCount?: number | null }>()

const { t } = useLocalMessages({ classCatalogue: classCatalogueMessages })

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('classCatalogue.tabs.mine'),
    to: { name: 'student-classes' },
    icon: 'learn',
    count: props.joinedCount ?? null,
  },
  {
    label: t('classCatalogue.tabs.find'),
    to: { name: 'student-classes-find' },
    icon: 'globe',
    count: props.catalogueCount ?? null,
  },
])
</script>
