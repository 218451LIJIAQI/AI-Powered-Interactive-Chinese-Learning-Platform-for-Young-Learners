<template>
  <div class="panel state-panel" :class="`is-${tone}`">
    <span class="state-panel-orb" />
    <div class="state-icon">
      <IconGlyph :name="icon" :size="24" />
    </div>
    <div class="state-copy">
      <span v-if="eyebrow" class="state-eyebrow">{{ eyebrow }}</span>
      <h3>{{ title }}</h3>
      <p class="muted">{{ message }}</p>
    </div>
    <div v-if="$slots.actions" class="row state-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import IconGlyph from './IconGlyph.vue'

withDefaults(
  defineProps<{
    icon?: string
    title: string
    message: string
    eyebrow?: string
    tone?: 'neutral' | 'loading' | 'error' | 'empty'
  }>(),
  {
    icon: 'help',
    eyebrow: '',
    tone: 'empty',
  },
)
</script>
