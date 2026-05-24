<template>
  <div class="brand app-brand" :class="{ 'is-compact': compact }">
    <div class="brand-copy">
      <h2 class="brand-title">
        Mandarin<span class="accent">Spark</span><IconGlyph name="star" :size="15" class="brand-star" />
      </h2>
      <p class="brand-subtitle">Learn Mandarin. Spark Your Future.</p>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Wordmark + tagline used in the public top nav and the dashboard
 * sidebar header. The `compact` variant drops the tagline so the
 * brand fits inside a collapsed sidebar.
 */
import IconGlyph from '@/components/common/IconGlyph.vue'

interface Props {
  compact?: boolean
}

withDefaults(defineProps<Props>(), {
  compact: false,
})
</script>
