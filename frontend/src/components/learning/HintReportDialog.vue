<template>
  <AiReportForm
    test-id-prefix="hint-report"
    :title="t('hintChrome.hintReportTitle')"
    :attached="t('hintChrome.hintReportAttached')"
    initial-reason="INCORRECT_CONTENT"
    :submit-report="submit"
    @close="emit('close')"
    @sent="emit('sent')"
  >
    <option v-for="option in REASONS" :key="option.value" :value="option.value">{{ t(option.labelKey) }}</option>
  </AiReportForm>
</template>

<script setup lang="ts">
import { hintApi } from '@/api/learn'
import AiReportForm from '@/components/ai/AiReportForm.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { hintChromeMessages } from '@/plugins/hintChromeMessages'
import type { AiResponseReportReason } from '@/types/api'

/**
 * "Report this hint": the learner picks why the SparkBot hint on screen is wrong and may add a note. The report joins
 * the admins' AI reports with the hint as it was shown and what it was for (the activity, set or lesson, item and
 * page), and the reply comes in Ask SparkBot › My reports. A failed send keeps the choices and says why.
 */
const props = defineProps<{
  /** The hint's sentence as the learner read it. */
  text: string
  /** What the hint belongs to (a set and item, a lesson question), for the team; the page's address is added. */
  context: string
}>()
const emit = defineEmits<{ close: []; sent: [] }>()

/** The reasons of an AI report (the server's `AiResponseReportReason`), worded for a hint. */
const REASONS: readonly { value: AiResponseReportReason; labelKey: string }[] = [
  { value: 'INCORRECT_CONTENT', labelKey: 'hintChrome.hintReasonWrong' },
  { value: 'UNCLEAR_EXPLANATION', labelKey: 'hintChrome.hintReasonUnclear' },
  { value: 'INAPPROPRIATE_CONTENT', labelKey: 'aiSafety.reasonInappropriate' },
  { value: 'OTHER', labelKey: 'aiSafety.reasonOther' },
]

const { t } = useLocalMessages({ hintChrome: hintChromeMessages })

function submit(reasonCategory: AiResponseReportReason, note: string) {
  return hintApi.report({
    reasonCategory,
    description: note || null,
    promptSnapshot: `${props.context} · ${window.location.pathname}`,
    aiResponseSnapshot: props.text,
  })
}
</script>
