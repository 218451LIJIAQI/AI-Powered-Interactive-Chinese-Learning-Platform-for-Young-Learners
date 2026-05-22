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
import IconGlyph from '@/components/common/IconGlyph.vue'

withDefaults(
  defineProps<{
    compact?: boolean
  }>(),
  {
    compact: false,
  },
)
</script>
