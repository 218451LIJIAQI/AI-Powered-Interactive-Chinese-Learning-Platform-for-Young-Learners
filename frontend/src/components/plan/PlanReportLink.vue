<template>
  <button class="btn btn-link plan-report-link" type="button" data-testid="plan-report" @click="reporting = true">
    {{ t('aiSafety.report') }}
  </button>
  <AiReportDialog
    v-if="reporting"
    :payload="payload"
    :title="t('plan.report.title')"
    :attached="t('plan.report.attached')"
    default-reason="DOES_NOT_MATCH_INPUT"
    @close="reporting = false"
    @sent="sent"
  />
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import AiReportDialog from '@/components/ai/AiReportDialog.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planPathMessages } from '@/plugins/planPathMessages'
import type { AiReportPayload } from '@/types/ai'
import type { LearningPlan } from '@/types/plan'

/**
 * "Report" beside a text SparkBot wrote for the plan (the goal summary, the first milestone, the path's reasons): the
 * goal in the learner's own words and SparkBot's texts are attached. `sent` lets the page confirm it with a toast.
 */
const props = defineProps<{ plan: LearningPlan }>()
const emit = defineEmits<{ sent: [] }>()

const { t } = useLocalMessages({ plan: planPathMessages })
const reporting = ref(false)

const payload = computed<Omit<AiReportPayload, 'reasonCategory' | 'description'>>(() => {
  const plan = props.plan
  const prompt = [plan.goal.reasonText, plan.goal.ownWords]
    .map((part) => part?.trim())
    .filter((line): line is string => Boolean(line))
    .join('\n')
  const written = [
    plan.goal.title,
    plan.goal.summary,
    ...plan.goal.workOn,
    ...plan.steps.map((step) => (step.reason ? `${step.title}: ${step.reason}` : null)),
    ...plan.outcomes,
    plan.milestone?.text,
  ].filter((line): line is string => Boolean(line))
  return {
    featureKey: 'LEARNING_PLAN',
    promptSnapshot: prompt || undefined,
    aiResponseSnapshot: written.join('\n'),
  }
})

function sent(): void {
  reporting.value = false
  emit('sent')
}
</script>
