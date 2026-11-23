<template>
  <RouteTabs :tabs="tabs" :label="t('adminAi.tabsLabel')" />
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'

import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import { navigationCountsKey } from '@/composables/navigationCounts'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminAiMessages } from '@/plugins/adminAiMessages'

/** The admin AI area's tabs: Reports (with the open count), Usage and Settings, each its own route. */
const { t } = useLocalMessages({ adminAi: adminAiMessages })
const navCounts = inject(navigationCountsKey, null)

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('adminAi.reports'),
    count: navCounts?.latest.value?.aiReports ?? null,
    attention: true,
    to: { name: 'admin-ai-reports' },
    icon: 'flag',
  },
  { label: t('adminAi.tabUsage'), to: { name: 'admin-ai-usage' }, icon: 'chart' },
  { label: t('adminAi.tabSettings'), to: { name: 'admin-ai-settings' }, icon: 'settings' },
])
</script>
