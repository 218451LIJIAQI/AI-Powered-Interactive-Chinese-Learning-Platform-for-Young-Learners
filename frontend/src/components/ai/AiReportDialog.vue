<template>
  <AppOverlay
    :title="t(target.titleKey)"
    :subtitle="t(target.leadKey ?? 'aiHelper.reportLead')"
    :busy="sending"
    @close="emit('close')"
  >
    <form :id="`${baseId}-form`" class="ai-dialog-form" data-testid="ai-report-form" @submit.prevent="send">
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
      <p class="ai-dialog-attached">
        <IconGlyph name="status" :size="14" />
        {{ t(target.attachedKey) }}
      </p>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button class="btn btn-secondary" type="button" :disabled="sending" @click="emit('close')">
        {{ t('aiHelper.cancel') }}
      </button>
      <button class="btn btn-primary" type="submit" :form="`${baseId}-form`" :disabled="sending" :aria-busy="sending">
        <IconGlyph v-if="!sending" name="flag" :size="16" />
        {{ t('aiHelper.sendReport') }}
      </button>
    </template>
  </AppOverlay>
</template>

<script setup lang="ts">
import { computed, ref, useId } from 'vue'

import { aiHelperApi } from '@/api/ai'
import AppOverlay from '@/components/common/AppOverlay.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { aiHelperMessages } from '@/plugins/aiHelperMessages'
import { aiSafetyMessages } from '@/plugins/aiSafetyMessages'
import { resolveApiError } from '@/utils/apiError'

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

.ai-dialog-attached {
  display: flex;
  align-items: flex-start;
  gap: var(--space-1-5);
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: 18px;
}

.ai-dialog-attached .icon-glyph {
  margin-top: var(--space-0-5);
}
</style>
