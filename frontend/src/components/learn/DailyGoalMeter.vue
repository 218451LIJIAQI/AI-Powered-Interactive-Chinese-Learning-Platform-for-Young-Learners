<template>
  <!-- The strip above the Practice stage: one line, with the streak at its end. -->
  <div v-if="strip" class="practice-goal" :class="{ 'is-reached': reached }" data-testid="daily-goal">
    <span>{{ t('practice.dailyGoal') }}</span>
    <ProgressBar
      class="practice-goal-bar"
      :progress="percent"
      :tone="reached ? 'success' : 'primary'"
      :label="t('practice.dailyGoal')"
    />
    <strong class="practice-goal-value">{{ t('learn.xpOfGoal', { xp: todayXp, goal: goalXp }) }}</strong>
    <span class="practice-goal-note">
      <template v-if="reached">
        {{ t('practice.goalReachedStreak', { days: streak }, streak) }}
        <IconGlyph name="check" :size="14" />
      </template>
      <template v-else>{{ t('practice.xpToGo', { xp: Math.max(0, goalXp - todayXp) }) }}</template>
    </span>
    <span class="practice-goal-streak">
      <IconGlyph name="streak" :size="16" />
      {{ streak > 0 ? t('practice.streakDays', { days: streak }, streak) : t('practice.noStreakYet') }}
    </span>
  </div>

  <div v-else class="learn-goal" :class="{ 'is-reached': reached }" data-testid="daily-goal">
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
import { practiceMessages } from '@/plugins/practiceMessages'
import { ratioPercent } from '@/utils/format'

/**
 * Today's XP against the daily goal and the streak: a one-line `strip` (Practice), or the compact block of the Learn
 * rail, with what it takes to reach the goal or that it is reached (in green, with a check).
 */
interface Props {
  todayXp: number
  goalXp: number
  reached: boolean
  streak: number
  /** The rail's hint while the goal is not reached yet ("2 more lessons reach today’s goal."). */
  pendingHint?: string
  strip?: boolean
}

const props = withDefaults(defineProps<Props>(), { pendingHint: '', strip: false })

const { t } = useLocalMessages({ learn: learnMessages, practice: practiceMessages })

const percent = computed(() => ratioPercent(props.todayXp, props.goalXp))
const hint = computed(() =>
  props.reached ? t('learn.goalReachedStreak', { days: props.streak }, props.streak) : props.pendingHint,
)
</script>
