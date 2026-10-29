<template>
  <section class="plan-side-block" aria-labelledby="plan-outcomes-title" data-testid="plan-outcomes">
    <h3 id="plan-outcomes-title" class="plan-side-title">{{ t('plan.side.byTheEnd') }}</h3>
    <ul class="plan-checklist">
      <li v-for="item in items" :key="item"><IconGlyph name="check" :size="16" />{{ item }}</li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'
import type { LearningPlan } from '@/types/plan'

import { OUTCOME_KEYS } from './planLabels'

/** "By the end, you should be able to": SparkBot's list, or the plan's goals when it was built from rules. */
const props = defineProps<{ plan: LearningPlan }>()

const { t } = useLocalMessages({ plan: planMessages })

const items = computed(() =>
  props.plan.outcomes.length ? props.plan.outcomes : props.plan.goal.goals.map((goal) => t(OUTCOME_KEYS[goal])),
)
</script>
