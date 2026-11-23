<template>
  <nav class="breadcrumb-nav" :aria-label="t('common.breadcrumb')">
    <ol class="breadcrumb">
      <li v-for="(item, index) in items" :key="index">
        <RouterLink v-if="item.to && index < items.length - 1" :to="item.to">{{ item.label }}</RouterLink>
        <span v-else :aria-current="index === items.length - 1 ? 'page' : undefined">{{ item.label }}</span>
      </li>
    </ol>
  </nav>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

export interface BreadcrumbItem {
  label: string
  to?: RouteLocationRaw
}

/** "Classes / Mandarin for Beginners / Greetings": every item but the last links back up; the last is the page. */
defineProps<{ items: readonly BreadcrumbItem[] }>()

const { t } = useI18n()
</script>
