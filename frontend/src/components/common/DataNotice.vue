<template>
  <div
    v-if="noticeKind"
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

type NoticeKind = 'error' | 'success' | 'fallback'

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

const noticeKind = computed<NoticeKind | null>(() => {
  if (props.error) return 'error'
  if (props.success) return 'success'
  if (props.usingFallback) return 'fallback'
  return null
})

const title = computed<string>(() => {
  switch (noticeKind.value) {
    case 'error':
      return props.errorTitle || 'Connection notice'
    case 'success':
      return props.successTitle || 'Saved successfully'
    case 'fallback':
      return props.fallbackTitle || 'Preview data mode'
    default:
      return ''
  }
})

const noticeIcon = computed<string>(() => {
  if (noticeKind.value === 'error') return 'alert'
  if (noticeKind.value === 'success') return 'status'
  return 'safe'
})

const noticeRole = computed<'alert' | 'status'>(() => (noticeKind.value === 'error' ? 'alert' : 'status'))
const noticeLive = computed<'assertive' | 'polite'>(() => (noticeKind.value === 'error' ? 'assertive' : 'polite'))

const noticeClass = computed(() => ({
  'is-error': noticeKind.value === 'error',
  'is-fallback': noticeKind.value === 'fallback',
  'is-success': noticeKind.value === 'success',
}))

const message = computed<string>(() => {
  switch (noticeKind.value) {
    case 'error':
      return props.error ?? ''
    case 'success':
      return props.success ?? ''
    case 'fallback':
      return props.fallbackMessage || 'Showing safe preview data. Connect the backend to use live platform data.'
    default:
      return ''
  }
})
</script>
