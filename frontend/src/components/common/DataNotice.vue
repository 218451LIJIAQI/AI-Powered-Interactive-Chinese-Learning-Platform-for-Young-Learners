<template>
  <div v-if="error" class="data-notice is-error" role="alert" aria-live="assertive" aria-atomic="true">
    <div class="data-notice-icon" aria-hidden="true">
      <IconGlyph name="alert" />
    </div>
    <div class="data-notice-copy">
      <strong>{{ errorTitle || t('common.errorTitle') }}</strong>
      <span>{{ error }}</span>
    </div>
    <div v-if="$slots.actions" class="data-notice-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import IconGlyph from './IconGlyph.vue'

/**
 * The banner of a page's failed load or action: what went wrong in one line, under the page's own title or a neutral
 * default, with optional `actions` (a retry). Nothing shows without an `error`.
 */
withDefaults(defineProps<{ error?: string | null; errorTitle?: string }>(), { error: null, errorTitle: '' })

const { t } = useI18n({ useScope: 'global' })
</script>
