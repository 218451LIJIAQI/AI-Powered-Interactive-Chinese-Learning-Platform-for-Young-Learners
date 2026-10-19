<template>
  <div
    v-if="noticeKind"
    class="data-notice"
    :class="noticeClass"
    :role="noticeRole"
    :aria-live="noticeLive"
    aria-atomic="true"
  >
    <div class="data-notice-icon" aria-hidden="true">
      <IconGlyph :name="noticeIcon" :size="20" />
    </div>
    <div class="data-notice-copy">
      <strong>{{ title }}</strong>
      <span>{{ message }}</span>
    </div>
    <div v-if="detail" class="data-notice-detail">{{ detail }}</div>
    <div v-if="$slots.actions" class="data-notice-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { dataNoticeMessages } from '@/plugins/dataNoticeMessages'

import IconGlyph from './IconGlyph.vue'

interface Props {
  usingFallback?: boolean
  error?: string | null
  success?: string | null
  /** Neutral guidance (blue). */
  info?: string | null
  /** Something the user should double-check (amber). */
  warning?: string | null
  fallbackTitle?: string
  fallbackMessage?: string
  errorTitle?: string
  successTitle?: string
  infoTitle?: string
  warningTitle?: string
  detail?: string | null
}

type NoticeKind = 'error' | 'warning' | 'success' | 'info' | 'fallback'

const { t } = useI18n({
  messages: {
    en: { dataNotice: dataNoticeMessages.en },
    ms: { dataNotice: dataNoticeMessages.ms },
    'zh-CN': { dataNotice: dataNoticeMessages['zh-CN'] },
  },
})

const props = withDefaults(defineProps<Props>(), {
  usingFallback: false,
  error: null,
  success: null,
  info: null,
  warning: null,
  fallbackTitle: '',
  fallbackMessage: '',
  errorTitle: '',
  successTitle: '',
  infoTitle: '',
  warningTitle: '',
  detail: null,
})

const noticeKind = computed<NoticeKind | null>(() => {
  if (props.error) return 'error'
  if (props.warning) return 'warning'
  if (props.success) return 'success'
  if (props.info) return 'info'
  if (props.usingFallback) return 'fallback'
  return null
})

const title = computed<string>(() => {
  switch (noticeKind.value) {
    case 'error':
      return props.errorTitle || t('dataNotice.errorTitle')
    case 'warning':
      return props.warningTitle || t('dataNotice.warningTitle')
    case 'success':
      return props.successTitle || t('dataNotice.successTitle')
    case 'info':
      return props.infoTitle || t('dataNotice.infoTitle')
    case 'fallback':
      return props.fallbackTitle || t('dataNotice.fallbackTitle')
    default:
      return ''
  }
})

const noticeIcon = computed<string>(() => {
  switch (noticeKind.value) {
    case 'error':
    case 'warning':
      return 'alert'
    case 'success':
      return 'status'
    case 'info':
      return 'info'
    default:
      return 'safe'
  }
})

const noticeRole = computed<'alert' | 'status'>(() => (noticeKind.value === 'error' ? 'alert' : 'status'))
const noticeLive = computed<'assertive' | 'polite'>(() => (noticeKind.value === 'error' ? 'assertive' : 'polite'))

const noticeClass = computed(() => (noticeKind.value ? `is-${noticeKind.value}` : ''))

const message = computed<string>(() => {
  switch (noticeKind.value) {
    case 'error':
      return props.error ?? ''
    case 'warning':
      return props.warning ?? ''
    case 'success':
      return props.success ?? ''
    case 'info':
      return props.info ?? ''
    case 'fallback':
      return props.fallbackMessage || t('dataNotice.fallbackMessage')
    default:
      return ''
  }
})
</script>
