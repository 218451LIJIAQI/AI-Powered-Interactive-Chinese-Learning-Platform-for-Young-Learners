<template>
  <article class="public-feature-card" :class="`is-${tone}`">
    <div class="public-feature-card-icon">
      <IconGlyph :name="icon" :size="26" />
    </div>
    <div class="public-feature-card-copy">
      <strong>{{ title }}</strong>
      <p>{{ description }}</p>
    </div>
  </article>
</template>

<script setup lang="ts">
/**
 * Feature tile used in the public landing "what you get" grid.
 * Six accent tones map to the design-token palette so a row of
 * features looks varied without per-card colour overrides in the
 * parent page.
 */
import IconGlyph from '@/components/common/IconGlyph.vue'

type FeatureTone = 'purple' | 'green' | 'orange' | 'pink' | 'blue' | 'gold'

interface Props {
  icon: string
  title: string
  description: string
  tone?: FeatureTone
}

withDefaults(defineProps<Props>(), {
  tone: 'purple',
})
</script>
