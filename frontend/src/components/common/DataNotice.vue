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
import { computed } from 'vue'

import IconGlyph from './IconGlyph.vue'

const props = withDefaults(
  defineProps<{
    usingFallback?: boolean
    error?: string | null
    success?: string | null
    fallbackTitle?: string
    fallbackMessage?: string
    errorTitle?: string
    successTitle?: string
    detail?: string | null
  }>(),
  {
    usingFallback: false,
    error: null,
    success: null,
    fallbackTitle: '',
    fallbackMessage: '',
    errorTitle: '',
    successTitle: '',
    detail: null,
  },
)

const title = computed(() => {
  if (props.error) return props.errorTitle || 'Connection notice'
  if (props.success) return props.successTitle || 'Saved successfully'
  if (props.usingFallback) return props.fallbackTitle || 'Preview data mode'
  return ''
})
const noticeIcon = computed(() => (props.error ? 'alert' : props.success ? 'status' : 'safe'))
const noticeRole = computed(() => (props.error ? 'alert' : 'status'))
const noticeLive = computed(() => (props.error ? 'assertive' : 'polite'))
const noticeClass = computed(() => ({
  'is-fallback': Boolean(props.usingFallback && !props.error && !props.success),
  'is-error': Boolean(props.error),
  'is-success': Boolean(props.success && !props.error),
}))
const message = computed(() => {
  if (props.error) {
    return props.error
  }

  if (props.success) {
    return props.success
  }

  if (props.usingFallback) {
    if (props.fallbackMessage) return props.fallbackMessage
    return 'Showing safe preview data. Connect the backend to use live platform data.'
  }

  return ''
})
</script>
