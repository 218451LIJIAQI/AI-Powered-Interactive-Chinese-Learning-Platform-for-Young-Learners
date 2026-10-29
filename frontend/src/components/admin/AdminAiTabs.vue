<template>
  <RouteTabs :tabs="tabs" :label="t('adminAi.tabsLabel')" />
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'

import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { adminAiMessages } from '@/plugins/adminAiMessages'

import { useAiReportCounts } from './useAiReportCounts'

/** The admin AI area's tabs: Reports (with the open count), Usage and Settings, each its own route. */
const { t } = useLocalMessages({ adminAi: adminAiMessages })
const { counts, reloadCounts } = useAiReportCounts()

onMounted(() => void reloadCounts())

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('adminAi.tabReports'),
    count: counts.value?.open || null,
    attention: true,
    to: { name: 'admin-ai-reports' },
    icon: 'flag',
    testId: 'admin-ai-tab-reports',
  },
  { label: t('adminAi.tabUsage'), to: { name: 'admin-ai-usage' }, icon: 'chart', testId: 'admin-ai-tab-usage' },
  {
    label: t('adminAi.tabSettings'),
    to: { name: 'admin-ai-settings' },
    icon: 'settings',
    testId: 'admin-ai-tab-settings',
  },
])
</script>
