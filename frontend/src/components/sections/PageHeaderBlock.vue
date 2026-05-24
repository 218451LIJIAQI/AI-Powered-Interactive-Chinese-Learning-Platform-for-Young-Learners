<template>
  <section class="page-header">
    <div class="page-title">
      <div class="page-title-icon">
        <IconGlyph :name="icon" :size="28" />
      </div>
      <div class="page-title-copy">
        <span v-if="eyebrow" class="page-eyebrow">{{ eyebrow }}</span>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </section>
</template>

<script setup lang="ts">
/**
 * Standardised page header used at the top of every dashboard page:
 * icon badge + optional eyebrow + title + supporting description on
 * the left, action slot on the right for primary CTAs ("Create
 * lesson", "Export CSV", etc.). Centralising it ensures every page
 * lands with the same visual rhythm.
 */
import IconGlyph from '@/components/common/IconGlyph.vue'

interface Props {
  title: string
  description: string
  icon: string
  eyebrow?: string
}

defineProps<Props>()
</script>
