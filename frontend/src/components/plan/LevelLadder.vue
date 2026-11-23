<template>
  <ol class="plan-ladder" :aria-label="t('plan.level.ladder')">
    <li
      v-for="(rung, index) in LADDER"
      :key="rung.code"
      class="plan-ladder-rung"
      :class="{
        'is-reached': index <= placedIndex,
        'is-target': rung.level === target && target !== placed,
      }"
      :style="{ '--rung': index }"
    >
      <span class="plan-ladder-tag-slot">
        <span v-if="rung.level === placed" class="tag is-strong">{{ t('plan.result.you') }}</span>
        <span v-else-if="rung.level === target" class="tag is-outline">{{ t('plan.result.target') }}</span>
      </span>
      <span class="plan-ladder-bar" aria-hidden="true" />
      <span class="plan-ladder-hsk">{{ t(rung.code) }}</span>
    </li>
  </ol>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { planSummaryMessages } from '@/plugins/planSummaryMessages'
import type { MandarinLevel } from '@/types/learn'

import { LADDER, ladderRung } from './planLabels'

/**
 * The Mandarin ladder, rising rung by rung from Pre-HSK to HSK 4+: the reached rungs filled, "You" on the learner's
 * rung and "Target" on the outlined one.
 */
const props = defineProps<{ placed: MandarinLevel; target: MandarinLevel }>()

const { t } = useLocalMessages({ plan: planSummaryMessages })

const placedIndex = computed(() => ladderRung(props.placed))
</script>
