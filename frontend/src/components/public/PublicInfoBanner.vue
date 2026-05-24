<template>
  <article class="public-info-banner" :class="`is-${tone}`">
    <div class="public-info-banner-main">
      <div class="public-info-banner-icon">
        <IconGlyph :name="icon" :size="26" />
      </div>
      <div class="public-info-banner-copy">
        <strong>{{ title }}</strong>
        <p>{{ description }}</p>
      </div>
    </div>
    <div v-if="badge" class="public-info-banner-badge">{{ badge }}</div>
  </article>
</template>

<script setup lang="ts">
/**
 * Larger informational banner used on the landing page for
 * trust / safety / parent-controls callouts. Optional `badge`
 * slot in the corner advertises an associated status ("Verified",
 * "Coming soon", etc.).
 */
import IconGlyph from '@/components/common/IconGlyph.vue'

type BannerTone = 'green' | 'purple' | 'blue' | 'pearl'

interface Props {
  icon: string
  title: string
  description: string
  badge?: string
  tone?: BannerTone
}

withDefaults(defineProps<Props>(), {
  badge: '',
  tone: 'green',
})
</script>
