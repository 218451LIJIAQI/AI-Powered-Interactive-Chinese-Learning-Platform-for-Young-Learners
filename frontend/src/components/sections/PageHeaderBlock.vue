<template>
  <!-- A section root: the header bar already names the page, so the h1 is for screen readers and the page starts with
       its tabs or content. Actions, if any, sit in a toolbar row. -->
  <template v-if="hideTitle">
    <h1 class="sr-only">{{ title }}</h1>
    <div v-if="$slots.actions" class="page-toolbar">
      <slot name="actions" />
    </div>
  </template>
  <header v-else class="page-header">
    <div v-if="$slots.before" class="page-header-before">
      <slot name="before" />
    </div>
    <div class="page-title">
      <div class="page-title-copy">
        <span v-if="eyebrow" class="page-eyebrow">{{ eyebrow }}</span>
        <h1>{{ title }}</h1>
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
 * meta line (`description`, `meta` slot) and the page's actions on the H1 row. Section roots whose name is already in the
 * header bar pass `hide-title`: the h1 stays for assistive technology but the page starts with its content.
 */
interface Props {
  title: string
  description?: string
  eyebrow?: string
  hideTitle?: boolean
}

withDefaults(defineProps<Props>(), {
  description: '',
  eyebrow: '',
  hideTitle: false,
})
</script>
