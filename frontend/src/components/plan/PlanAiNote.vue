<template>
  <div class="plan-ai-note" data-testid="plan-ai-note">
    <IconGlyph :name="plan.aiAssisted ? 'ai' : 'info'" :size="16" />
    <p>{{ plan.aiAssisted ? t('plan.page.aiNote') : t('plan.page.rulesNote') }}</p>
    <button
      v-if="plan.aiAssisted"
      class="btn btn-link"
      type="button"
      data-testid="plan-report"
      @click="reporting = true"
    >
      <IconGlyph name="flag" :size="14" />{{ t('plan.common.report') }}
    </button>
    <AiReportDialog v-if="reporting" :target="target" @close="reporting = false" @sent="reporting = false" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import AiReportDialog from '@/components/ai/AiReportDialog.vue'
import type { AiReportTarget } from '@/components/ai/aiHelper'
import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'
import type { LearningPlan } from '@/types/plan'

/**
 * Says who wrote what: SparkBot's texts are AI-assisted and can be reported (the goal in the student's words and
 * SparkBot's texts are attached); a rule-based plan says it was built from the student's answers.
 */
const props = defineProps<{ plan: LearningPlan }>()

const { t } = useLocalMessages({ plan: planMessages })
const reporting = ref(false)

const target = computed<AiReportTarget>(() => {
  const plan = props.plan
  const written = [
    plan.goal.title,
    plan.goal.summary,
    ...plan.goal.workOn,
    ...plan.steps.map((step) => (step.reason ? `${step.title}: ${step.reason}` : null)),
    ...plan.outcomes,
    plan.milestone?.text,
  ].filter((line): line is string => Boolean(line))
  return {
    payload: {
      featureKey: 'LEARNING_PLAN',
      promptSnapshot: plan.goal.ownWords ?? undefined,
      aiResponseSnapshot: written.join('\n').slice(0, 4000),
    },
    titleKey: 'aiHelper.reportPlanTitle',
    attachedKey: 'aiHelper.reportPlanAttached',
  }
})
</script>
