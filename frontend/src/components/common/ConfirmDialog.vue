<template>
  <AppOverlay
    :open="open"
    :title="title"
    size="sm"
    role="alertdialog"
    :closable="false"
    :busy="busy"
    :described-by-id="message ? messageId : undefined"
    :test-id="testId"
    @close="emit('cancel')"
  >
    <p v-if="message" :id="messageId" class="overlay-message">{{ message }}</p>
    <slot />
    <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    <template #footer>
      <button
        v-for="button in buttons"
        :key="button.action"
        type="button"
        class="btn"
        :class="button.variant"
        :disabled="busy || (button.action === 'confirm' && confirmDisabled)"
        :aria-busy="button.action === 'confirm' && busy ? true : undefined"
        :data-autofocus="button.safe ? '' : undefined"
        :data-testid="`${testId}-${button.action}`"
        @click="button.action === 'confirm' ? emit('confirm') : emit('cancel')"
      >
        {{ button.label }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'

import AppOverlay from '@/components/common/AppOverlay.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { commonMessages } from '@/plugins/commonMessages'

/**
 * A confirm step that says exactly what happens. `confirm` is the action the confirm label names; `cancel` is
 * everything else (the cancel button, Escape, the scrim). Focus starts on the safe choice, so Enter never confirms by
 * accident. Tones:
 * - `primary` / `danger`: Cancel, then the filled confirm button (Publish, Archive, Delete …);
 * - `leave`: the focus-mode "Leave …?" step: an outline Leave (confirm), then a primary Keep going (cancel).
 * Extra fields (a reason) go in the default slot; `error` shows why the last attempt failed.
 */
interface Props {
  title: string
  confirmLabel: string
  open?: boolean
  message?: string
  /** Defaults to "Cancel"; for `tone="leave"` it names the stay choice ("Keep going"). */
  cancelLabel?: string
  tone?: 'primary' | 'danger' | 'leave'
  busy?: boolean
  confirmDisabled?: boolean
  error?: string | null
  testId?: string
}

const props = withDefaults(defineProps<Props>(), {
  open: true,
  message: '',
  cancelLabel: '',
  tone: 'primary',
  busy: false,
  confirmDisabled: false,
  error: null,
  testId: 'confirm-dialog',
})

const emit = defineEmits<{ confirm: []; cancel: [] }>()
const { t } = useLocalMessages({ common: commonMessages })
const messageId = `${useId()}-message`

interface DialogButton {
  readonly action: 'confirm' | 'cancel'
  readonly label: string
  readonly variant: string
  /** The choice that changes nothing; it has the focus. */
  readonly safe: boolean
}

const buttons = computed<readonly DialogButton[]>(() => {
  const cancelLabel = props.cancelLabel || t('common.cancel')
  if (props.tone === 'leave') {
    return [
      { action: 'confirm', label: props.confirmLabel, variant: 'btn-outline', safe: false },
      { action: 'cancel', label: cancelLabel, variant: 'btn-primary', safe: true },
    ]
  }
  return [
    { action: 'cancel', label: cancelLabel, variant: 'btn-ghost', safe: true },
    {
      action: 'confirm',
      label: props.confirmLabel,
      variant: props.tone === 'danger' ? 'btn-danger' : 'btn-primary',
      safe: false,
    },
  ]
})
</script>
