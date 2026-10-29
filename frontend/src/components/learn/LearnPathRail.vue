<template>
  <aside class="learn-rail" :aria-label="t('learn.railLabel')">
    <section class="learn-rail-card" data-testid="next-goal">
      <h2 class="learn-rail-overline">{{ t('learn.nextGoal') }}</h2>
      <template v-if="goal.kind === 'unit'">
        <p class="learn-rail-title">{{ t('learn.unlockUnit', { number: goal.next.number }) }}</p>
        <div class="learn-rail-units" aria-hidden="true">
          <span class="seal is-outline learn-rail-seal" lang="zh-CN">{{ hanziNumeral(goal.current.number) }}</span>
          <span class="learn-rail-thread" />
          <span class="seal is-muted learn-rail-seal" lang="zh-CN">{{ hanziNumeral(goal.next.number) }}</span>
        </div>
        <p class="learn-rail-text">
          {{
            t(
              'learn.lessonsToGo',
              { count: goal.remaining, number: goal.current.number, next: `${goal.next.title} · ${goal.next.titleZh}` },
              goal.remaining,
            )
          }}
        </p>
      </template>
      <template v-else-if="goal.kind === 'checkpoint'">
        <p class="learn-rail-title">{{ goal.checkpoint.title }}</p>
        <p class="learn-rail-text">{{ t('learn.checkpointGoal', { percent: goal.checkpoint.passPercent }) }}</p>
        <RouterLink
          class="btn btn-primary btn-sm"
          :to="{ name: 'student-learn-lesson', params: { lessonId: goal.checkpoint.id } }"
        >
          {{ t('learn.startWithXp', { xp: goal.checkpoint.xpReward }) }}
        </RouterLink>
      </template>
      <template v-else-if="goal.kind === 'level'">
        <p class="learn-rail-title">{{ t('learn.reachCheckpoint') }}</p>
        <p class="learn-rail-text">{{ t('learn.lessonsLeftInUnit', { count: goal.remaining }, goal.remaining) }}</p>
      </template>
      <p v-else class="learn-rail-title">{{ t('learn.pathFinished') }}</p>
    </section>

    <section class="learn-rail-card" data-testid="your-path">
      <div class="learn-rail-head">
        <h2 class="learn-rail-overline">{{ t('learn.yourPath') }}</h2>
        <span class="learn-rail-meta">{{
          t('learn.lessonsOfTotal', { done: path.completedLessons, total: path.totalLessons })
        }}</span>
      </div>
      <ProgressBar
        class="is-sm"
        :progress="ratioPercent(path.completedLessons, path.totalLessons)"
        :label="t('learn.yourPath')"
      />
    </section>

    <section v-if="today" class="learn-rail-card">
      <DailyGoalMeter
        :today-xp="today.todayXp"
        :goal-xp="today.dailyGoalXp"
        :reached="today.goalReached"
        :streak="today.currentStreak"
        :pending-hint="goalHint"
      />
    </section>
  </aside>

  <div class="learn-more">
    <PlanRailCard />

    <section class="learn-rail-card" data-testid="practise-card">
      <h2 class="learn-rail-overline">{{ t('learn.practiseWhatYouLearned') }}</h2>
      <template v-if="path.learnMistakesWaiting > 0">
        <p class="learn-rail-text">
          {{ t('learn.lessonMistakesWaiting', { count: path.learnMistakesWaiting }, path.learnMistakesWaiting) }}
        </p>
        <RouterLink class="btn btn-link" :to="{ name: 'student-practice', params: { type: 'mistakes' } }">
          {{ t('learn.seeMistakes', { count: path.learnMistakesWaiting }) }}<IconGlyph name="chevronRight" :size="14" />
        </RouterLink>
      </template>
      <p v-else class="learn-rail-text">{{ t('learn.noLessonMistakes') }}</p>
    </section>

    <p class="learn-rail-note">
      {{ t('learn.lookingForClass') }}
      <RouterLink :to="{ name: 'student-classes' }">{{ t('learn.itsInClasses') }}</RouterLink>
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import PlanRailCard from '@/components/plan/PlanRailCard.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import type { LearnPath, PathCheckpoint, PathUnit, TodaySummary } from '@/types/learn'
import { ratioPercent } from '@/utils/format'

import DailyGoalMeter from './DailyGoalMeter.vue'
import { hanziNumeral } from './sealGlyphs'

/**
 * The Learn page's side column. The rail (a compact strip above the path on smaller screens): what the next lessons
 * unlock, how far along the whole path is, and today's goal. Under it (after the path on smaller screens): the
 * learning plan, the lesson mistakes to practise and where a teacher's class lives.
 */
interface Props {
  path: LearnPath
  today: TodaySummary | null
}

const props = defineProps<Props>()

const { t } = useLocalMessages({ learn: learnMessages })

type Goal =
  | { kind: 'unit'; current: PathUnit; next: PathUnit; remaining: number }
  | { kind: 'level'; remaining: number }
  | { kind: 'checkpoint'; checkpoint: PathCheckpoint }
  | { kind: 'done' }

/** What the next lessons open: the next unit, the level checkpoint, or nothing (the path is finished). */
const goal = computed<Goal>(() => {
  const units = props.path.levels.flatMap((level) => level.units)
  const nextLesson = props.path.nextLesson
  if (nextLesson) {
    const index = units.findIndex((unit) => unit.id === nextLesson.unitId)
    const current = units[index]
    if (current) {
      const remaining = current.lessons.filter((stop) => stop.kind !== 'BONUS' && stop.state !== 'DONE').length
      const level = props.path.levels.find((candidate) => candidate.units.some((unit) => unit.id === current.id))
      const next = level?.units[level.units.indexOf(current) + 1]
      if (next) return { kind: 'unit', current, next, remaining }
      return { kind: 'level', remaining }
    }
  }
  const checkpoint = props.path.levels.map((level) => level.checkpoint).find((c) => c?.state === 'AVAILABLE')
  if (checkpoint) return { kind: 'checkpoint', checkpoint }
  return { kind: 'done' }
})

const goalHint = computed(() => {
  const today = props.today
  if (!today) return ''
  const remaining = Math.max(0, today.dailyGoalXp - today.todayXp)
  const lessonXp = props.path.nextLesson?.xpReward ?? 10
  const lessons = Math.max(1, Math.ceil(remaining / lessonXp))
  return today.todayXp === 0
    ? t('learn.goalHintFirst', { count: lessons }, lessons)
    : t('learn.goalHintMore', { count: lessons }, lessons)
})
</script>
