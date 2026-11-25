<template>
  <AppOverlay
    :title="title"
    :subtitle="subtitle ?? t('aiSafety.reportLead')"
    :busy="sending"
    :test-id="`${testIdPrefix}-dialog`"
    @close="emit('close')"
  >
    <form
      :id="`${baseId}-form`"
      class="form-grid"
      novalidate
      :data-testid="`${testIdPrefix}-form`"
      @submit.prevent="send"
    >
      <div class="field">
        <label class="field-label" :for="`${baseId}-reason`">
          {{ t('aiSafety.reportReason') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <select :id="`${baseId}-reason`" v-model="reason" class="select" required data-autofocus :disabled="sending">
          <slot />
        </select>
      </div>
      <div class="field">
        <label class="field-label" :for="`${baseId}-note`">{{ t('aiSafety.reportNote') }}</label>
        <textarea
          :id="`${baseId}-note`"
          v-model="note"
          class="textarea"
          rows="3"
          :disabled="sending"
          maxlength="1000"
          :placeholder="t('aiSafety.reportNotePlaceholder')"
        />
      </div>
      <p class="report-attached">
        <IconGlyph name="status" :size="14" />
        {{ attached }}
      </p>
      <div v-if="error" class="callout is-danger" role="alert" :data-testid="`${testIdPrefix}-error`">
        <IconGlyph name="alert" :size="18" />
        <span>{{ error }}</span>
      </div>
    </form>
    <template #footer>
      <button class="btn btn-secondary" type="button" :disabled="sending" @click="emit('close')">
        {{ t('common.cancel') }}
      </button>
      <button
        class="btn btn-primary"
        type="submit"
        :form="`${baseId}-form`"
        :disabled="sending"
        :aria-busy="sending"
        :data-testid="`${testIdPrefix}-send`"
      >
        <IconGlyph v-if="!sending" name="flag" :size="16" />
        {{ t('aiSafety.sendReport') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { ref, useId } from 'vue'
import { useI18n } from 'vue-i18n'

import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useIsUnmounted } from '@/composables/useIsUnmounted'
import type { AiResponseReportReason } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'

const props = withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    attached: string
    initialReason: AiResponseReportReason
    submitReport: (reason: AiResponseReportReason, note: string) => Promise<unknown>
    testIdPrefix?: string
  }>(),
  { subtitle: undefined, testIdPrefix: 'ai-report' },
)
const emit = defineEmits<{ close: []; sent: [] }>()
const { t } = useI18n({ useScope: 'global' })
const baseId = useId()
const reason = ref(props.initialReason)
const note = ref('')
const sending = ref(false)
const error = ref<string | null>(null)
const isUnmounted = useIsUnmounted()

async function send(): Promise<void> {
  if (sending.value) return
  sending.value = true
  error.value = null
  try {
    await props.submitReport(reason.value, note.value.trim())
    if (!isUnmounted()) emit('sent')
  } catch (reasonForFailure) {
    if (!isUnmounted()) error.value = resolveApiError(reasonForFailure, t('aiSafety.reportError'))
  } finally {
    if (!isUnmounted()) sending.value = false
  }
}
</script>
