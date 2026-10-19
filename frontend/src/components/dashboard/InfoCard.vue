<template>
  <article class="panel stack">
    <div class="card-section-head">
      <div class="card-section-title">
        <div class="media-badge small" aria-hidden="true">
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
