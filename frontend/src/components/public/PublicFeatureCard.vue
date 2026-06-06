<template>
  <article class="public-feature-card" :class="toneClass">
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
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'

type FeatureTone = 'purple' | 'green' | 'orange' | 'pink' | 'blue' | 'gold'

interface Props {
  icon: string
  title: string
  description: string
  tone?: FeatureTone
}

const props = withDefaults(defineProps<Props>(), {
  tone: 'purple',
})

const toneClass = computed(() => `is-${props.tone}`)
</script>
