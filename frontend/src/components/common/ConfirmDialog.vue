<template>
  <AppOverlay
    :open="open"
    :title="title"
    role="alertdialog"
    :busy="busy"
    :described-by-id="message ? messageId : undefined"
    test-id="confirm-dialog"
    @close="emit('cancel')"
  >
    <p v-if="message" :id="messageId" class="overlay-message">{{ message }}</p>
    <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    <template #footer>
      <button
        type="button"
        class="btn btn-secondary"
        :disabled="busy"
        data-autofocus
        data-testid="confirm-dialog-cancel"
        @click="emit('cancel')"
      >
        {{ cancelLabel || t('common.cancel') }}
      </button>
      <button
        type="button"
        class="btn"
        :class="tone === 'danger' ? 'btn-danger' : 'btn-primary'"
        :disabled="busy"
        :aria-busy="busy || undefined"
        data-testid="confirm-dialog-confirm"
        @click="emit('confirm')"
      >
        {{ confirmLabel }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { useId } from 'vue'
import { useI18n } from 'vue-i18n'

import AppOverlay from '@/components/common/AppOverlay.vue'

/**
 * A confirm step that says exactly what happens. `confirm` is the action the confirm label names; `cancel` is
 * everything else (the cancel button, the × in the header, Escape, the scrim). The safe choice is always on the left (secondary) and has
 * the focus, so Enter never confirms by accident; the button on the right does what the title asks: the filled
 * primary button (Publish, Leave, Set up later …) or, with `tone="danger"`, the danger one (Archive, Delete …).
 * `error` shows why the last attempt failed.
 */
interface Props {
  title: string
  confirmLabel: string
  open?: boolean
  message?: string
  /** Defaults to "Cancel"; a "Leave …?" step names the stay choice here ("Keep going"). */
  cancelLabel?: string
  tone?: 'primary' | 'danger'
  busy?: boolean
  error?: string | null
}

withDefaults(defineProps<Props>(), {
  open: true,
  message: '',
  cancelLabel: '',
  tone: 'primary',
  busy: false,
  error: null,
})

const emit = defineEmits<{ confirm: []; cancel: [] }>()
const { t } = useI18n()
const messageId = `${useId()}-message`
</script>
