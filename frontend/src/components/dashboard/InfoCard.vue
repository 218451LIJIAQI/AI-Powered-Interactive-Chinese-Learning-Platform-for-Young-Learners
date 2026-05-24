<template>
  <article class="panel stack">
    <div class="card-section-head">
      <div class="card-section-title">
        <div class="media-badge small">
          <IconGlyph :name="icon" />
        </div>
        <div class="card-section-copy">
          <strong>{{ title }}</strong>
          <span v-if="subtitle" class="muted">{{ subtitle }}</span>
        </div>
      </div>
      <slot name="header-end" />
    </div>
    <slot />
  </article>
</template>

<script setup lang="ts">
/**
 * Read-only sibling of {@link ActionFormCard} — same header shape
 * (icon badge + title + optional subtitle) but a free-form `<slot />`
 * body instead of a form. Used for tile-style sections on every
 * persona dashboard.
 */
import IconGlyph from '@/components/common/IconGlyph.vue'

interface Props {
  title: string
  subtitle?: string
  icon?: string
}

withDefaults(defineProps<Props>(), {
  subtitle: '',
  icon: 'dashboard',
})
</script>
