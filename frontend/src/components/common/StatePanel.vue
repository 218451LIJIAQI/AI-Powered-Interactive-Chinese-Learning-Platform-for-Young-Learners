<template>
  <div
    class="panel state-panel"
    :class="`is-${tone}`"
    :role="tone === 'error' ? 'alert' : 'status'"
    :aria-live="tone === 'error' ? 'assertive' : 'polite'"
    aria-atomic="true"
  >
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
/**
 * Standardised empty / loading / error / neutral panel rendered
 * across the app whenever a data list has nothing to show or is
 * still loading. Centralising the layout + ARIA semantics (`role`
 * and `aria-live`) per tone keeps screen-reader announcements
 * consistent — error panels announce assertively, empty / loading
 * panels announce politely.
 */
import IconGlyph from './IconGlyph.vue'

type PanelTone = 'neutral' | 'loading' | 'error' | 'empty'

interface Props {
  icon?: string
  title: string
  message: string
  eyebrow?: string
  tone?: PanelTone
}

withDefaults(defineProps<Props>(), {
  icon: 'help',
  eyebrow: '',
  tone: 'empty',
})
</script>
