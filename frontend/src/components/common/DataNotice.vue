<template>
  <div
    v-if="usingFallback || error || success"
    class="data-notice"
    :class="noticeClass"
    :role="noticeRole"
    :aria-live="noticeLive"
    aria-atomic="true"
  >
    <div class="data-notice-icon">
      <IconGlyph :name="noticeIcon" />
    </div>
    <div class="data-notice-copy">
      <strong>{{ title }}</strong>
      <span>{{ message }}</span>
    </div>
    <div v-if="detail" class="data-notice-detail">{{ detail }}</div>
  </div>
</template>

<script setup lang="ts">
/**
 * Inline banner used by every page that hydrates data through
 * `useResource`. Renders one of three tones:
 *   - **fallback**: preview-mode demo data is being shown
 *   - **error**: API call failed, surface the friendly message
 *   - **success**: optimistic confirmation after a save action
 *
 * Renders nothing when none of the three states apply, so pages
 * can drop it in unconditionally without a `v-if` wrapper.
 * Carries `aria-live` semantics so screen-readers announce the
 * notice with the right urgency (assertive for errors, polite
 * for everything else).
 */
import { computed } from 'vue'

import IconGlyph from './IconGlyph.vue'

interface Props {
  usingFallback?: boolean
  error?: string | null
  success?: string | null
  fallbackTitle?: string
  fallbackMessage?: string
  errorTitle?: string
  successTitle?: string
  detail?: string | null
}

const props = withDefaults(defineProps<Props>(), {
  usingFallback: false,
  error: null,
  success: null,
  fallbackTitle: '',
  fallbackMessage: '',
  errorTitle: '',
  successTitle: '',
  detail: null,
})

const title = computed<string>(() => {
  if (props.error) return props.errorTitle || 'Connection notice'
  if (props.success) return props.successTitle || 'Saved successfully'
  if (props.usingFallback) return props.fallbackTitle || 'Preview data mode'
  return ''
})

const noticeIcon = computed<string>(() => {
  if (props.error) return 'alert'
  if (props.success) return 'status'
  return 'safe'
})

const noticeRole = computed<'alert' | 'status'>(() => (props.error ? 'alert' : 'status'))
const noticeLive = computed<'assertive' | 'polite'>(() => (props.error ? 'assertive' : 'polite'))

const noticeClass = computed(() => ({
  'is-fallback': !!(props.usingFallback && !props.error && !props.success),
  'is-error': !!props.error,
  'is-success': !!(props.success && !props.error),
}))

const message = computed<string>(() => {
  if (props.error) return props.error
  if (props.success) return props.success
  if (props.usingFallback) {
    return props.fallbackMessage || 'Showing safe preview data. Connect the backend to use live platform data.'
  }
  return ''
})
</script>
