<template>
  <div class="panel state-panel" :class="toneClass" :role="panelRole" :aria-live="panelLive" aria-atomic="true">
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
import { computed } from 'vue'

import IconGlyph from './IconGlyph.vue'

type PanelTone = 'neutral' | 'loading' | 'error' | 'empty'

interface Props {
  icon?: string
  title: string
  message: string
  eyebrow?: string
  tone?: PanelTone
}

const props = withDefaults(defineProps<Props>(), {
  icon: 'help',
  eyebrow: '',
  tone: 'empty',
})

const isError = computed(() => props.tone === 'error')
const panelLive = computed<'assertive' | 'polite'>(() => (isError.value ? 'assertive' : 'polite'))
const panelRole = computed<'alert' | 'status'>(() => (isError.value ? 'alert' : 'status'))
const toneClass = computed(() => `is-${props.tone}`)
</script>
