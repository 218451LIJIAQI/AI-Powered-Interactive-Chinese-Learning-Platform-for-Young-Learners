<template>
  <header class="page-header">
    <div v-if="$slots.before" class="page-header-before">
      <slot name="before" />
    </div>
    <div class="page-title">
      <span v-if="icon" class="page-title-icon" aria-hidden="true">
        <IconGlyph :name="icon" :size="22" />
      </span>
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
import IconGlyph from '@/components/common/IconGlyph.vue'

/**
 * Shared page header: optional icon and eyebrow, the page's only h1, a short description,
 * optional meta (status pills, dates) and the page's primary/secondary actions. The `before` slot holds context
 * shown above the title row, such as a breadcrumb.
 */
interface Props {
  title: string
  description?: string
  icon?: string
  eyebrow?: string
}

withDefaults(defineProps<Props>(), {
  description: '',
  icon: '',
  eyebrow: '',
})
</script>
