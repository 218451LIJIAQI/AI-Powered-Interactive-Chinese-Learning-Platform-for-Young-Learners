<template>
  <header class="page-header class-page-header">
    <div class="page-header-before"><AppBreadcrumb :items="crumbs" /></div>
    <div class="page-title">
      <PortraitAvatar :size="48" :name="teacher" />
      <div class="page-title-copy">
        <h1><CjkText :text="title" /></h1>
        <p v-if="meta">{{ meta }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="page-header-actions"><slot name="actions" /></div>
  </header>
</template>

<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'

import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import CjkText from '@/components/learning/CjkText.vue'

/** The header of a class and a lesson: breadcrumb, the teacher's avatar beside the H1 and meta, actions on the H1 row. */
defineProps<{
  crumbs: readonly { label: string; to?: RouteLocationRaw }[]
  teacher: string
  title: string
  meta?: string
}>()
</script>
