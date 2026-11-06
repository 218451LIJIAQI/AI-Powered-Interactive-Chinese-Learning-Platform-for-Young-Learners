<template>
  <ol class="plan-ladder" :aria-label="t('plan.level.ladder')" data-testid="level-ladder">
    <li
      v-for="(rung, index) in LADDER"
      :key="rung.hsk"
      class="plan-ladder-rung"
      :class="{
        'is-reached': rung.level !== null && index <= placedIndex,
        'is-target': rung.level === target && target !== placed,
      }"
      :style="{ '--rung': index }"
    >
      <span class="plan-ladder-tag-slot">
        <span v-if="rung.level === placed" class="tag is-strong">{{ t('plan.result.you') }}</span>
        <span v-else-if="rung.level === target" class="tag is-outline">{{ t('plan.result.target') }}</span>
      </span>
      <span class="plan-ladder-bar" aria-hidden="true" />
      <span class="plan-ladder-hsk">{{ t(rung.hsk) }}</span>
      <span class="plan-ladder-name">{{ t(rung.name) }}</span>
    </li>
  </ol>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'
import type { MandarinLevel } from '@/types/learn'

import { LADDER } from './planLabels'

/**
 * The Mandarin ladder, rising rung by rung from Pre-HSK to HSK 4+: the reached rungs filled, "You" on the student's rung
 * and "Target" on the outlined one.
 */
const props = defineProps<{ placed: MandarinLevel; target: MandarinLevel }>()

const { t } = useLocalMessages({ plan: planMessages })

const placedIndex = computed(() => LADDER.findIndex((rung) => rung.level === props.placed))
</script>
