<template>
  <header class="page-header">
    <div v-if="$slots.before" class="page-header-before">
      <slot name="before" />
    </div>
    <div class="page-title">
      <div class="page-title-copy">
        <div class="teacher-title-row">
          <h1>{{ title }}</h1>
          <slot name="badge" />
        </div>
        <p v-if="description">{{ description }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
/**
 * The header of a nested teacher page: breadcrumb, H1 with its status badge on one row, a meta line, and the page's
 * actions at the right of the H1 row. The same layout as PageHeaderBlock, which has no slot beside the H1.
 */
withDefaults(defineProps<{ title: string; description?: string }>(), { description: '' })
</script>
