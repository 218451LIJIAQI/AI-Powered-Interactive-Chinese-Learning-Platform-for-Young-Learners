<template>
  <aside class="practice-today" :aria-label="t('practice.today')" data-testid="practice-today">
    <p class="page-eyebrow">
      {{ t('practice.todayDate', { date: formatWeekdayDate(today.date) }) }}
    </p>

    <div class="practice-today-block">
      <p class="practice-today-figure">
        <strong class="stat-figure">{{ today.todayXp }}</strong>
        <span>{{ t('practice.goalOf', { goal: today.dailyGoalXp }) }}</span>
      </p>
      <ProgressBar
        class="is-sm"
        :progress="goalPercent"
        :tone="today.goalReached ? 'success' : 'primary'"
        :label="t('practice.dailyGoal')"
      />
      <p class="practice-today-caption">
        <IconGlyph v-if="today.goalReached" name="check" :size="14" />
        {{
          today.goalReached
            ? t('practice.goalReached')
            : `${t('practice.dailyGoal')} · ${t('practice.xpToGo', { xp: Math.max(0, today.dailyGoalXp - today.todayXp) })}`
        }}
      </p>
    </div>

    <div class="practice-today-block">
      <p v-if="today.currentStreak > 0" class="practice-today-figure">
        <strong class="stat-figure">{{ today.currentStreak }}</strong>
        <span>{{ t('practice.streakNoun') }}</span>
      </p>
      <p v-else class="practice-today-figure is-quiet">{{ t('practice.noStreakYet') }}</p>
      <StreakWeek :week="today.week" :today="today.date" />
    </div>

    <ul class="practice-today-lines">
      <li>
        <IconGlyph name="correct" :size="16" />
        {{
          today.setsDoneToday > 0
            ? t('practice.setsDoneToday', { count: today.setsDoneToday }, today.setsDoneToday)
            : t('practice.nothingDoneToday')
        }}
      </li>
      <li v-if="today.mistakesWaiting === 0">
        <IconGlyph name="check" :size="16" />
        {{ t('practice.noMistakesYet') }}
      </li>
    </ul>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StreakWeek from '@/components/learn/StreakWeek.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { practiceMessages } from '@/plugins/practiceMessages'
import type { TodaySummary } from '@/types/learn'
import { formatWeekdayDate, ratioPercent } from '@/utils/format'

/**
 * Practice's "Today" rail (a slim column beside the activities): today's XP against the daily goal, the streak and its
 * last seven days, and the sets done today.
 */
const props = defineProps<{ today: TodaySummary }>()

const { t } = useLocalMessages({ practice: practiceMessages })

const goalPercent = computed(() => ratioPercent(props.today.todayXp, props.today.dailyGoalXp))
</script>
