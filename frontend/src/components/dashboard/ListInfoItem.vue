<template>
  <article class="list-item">
    <div class="list-info-main">
      <div class="media-badge small">
        <IconGlyph :name="icon" />
      </div>
      <div class="list-info-copy">
        <strong>{{ title }}</strong>
        <span v-if="description" class="muted">{{ description }}</span>
        <div v-if="$slots.meta" class="list-info-meta">
          <slot name="meta" />
        </div>
      </div>
    </div>
    <div class="list-info-aside">
      <slot name="aside" />
    </div>
  </article>
</template>

<script setup lang="ts">
/**
 * Single row in any dashboard list (recent orders, recent students,
 * recent lessons, etc.). Icon + title + optional description on the
 * left, free-form `aside` slot on the right for status pills or
 * actions, and an inline `meta` slot for trailing tags. Keeps every
 * list visually uniform across personas without each parent
 * recomputing the same flex layout.
 */
import IconGlyph from '@/components/common/IconGlyph.vue'

withDefaults(
  defineProps<{
    title: string
    description?: string
    icon?: string
  }>(),
  {
    description: '',
    icon: 'dashboard',
  },
)
</script>
