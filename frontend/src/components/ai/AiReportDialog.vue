<template>
  <AppOverlay :title="t(target.titleKey)" :subtitle="t('aiHelper.reportLead')" @close="emit('close')">
    <form class="ai-dialog-form" data-testid="ai-report-form" @submit.prevent="send">
      <div class="field">
        <label class="field-label" :for="`${baseId}-reason`">
          {{ t('aiHelper.reportReason') }}<span class="required-mark" aria-hidden="true">*</span>
        </label>
        <select :id="`${baseId}-reason`" v-model="reason" class="select" required>
          <option v-for="option in reasons" :key="option" :value="option">{{ t(reportReasonKey(option)) }}</option>
        </select>
      </div>
      <div class="field">
        <label class="field-label" :for="`${baseId}-note`">{{ t('aiHelper.reportNote') }}</label>
        <textarea
          :id="`${baseId}-note`"
          v-model="note"
          class="textarea"
          rows="3"
          :maxlength="MAX_NOTE"
          :placeholder="t('aiHelper.reportNotePlaceholder')"
        />
      </div>
      <p class="callout is-compact">
        <IconGlyph name="attach" :size="16" />
        {{ t(target.attachedKey) }}
      </p>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
      <div class="form-actions">
        <button class="btn btn-ghost" type="button" @click="emit('close')">{{ t('aiHelper.cancel') }}</button>
        <button class="btn btn-primary" type="submit" :disabled="sending" :aria-busy="sending">
          <IconGlyph v-if="!sending" name="flag" :size="16" />
          {{ t('aiHelper.sendReport') }}
        </button>
      </div>
    </form>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import { aiHelperApi } from '@/api/ai'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'
import { resolveApiError } from '@/utils/apiError'

import AppOverlay from '@/components/common/AppOverlay.vue'
import { reportReasonKey, reportReasons, type AiReportTarget } from './aiHelper'

/** "Report this answer": the student picks a reason and may add a note; what was reported is attached for the admin. */
const props = defineProps<{ target: AiReportTarget }>()
const emit = defineEmits<{ close: []; sent: [] }>()

const MAX_NOTE = 1000
const { t } = useLocalMessages({ aiHelper: aiHelperMessages, aiSafety: aiSafetyMessages })
const baseId = useId()
const reasons = computed(() => reportReasons(props.target.payload.featureKey))
const reason = ref<string>(reasons.value[0])
const note = ref('')
const sending = ref(false)
const error = ref<string | null>(null)

async function send(): Promise<void> {
  if (sending.value) return
  sending.value = true
  error.value = null
  try {
    await aiHelperApi.report({
      ...props.target.payload,
      reasonCategory: reason.value,
      description: note.value.trim() || undefined,
    })
    emit('sent')
  } catch (reasonForFailure) {
    error.value = resolveApiError(reasonForFailure, t('aiHelper.reportError'))
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
/* Scoped: the dialog also opens from My learning plan, which does not load ai.css. */
.ai-dialog-form {
  display: grid;
  gap: var(--space-4);
}
</style>
