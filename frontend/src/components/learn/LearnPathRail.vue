<template>
  <aside class="learn-rail" :aria-label="t('learn.railLabel')">
    <section class="learn-rail-section learn-rail-goal" data-testid="next-goal">
      <h2 class="learn-rail-overline">{{ t('learn.nextGoal') }}</h2>
      <template v-if="goal.kind === 'lessons'">
        <p class="learn-rail-title">{{ goal.title }}</p>
        <p class="learn-rail-text">{{ t('learn.lessonsToGo', { count: goal.remaining }, goal.remaining) }}</p>
      </template>
      <template v-else-if="goal.kind === 'checkpoint'">
        <p class="learn-rail-title">{{ t('learn.checkpointOverline', { hsk: goal.hskLevel }) }}</p>
        <p class="learn-rail-text">
          {{ t('learn.checkpointGoal', { percent: goal.checkpoint.passPercent, hsk: goal.hskLevel + 1 }) }}
        </p>
        <RouterLink
          class="btn btn-secondary btn-sm"
          :to="{ name: 'student-learn-lesson', params: { lessonId: goal.checkpoint.id } }"
        >
          {{ t('learn.startWithXp', { xp: goal.checkpoint.xpReward }) }}
        </RouterLink>
      </template>
      <p v-else class="learn-rail-title">{{ t('learn.pathFinished') }}</p>
    </section>

    <section v-if="today" class="learn-rail-section">
      <DailyGoalMeter
        :today-xp="today.todayXp"
        :goal-xp="today.dailyGoalXp"
        :reached="today.goalReached"
        :pending-hint="goalHint"
      />
    </section>
    <section v-else-if="todayError" class="learn-rail-section" data-testid="today-error">
      <DataNotice :error="todayError" :on-retry="onRetryToday" />
    </section>

    <section v-if="today && today.mistakesWaiting > 0" class="learn-rail-section" data-testid="practise-card">
      <h2 class="learn-rail-heading">{{ t('learn.practiseWhatYouLearned') }}</h2>
      <p class="learn-rail-note">
        {{ t('learn.mistakesWaiting', { count: today.mistakesWaiting }, today.mistakesWaiting) }}
      </p>
      <RouterLink class="btn btn-secondary btn-sm" :to="{ name: 'student-practice', params: { type: 'mistakes' } }">
        {{ t('learn.seeMistakesShort') }}
      </RouterLink>
    </section>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import DataNotice from '@/components/common/DataNotice.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import type { LearnPath, PathCheckpoint, TodaySummary } from '@/types/learn'

import DailyGoalMeter from './DailyGoalMeter.vue'

/**
 * The Learn page's fixed side column: what the next lessons unlock, today's goal and the mistakes to practise (only
 * when some are waiting). Every number comes from the path and today's summary.
 */
interface Props {
  path: LearnPath
  today: TodaySummary | null
  todayError?: string | null
  onRetryToday?: () => void
}

const props = defineProps<Props>()

const { t } = useLocalMessages({ learn: learnMessages })

type Goal =
  | { kind: 'lessons'; title: string; remaining: number }
  | { kind: 'checkpoint'; checkpoint: PathCheckpoint; hskLevel: number }
  | { kind: 'done' }

/**
 * What the next lessons open: the next unit, the level checkpoint (only a level that has one), the end of the path
 * (its last unit), or nothing (the path is finished). A bonus chest is not a lesson, so it is never counted.
 */
const goal = computed<Goal>(() => {
  const { nextLesson, levels } = props.path
  if (nextLesson) {
    for (const [levelIndex, level] of levels.entries()) {
      const index = level.units.findIndex((unit) => unit.id === nextLesson.unitId)
      const current = level.units[index]
      if (!current) continue
      const remaining = current.lessonCount - current.completedCount
      if (!(remaining > 0)) break
      const nextInLevel = level.units[index + 1]
      if (nextInLevel) {
        return { kind: 'lessons', title: t('learn.unlockUnit', { number: nextInLevel.number }), remaining }
      }
      if (level.checkpoint) return { kind: 'lessons', title: t('learn.reachCheckpoint'), remaining }
      // No checkpoint closes this level: its last unit opens the next level, or ends the path.
      const nextLevelUnit = levels.slice(levelIndex + 1).find((later) => later.units.length)?.units[0]
      const title = nextLevelUnit ? t('learn.unlockUnit', { number: nextLevelUnit.number }) : t('learn.finishPath')
      return { kind: 'lessons', title, remaining }
    }
  }
  for (const level of levels) {
    if (level.checkpoint?.state === 'AVAILABLE') {
      return { kind: 'checkpoint', checkpoint: level.checkpoint, hskLevel: level.hskLevel }
    }
  }
  return { kind: 'done' }
})

/** How many lessons of the next one's size still reach today's goal. */
const goalHint = computed(() => {
  const today = props.today
  if (!today) return ''
  const remaining = Math.max(0, today.dailyGoalXp - today.todayXp)
  const rawXp = props.path.nextLesson?.xpReward ?? 10
  const lessonXp = rawXp > 0 ? rawXp : 10
  const lessons = Math.max(1, Math.ceil(remaining / lessonXp))
  return t(today.todayXp === 0 ? 'learn.goalHintFirst' : 'learn.goalHintMore', { count: lessons }, lessons)
})
</script>
