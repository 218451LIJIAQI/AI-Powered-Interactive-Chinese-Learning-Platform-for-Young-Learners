<template>
  <header class="page-header">
    <div v-if="$slots.before" class="page-header-before">
      <slot name="before" />
    </div>
    <div class="page-title">
      <div class="page-title-copy">
        <div v-if="$slots.badge" class="page-title-row">
          <h1>{{ title }}</h1>
          <slot name="badge" />
        </div>
        <h1 v-else>{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
        <div v-if="$slots.meta" class="page-header-meta">
          <slot name="meta" />
        </div>
      </div>
    </div>
    <div v-if="$slots.actions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
/**
 * Shared page header of nested pages: the `before` slot holds the breadcrumb, then the page's only h1 (28), an optional
 * meta line (`description`, `meta` slot) and the page's actions on the H1 row. The `badge` slot puts a status badge
 * beside the h1 (an admin's class or lesson).
 */
defineProps<{ title: string; description?: string }>()
</script>
