<template>
  <div
    class="state-panel"
    :class="[`is-${tone}`, { 'is-compact': !spacious }]"
    :role="tone === 'error' ? 'alert' : 'status'"
    :aria-live="tone === 'error' ? 'assertive' : 'polite'"
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
import IconGlyph from './IconGlyph.vue'

type PanelTone = 'loading' | 'error' | 'empty'

interface Props {
  /** Shown for the error and empty tones; a loading panel shows a spinner. */
  icon?: string
  title: string
  message: string
  eyebrow?: string
  tone?: PanelTone
  /** Roomier padding for a state that replaces a whole page; the default is compact, for cards and side panels. */
  spacious?: boolean
  /** Shows placeholder skeleton lines under a loading message. */
  skeleton?: boolean
}

withDefaults(defineProps<Props>(), {
  icon: 'help',
  eyebrow: '',
  tone: 'empty',
  spacious: false,
  skeleton: true,
})
</script>
