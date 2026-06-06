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
    <div v-if="hasActions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { useSlots } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'

interface Props {
  title: string
  description: string
  icon: string
  eyebrow?: string
}

defineProps<Props>()

const slots = useSlots()
const hasActions = Boolean(slots.actions)
</script>
