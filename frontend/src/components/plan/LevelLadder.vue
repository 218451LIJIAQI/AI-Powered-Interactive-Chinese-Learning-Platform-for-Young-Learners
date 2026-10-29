<template>
  <ol class="plan-ladder" :aria-label="t('plan.level.ladder')" data-testid="level-ladder">
    <li
      v-for="(level, index) in LEVELS"
      :key="level"
      class="plan-ladder-rung"
      :class="{
        'is-reached': index <= placedIndex,
        'is-you': level === placed,
        'is-target': level === target && target !== placed,
      }"
    >
      <span class="plan-ladder-tag-row">
        <span v-if="level === placed" class="plan-ladder-tag">{{ t('plan.result.you') }}</span>
        <span v-else-if="level === target" class="plan-ladder-tag is-target">{{ t('plan.result.target') }}</span>
      </span>
      <span class="plan-ladder-bar" aria-hidden="true" />
      <span class="plan-ladder-name">{{ t(LEVEL_HSK_KEYS[level]) }}</span>
    </li>
  </ol>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { useLocalMessages } from '@/composables/useLocalMessages'
import { planMessages } from '@/plugins/planMessages'
import type { MandarinLevel } from '@/types/learn'

import { LEVEL_HSK_KEYS, LEVELS } from './planLabels'

/**
 * The Mandarin level as three segments of one track (New to Mandarin → Beginner 1 · HSK 1 → Beginner 2 · HSK 2):
 * the reached ones filled, "You" on the student's segment and the target outlined.
 */
const props = defineProps<{ placed: MandarinLevel; target: MandarinLevel }>()

const { t } = useLocalMessages({ plan: planMessages })

const placedIndex = computed(() => LEVELS.indexOf(props.placed))
</script>
