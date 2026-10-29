<template>
  <RouteTabs :tabs="tabs" :label="t('classCatalogue.tabs.label')" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { studentClassesApi } from '@/api/classes'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classCatalogueMessages } from '@/plugins/classCatalogueMessages'
import type { MyClasses } from '@/types/classes'

/**
 * My classes · Find classes · Live sessions, with counts. My classes passes what it loads itself (loadCounts false);
 * the other two tabs load the counts here.
 */
const props = withDefaults(defineProps<{ overview?: MyClasses | null; loadCounts?: boolean }>(), {
  overview: null,
  loadCounts: true,
})

const { t } = useLocalMessages({ classCatalogue: classCatalogueMessages })
const loaded = useResource(() => studentClassesApi.myClasses(), props.loadCounts)
const data = computed(() => props.overview ?? loaded.data.value)

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('classCatalogue.tabs.mine'),
    to: { name: 'student-classes' },
    icon: 'learn',
    count: data.value?.classes.length ?? null,
  },
  {
    label: t('classCatalogue.tabs.find'),
    to: { name: 'student-classes-find' },
    icon: 'search',
    count: data.value?.moreClassCount ?? null,
  },
  {
    label: t('classCatalogue.tabs.live'),
    to: { name: 'student-live-sessions' },
    icon: 'live',
    count: data.value?.upcomingLiveSessionCount ?? null,
  },
])
</script>
