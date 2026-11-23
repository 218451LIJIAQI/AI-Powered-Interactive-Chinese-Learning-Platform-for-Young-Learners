<template>
  <header class="page-header class-page-header">
    <div class="page-header-before"><AppBreadcrumb :items="crumbs" /></div>
    <div class="page-title">
      <ClassAvatar :title="classTitle" :size="48" />
      <div class="page-title-copy">
        <h1><CjkText :text="title" /></h1>
        <p v-if="meta">{{ meta }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="page-header-actions"><slot name="actions" /></div>
  </header>
</template>

<script setup lang="ts">
import AppBreadcrumb, { type BreadcrumbItem } from '@/components/common/AppBreadcrumb.vue'
import CjkText from '@/components/learning/CjkText.vue'

import ClassAvatar from './ClassAvatar.vue'

/**
 * The header of a class and of its lessons: breadcrumb, the class's initials beside the H1 and its meta, the page's
 * actions on the H1 row. A lesson's H1 is the lesson; its avatar is still the class's (`classTitle`).
 */
defineProps<{
  crumbs: readonly BreadcrumbItem[]
  classTitle: string
  title: string
  meta?: string
}>()
</script>
