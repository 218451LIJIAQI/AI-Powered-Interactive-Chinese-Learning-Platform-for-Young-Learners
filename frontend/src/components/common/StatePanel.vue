<template>
  <div
    class="panel state-panel"
    :class="[toneClass, { 'is-compact': compact }]"
    :role="panelRole"
    :aria-live="panelLive"
    aria-atomic="true"
    :aria-busy="tone === 'loading' ? 'true' : undefined"
  >
    <div class="state-icon" aria-hidden="true">
      <span v-if="tone === 'loading'" class="spinner" />
      <IconGlyph v-else :name="icon" :size="24" />
    </div>
    <div class="state-copy">
      <span v-if="eyebrow" class="state-eyebrow">{{ eyebrow }}</span>
      <h3>{{ title }}</h3>
      <p class="muted">{{ message }}</p>
    </div>
    <div v-if="tone === 'loading' && skeleton" class="state-skeleton" aria-hidden="true">
      <span class="skeleton skeleton-line" />
      <span class="skeleton skeleton-line" />
      <span class="skeleton skeleton-line is-short" />
    </div>
    <div v-if="$slots.actions" class="state-actions">
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
  /** Smaller padding for states inside cards and side panels. */
  compact?: boolean
  /** Shows placeholder skeleton lines under a loading message. */
  skeleton?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  icon: 'help',
  eyebrow: '',
  tone: 'empty',
  compact: false,
  skeleton: true,
})

const isError = computed(() => props.tone === 'error')
const panelLive = computed<'assertive' | 'polite'>(() => (isError.value ? 'assertive' : 'polite'))
const panelRole = computed<'alert' | 'status'>(() => (isError.value ? 'alert' : 'status'))
const toneClass = computed(() => `is-${props.tone}`)
</script>
