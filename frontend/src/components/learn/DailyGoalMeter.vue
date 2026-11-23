<template>
  <!-- The strip above the Practice stage: one line, "Goal reached" at its end once it is. -->
  <div v-if="strip" class="practice-goal" :class="{ 'is-reached': reached }" data-testid="daily-goal">
    <span>{{ t('common.dailyGoal') }}</span>
    <ProgressBar
      class="practice-goal-bar"
      :progress="percent"
      :tone="reached ? 'success' : 'primary'"
      :label="t('common.dailyGoal')"
    />
    <strong>{{ t('learn.xpOfGoal', { xp: todayXp, goal: goalXp }) }}</strong>
    <span v-if="reached" class="practice-goal-note">
      <IconGlyph name="check" :size="14" />
      {{ t('learn.goalReached') }}
    </span>
  </div>

  <div v-else class="learn-goal" :class="{ 'is-reached': reached }" data-testid="daily-goal">
    <div class="learn-goal-head">
      <span class="learn-goal-label">{{ t('common.dailyGoal') }}</span>
      <strong class="learn-goal-value">{{ t('learn.xpOfGoal', { xp: todayXp, goal: goalXp }) }}</strong>
    </div>
    <ProgressBar :progress="percent" :tone="reached ? 'success' : 'primary'" :label="t('common.dailyGoal')" />
    <p class="learn-goal-hint">
      <IconGlyph v-if="reached" name="check" :size="14" />
      {{ reached ? t('learn.goalReached') : pendingHint }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnSharedMessages } from '@/plugins/learnSharedMessages'
import { ratioPercent } from '@/utils/format'

/**
 * Today's XP against the daily goal: a one-line `strip` (Practice) or the compact block of the Learn rail with what it
 * takes to reach the goal, then "Goal reached" (in green, with a check). The streak is not repeated here: the
 * sidebar's status card shows it once.
 */
interface Props {
  todayXp: number
  goalXp: number
  reached: boolean
  /** The rail's line while the goal is not reached yet ("One more lesson reaches today’s goal."). */
  pendingHint?: string
  strip?: boolean
}

const props = defineProps<Props>()

const { t } = useLocalMessages({ learn: learnSharedMessages })

const percent = computed(() => ratioPercent(props.todayXp, props.goalXp))
</script>
