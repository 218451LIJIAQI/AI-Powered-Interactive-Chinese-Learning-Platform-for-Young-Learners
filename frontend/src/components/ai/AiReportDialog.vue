<template>
  <AiReportForm
    :title="title"
    :subtitle="subtitle"
    :attached="attached"
    :initial-reason="defaultReason ?? reasons[0]!"
    :submit-report="submit"
    @close="emit('close')"
    @sent="emit('sent')"
  >
    <option v-for="option in reasons" :key="option" :value="option">{{ t(reportReasonKey(option)) }}</option>
  </AiReportForm>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import { aiHelperApi } from '@/api/ai'
import { reportReasonKey } from '@/plugins/aiSafetyMessages'
import type { AiReportPayload } from '@/types/ai'
import type { AiResponseReportReason } from '@/types/api'

import AiReportForm from './AiReportForm.vue'

/**
 * "Report this answer": the learner picks a reason and may add a note; what was reported is attached for the admin.
 * `defaultReason` preselects the reason a screen expects most (it leads the list when the feature's list lacks it).
 */
const props = defineProps<{
  payload: Omit<AiReportPayload, 'reasonCategory' | 'description'>
  title: string
  attached: string
  subtitle?: string
  defaultReason?: AiResponseReportReason
}>()
const emit = defineEmits<{ close: []; sent: [] }>()

const { t } = useI18n()
/** A score has its own reason. The dialog opens anew for each target. */
const featureReasons: readonly AiResponseReportReason[] =
  props.payload.featureKey === 'PRACTICE_CHECK'
    ? ['SCORE_SEEMS_WRONG', 'INCORRECT_CONTENT', 'INAPPROPRIATE_CONTENT', 'OTHER']
    : ['INCORRECT_CONTENT', 'UNCLEAR_EXPLANATION', 'INAPPROPRIATE_CONTENT', 'OTHER']
const reasons: readonly AiResponseReportReason[] =
  props.defaultReason && !featureReasons.includes(props.defaultReason)
    ? [props.defaultReason, ...featureReasons]
    : featureReasons

function submit(reasonCategory: AiResponseReportReason, note: string) {
  return aiHelperApi.report({ ...props.payload, reasonCategory, description: note || undefined })
}
</script>
