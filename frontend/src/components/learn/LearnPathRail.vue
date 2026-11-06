<template>
  <aside class="learn-rail" :aria-label="t('learn.railLabel')">
    <section class="learn-rail-section learn-rail-goal" data-testid="next-goal">
      <h2 class="learn-rail-overline">{{ t('learn.nextGoal') }}</h2>
      <template v-if="goal.kind === 'unit' || goal.kind === 'level'">
        <p class="learn-rail-title">
          {{ goal.kind === 'unit' ? t('learn.unlockUnit', { number: goal.next.number }) : t('learn.reachCheckpoint') }}
        </p>
        <p class="learn-rail-text">{{ t('learn.lessonsToGo', { count: goal.remaining }, goal.remaining) }}</p>
        <!-- The sentence above says the same for assistive technology. -->
        <ol class="learn-steps" aria-hidden="true">
          <li v-for="stop in goal.current.lessons" :key="stop.id" class="learn-step" :class="stepClass(stop)">
            <span class="learn-step-box">
              <IconGlyph v-if="stop.state === 'DONE'" name="check" :size="14" />
              <template v-else-if="stop.current">{{ stop.position }}</template>
              <IconGlyph v-else-if="stop.kind === 'BONUS'" name="rewards" :size="12" />
            </span>
          </li>
        </ol>
      </template>
      <template v-else-if="goal.kind === 'checkpoint'">
        <p class="learn-rail-title">{{ t('learn.checkpointOverline', { level: goal.nextLevel - 1 }) }}</p>
        <p class="learn-rail-text">
          {{ t('learn.checkpointGoal', { percent: goal.checkpoint.passPercent, level: goal.nextLevel }) }}
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

    <section class="learn-rail-section learn-rail-map" data-testid="your-path">
      <div class="learn-rail-head">
        <h2 class="learn-rail-heading">{{ t('learn.yourPath') }}</h2>
        <span class="learn-rail-meta">{{
          t('learn.lessonsOfTotal', { done: path.completedLessons, total: path.totalLessons })
        }}</span>
      </div>
      <ul class="learn-map">
        <li v-for="row in mapRows" :key="row.key" class="learn-map-row" :class="`is-${row.tone}`">
          <span v-if="row.tone === 'here'" class="learn-map-dot" aria-hidden="true" />
          <IconGlyph v-else :name="row.icon" :size="12" />
          <span class="learn-map-title">{{ row.title }}</span>
          <span class="learn-map-count">{{ row.count }}</span>
        </li>
      </ul>
    </section>

    <section v-if="today" class="learn-rail-section">
      <DailyGoalMeter
        :today-xp="today.todayXp"
        :goal-xp="today.dailyGoalXp"
        :reached="today.goalReached"
        :streak="today.currentStreak"
        :pending-hint="goalHint"
      />
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

import IconGlyph from '@/components/common/IconGlyph.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { learnMessages } from '@/plugins/learnMessages'
import type { LearnPath, PathCheckpoint, PathStop, PathUnit, TodaySummary } from '@/types/learn'

import DailyGoalMeter from './DailyGoalMeter.vue'

/**
 * The Learn page's fixed side column: what the next lessons unlock, the whole path at a glance, today's goal and the
 * mistakes to practise (only when some are waiting). Every number comes from the path.
 */
interface Props {
  path: LearnPath
  today: TodaySummary | null
}

const props = defineProps<Props>()

const { t } = useLocalMessages({ learn: learnMessages })

type Goal =
  | { kind: 'unit'; current: PathUnit; next: PathUnit; remaining: number }
  | { kind: 'level'; current: PathUnit; remaining: number }
  | { kind: 'checkpoint'; checkpoint: PathCheckpoint; nextLevel: number }
  | { kind: 'done' }

/** What the next lessons open: the next unit, the level checkpoint, or nothing (the path is finished). */
const goal = computed<Goal>(() => {
  const nextLesson = props.path.nextLesson
  if (nextLesson) {
    for (const level of props.path.levels) {
      const index = level.units.findIndex((unit) => unit.id === nextLesson.unitId)
      const current = level.units[index]
      if (!current) continue
      // A bonus chest is optional: the next unit only waits for the other stops.
      const remaining = current.lessons.filter((stop) => stop.kind !== 'BONUS' && stop.state !== 'DONE').length
      const next = level.units[index + 1]
      return next ? { kind: 'unit', current, next, remaining } : { kind: 'level', current, remaining }
    }
  }
  for (const level of props.path.levels) {
    if (level.checkpoint?.state === 'AVAILABLE') {
      return { kind: 'checkpoint', checkpoint: level.checkpoint, nextLevel: level.level + 1 }
    }
  }
  return { kind: 'done' }
})

function stepClass(stop: PathStop): string {
  if (stop.state === 'DONE') return 'is-done'
  return stop.current ? 'is-here' : 'is-ahead'
}

const mapRows = computed(() =>
  props.path.levels.flatMap((level) => {
    const units = level.units.map((unit) => {
      const here = unit.id === props.path.nextLesson?.unitId
      return {
        key: `unit-${unit.id}`,
        tone: here ? 'here' : unit.state === 'DONE' ? 'done' : 'ahead',
        icon: unit.state === 'DONE' ? 'check' : 'lock',
        title: t('learn.unitShort', { number: unit.number, title: unit.title }),
        count:
          unit.state === 'LOCKED'
            ? t('learn.lessonCount', { count: unit.lessonCount }, unit.lessonCount)
            : t('learn.xOfY', { done: unit.completedCount, total: unit.lessonCount }),
      }
    })
    const checkpoint = level.checkpoint
    const first = level.units[0]
    const last = level.units[level.units.length - 1]
    if (!checkpoint || !first || !last) return units
    return [
      ...units,
      {
        key: `checkpoint-${checkpoint.id}`,
        tone: checkpoint.state === 'DONE' ? 'done' : checkpoint.state === 'AVAILABLE' ? 'here' : 'ahead',
        icon: checkpoint.state === 'DONE' ? 'check' : 'trophy',
        title: t('learn.checkpointOverline', { level: level.level }),
        count: t('learn.unitsRange', { from: first.number, to: last.number }),
      },
    ]
  }),
)

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
