<template>
  <div
    class="state-panel"
    :class="[`is-${tone}`, { 'is-compact': !spacious }]"
    :role="tone === 'error' ? 'alert' : 'status'"
    :aria-live="tone === 'error' ? 'assertive' : 'polite'"
    aria-atomic="true"
  >
    <div class="state-icon" aria-hidden="true">
      <span v-if="tone === 'loading'" class="spinner" />
      <IconGlyph v-else :name="resolvedIcon" :size="24" />
    </div>
    <div class="state-copy">
      <component :is="headingTag" class="state-title">{{ title }}</component>
      <p v-if="message" class="muted">{{ message }}</p>
    </div>
    <div v-if="tone === 'loading' && skeleton" class="state-skeleton" aria-hidden="true">
      <span class="skeleton skeleton-line" />
      <span class="skeleton skeleton-line" />
      <span class="skeleton skeleton-line is-short" />
    </div>
    <div v-if="$slots.actions || onRetry" class="state-actions">
      <slot name="actions">
        <button class="btn btn-primary" type="button" @click="onRetry?.()">{{ t('common.retry') }}</button>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import IconGlyph, { type IconName } from './IconGlyph.vue'

type PanelTone = 'loading' | 'error' | 'empty'

interface Props {
  /** Shown for the error and empty tones; defaults to `'alert'` for error and `'help'` for empty. */
  icon?: IconName
  title: string
  /** h2 for a state directly under the page title, h3 inside a titled section. */
  headingTag?: 'h2' | 'h3'
  message?: string | null
  tone?: PanelTone
  /** Roomier padding for a state that replaces a whole page; the default is compact, for cards and side panels. */
  spacious?: boolean
  /** Shows placeholder skeleton lines under a loading message. */
  skeleton?: boolean
  /** `@retry`: the panel then offers "Try again" as its action, unless the `actions` slot gives its own. */
  onRetry?: () => void
}

const props = withDefaults(defineProps<Props>(), {
  icon: undefined,
  headingTag: 'h3',
  message: '',
  tone: 'empty',
  spacious: false,
  skeleton: true,
  onRetry: undefined,
})

const resolvedIcon = computed(() => props.icon ?? (props.tone === 'error' ? 'alert' : 'help'))

const { t } = useI18n()
</script>
