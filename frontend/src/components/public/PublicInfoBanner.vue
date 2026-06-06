<template>
  <article class="public-info-banner" :class="toneClass">
    <div class="public-info-banner-main">
      <div class="public-info-banner-icon">
        <IconGlyph :name="icon" :size="26" />
      </div>
      <div class="public-info-banner-copy">
        <strong>{{ title }}</strong>
        <p>{{ description }}</p>
      </div>
    </div>
    <div v-if="showBadge" class="public-info-banner-badge">{{ badge }}</div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'

type BannerTone = 'green' | 'purple' | 'blue' | 'pearl'

interface Props {
  icon: string
  title: string
  description: string
  badge?: string
  tone?: BannerTone
}

const props = withDefaults(defineProps<Props>(), {
  badge: '',
  tone: 'green',
})

const toneClass = computed(() => `is-${props.tone}`)
const showBadge = computed(() => props.badge.trim().length > 0)
</script>
