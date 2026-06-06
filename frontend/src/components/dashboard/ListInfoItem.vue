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
import IconGlyph from '@/components/common/IconGlyph.vue'

interface Props {
  title: string
  description?: string
  icon?: string
}

withDefaults(defineProps<Props>(), {
  description: '',
  icon: 'dashboard',
})
</script>
