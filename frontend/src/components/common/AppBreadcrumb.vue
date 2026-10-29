<template>
  <nav class="breadcrumb-nav" :aria-label="t('common.breadcrumb')">
    <ol class="breadcrumb">
      <li v-for="(item, index) in items" :key="index">
        <RouterLink v-if="item.to && index < items.length - 1" :to="item.to">{{ item.label }}</RouterLink>
        <span v-else aria-current="page">{{ item.label }}</span>
      </li>
    </ol>
  </nav>
</template>

<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { commonMessages } from '@/plugins/commonMessages'

/** "Classes / Mandarin for Beginners / Greetings": every item but the last links back up; the last is the page. */
defineProps<{ items: readonly { label: string; to?: RouteLocationRaw }[] }>()

const { t } = useLocalMessages({ common: commonMessages })
</script>
