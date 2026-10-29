<template>
  <div
    v-if="notice"
    class="data-notice"
    :class="`is-${notice.kind}`"
    :role="notice.kind === 'error' ? 'alert' : 'status'"
    :aria-live="notice.kind === 'error' ? 'assertive' : 'polite'"
    aria-atomic="true"
  >
    <div class="data-notice-icon" aria-hidden="true">
      <IconGlyph :name="notice.icon" />
    </div>
    <div class="data-notice-copy">
      <strong>{{ notice.title }}</strong>
      <span>{{ notice.message }}</span>
    </div>
    <div v-if="detail" class="data-notice-detail">{{ detail }}</div>
    <div v-if="$slots.actions" class="data-notice-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { dataNoticeMessages } from '@/plugins/dataNoticeMessages'

import IconGlyph from './IconGlyph.vue'

interface Props {
  error?: string | null
  success?: string | null
  /** Neutral guidance (blue). */
  info?: string | null
  errorTitle?: string
  successTitle?: string
  infoTitle?: string
  detail?: string | null
}

const { t } = useLocalMessages({ dataNotice: dataNoticeMessages })

const props = withDefaults(defineProps<Props>(), {
  error: null,
  success: null,
  info: null,
  errorTitle: '',
  successTitle: '',
  infoTitle: '',
  detail: null,
})

/** The most important message wins: error, then success, then info. */
const notice = computed(() => {
  if (props.error) {
    return { kind: 'error', icon: 'alert', title: props.errorTitle || t('dataNotice.errorTitle'), message: props.error }
  }
  if (props.success) {
    return {
      kind: 'success',
      icon: 'status',
      title: props.successTitle || t('dataNotice.successTitle'),
      message: props.success,
    }
  }
  if (props.info) {
    return { kind: 'info', icon: 'info', title: props.infoTitle || t('dataNotice.infoTitle'), message: props.info }
  }
  return null
})
</script>
