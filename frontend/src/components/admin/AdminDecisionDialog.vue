<template>
  <AppOverlay
    :open="open"
    :title="title"
    :subtitle="subtitle"
    :role="role"
    :busy="busy"
    :test-id="testId"
    @close="cancel"
  >
    <slot />
    <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    <template #footer>
      <button
        class="btn btn-secondary"
        type="button"
        :disabled="busy"
        data-autofocus
        :data-testid="testId && `${testId}-cancel`"
        @click="cancel"
      >
        {{ cancelLabel || t('common.cancel') }}
      </button>
      <button
        class="btn"
        :class="tone === 'danger' ? 'btn-danger' : 'btn-primary'"
        type="button"
        :disabled="busy"
        :aria-busy="busy || undefined"
        :data-testid="confirmTestId ?? (testId && `${testId}-confirm`)"
        @click="emit('confirm')"
      >
        <IconGlyph v-if="confirmIcon" :name="confirmIcon" :size="16" />
        <span>{{ confirmLabel }}</span>
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { commonMessages } from '@/plugins/commonMessages'

/**
 * The admin's decision step (approve, request changes, deactivate, archive, unpublish): a title, what it is about, what
 * happens in the default slot, then [Cancel][Confirm]. Focus starts on the first `data-autofocus` element: a reason field
 * when the slot has one, otherwise Cancel, so Enter never confirms by accident.
 */
const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    confirmLabel: string
    subtitle?: string
    confirmIcon?: string
    cancelLabel?: string
    tone?: 'primary' | 'danger'
    busy?: boolean
    error?: string | null
    role?: 'dialog' | 'alertdialog'
    testId?: string
    confirmTestId?: string
  }>(),
  {
    subtitle: '',
    confirmIcon: '',
    cancelLabel: '',
    tone: 'primary',
    busy: false,
    error: null,
    role: 'alertdialog',
    testId: undefined,
    confirmTestId: undefined,
  },
)
const emit = defineEmits<{ confirm: []; cancel: [] }>()

const { t } = useLocalMessages({ common: commonMessages })

function cancel(): void {
  if (!props.busy) emit('cancel')
}
</script>
