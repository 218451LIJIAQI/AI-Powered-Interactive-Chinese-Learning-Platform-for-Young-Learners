<template>
  <div class="ai-report">
    <button
      v-if="!open"
      class="btn btn-ghost btn-sm ai-report-toggle"
      type="button"
      :disabled="!canReport"
      data-testid="ai-report-toggle"
      @click="open = true"
    >
      <IconGlyph name="flag" :size="14" />
      {{ t('aiSafety.reportToggle') }}
    </button>

    <form v-else class="ai-report-form" :aria-labelledby="titleId" @submit.prevent="submit">
      <p :id="titleId" class="ai-report-title">
        <IconGlyph name="flag" :size="16" />
        {{ t('aiSafety.reportFormLabel') }}
      </p>
      <div class="field">
        <label class="field-label" :for="reasonId">{{ t('aiSafety.reason') }}</label>
        <select :id="reasonId" v-model="reasonCategory" class="select" :disabled="submitting">
          <option v-for="option in reasonOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
      </div>
      <div class="field">
        <label class="field-label" :for="noteId">{{ t('aiSafety.noteLabel') }}</label>
        <textarea
          :id="noteId"
          v-model="description"
          class="textarea"
          rows="3"
          maxlength="1000"
          :disabled="submitting"
          :placeholder="t('aiSafety.notePlaceholder')"
        />
      </div>
      <div class="form-actions ai-report-actions">
        <button class="btn btn-ghost btn-sm" type="button" :disabled="submitting" @click="close">
          {{ t('aiSafety.cancel') }}
        </button>
        <button class="btn btn-primary btn-sm" type="submit" :disabled="submitting" :aria-busy="submitting">
          {{ submitting ? t('aiSafety.submitting') : t('aiSafety.submit') }}
        </button>
      </div>
    </form>

    <p
      v-if="statusMessage"
      class="inline-feedback ai-report-status"
      :class="{ 'is-error': statusIsError }"
      role="status"
    >
      <IconGlyph :name="statusIsError ? 'alert' : 'status'" :size="16" />
      {{ statusMessage }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { aiApi } from '@/api/services'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { AI_REPORT_REASON_KEYS, aiSafetyMessages } from '@/plugins/aiSafetyMessages'
import type { AiFeatureKey, AiResponseReportReason, StudentAiResponseReportResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'

/** Backend limit for promptSnapshot / aiResponseSnapshot. */
const SNAPSHOT_LIMIT = 4000

interface Props {
  featureKey: AiFeatureKey
  /** Text of the AI output being reported (required by the backend for every feature except CHAT_TUTOR with a session). */
  responseSnapshot: string
  /** Short summary of the learner input (word, sentence, topic, question). */
  promptSnapshot?: string | null
  chatSessionId?: number | null
  /** Preview/demo sessions never send live reports. */
  preview?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  promptSnapshot: null,
  chatSessionId: null,
  preview: false,
})

const emit = defineEmits<{ reported: [report: StudentAiResponseReportResponse | null] }>()

const { t } = useI18n({
  messages: {
    en: { aiSafety: aiSafetyMessages.en },
    ms: { aiSafety: aiSafetyMessages.ms },
    'zh-CN': { aiSafety: aiSafetyMessages['zh-CN'] },
  },
})

const baseId = useId()
const reasonId = `${baseId}-reason`
const noteId = `${baseId}-note`
const titleId = `${baseId}-title`
const open = ref(false)
const submitting = ref(false)
const reasonCategory = ref<AiResponseReportReason>('INCORRECT_CONTENT')
const description = ref('')
const statusMessage = ref<string | null>(null)
const statusIsError = ref(false)

const canReport = computed(() => Boolean(props.responseSnapshot.trim()))
const reasonOptions = computed(() =>
  (Object.keys(AI_REPORT_REASON_KEYS) as AiResponseReportReason[]).map((value) => ({
    value,
    label: t(`aiSafety.${AI_REPORT_REASON_KEYS[value]}`),
  })),
)

function truncate(value: string | null | undefined): string | undefined {
  const trimmed = value?.trim()
  return trimmed ? trimmed.slice(0, SNAPSHOT_LIMIT) : undefined
}

function close(): void {
  open.value = false
  description.value = ''
}

// A new AI result reuses this instance: drop the previous report state.
watch(
  () => props.responseSnapshot,
  () => {
    statusMessage.value = null
    statusIsError.value = false
    close()
  },
)

async function submit(): Promise<void> {
  if (submitting.value || !canReport.value) return
  statusMessage.value = null
  statusIsError.value = false

  if (props.preview) {
    statusMessage.value = t('aiSafety.previewNotice')
    close()
    emit('reported', null)
    return
  }

  submitting.value = true
  try {
    const report = await aiApi.reportResponse({
      featureKey: props.featureKey,
      reasonCategory: reasonCategory.value,
      description: description.value.trim() || undefined,
      chatSessionId: props.chatSessionId ?? undefined,
      promptSnapshot: truncate(props.promptSnapshot),
      aiResponseSnapshot: truncate(props.responseSnapshot),
    })
    statusMessage.value = t('aiSafety.thanks')
    close()
    emit('reported', report)
  } catch (reason) {
    statusIsError.value = true
    statusMessage.value = resolveApiError(reason, t('aiSafety.error'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.ai-report {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.ai-report-toggle {
  justify-self: start;
  color: var(--text-secondary);
}

.ai-report-form {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
}

.ai-report-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-extrabold);
}

.ai-report-title .icon-glyph {
  color: var(--color-danger-text);
}

.ai-report-status {
  margin: 0;
  overflow-wrap: anywhere;
}
</style>
