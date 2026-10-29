<template>
  <div class="learn-goal" :class="{ 'is-reached': reached }" data-testid="daily-goal">
    <div class="learn-goal-head">
      <span class="learn-goal-label">{{ t('learn.dailyGoal') }}</span>
      <strong class="learn-goal-value">{{ t('learn.xpOfGoal', { xp: todayXp, goal: goalXp }) }}</strong>
    </div>
    <ProgressBar
      class="is-sm"
      :progress="percent"
      :tone="reached ? 'success' : 'primary'"
      :label="t('learn.dailyGoal')"
    />
    <p class="learn-goal-hint">
      <IconGlyph v-if="reached" name="check" :size="14" />
      {{ hint }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import { ratioPercent } from '@/utils/format'

/**
 * Today's XP against the daily goal, with what it takes to reach it or that it is reached (in jade, with a check).
 * The bar takes the page's pigment (`--progress-color`).
 */
interface Props {
  todayXp: number
  goalXp: number
  reached: boolean
  streak: number
  /** The hint while the goal is not reached yet ("2 more lessons reach today’s goal."). */
  pendingHint: string
}

const props = defineProps<Props>()

const { t } = useLocalMessages({ learn: learnMessages })

const percent = computed(() => ratioPercent(props.todayXp, props.goalXp))
const hint = computed(() =>
  props.reached ? t('learn.goalReachedStreak', { days: props.streak }, props.streak) : props.pendingHint,
)
</script>
