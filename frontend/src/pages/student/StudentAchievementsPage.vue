<template>
  <div class="page-stack student-achievements-page">
    <DataNotice :using-fallback="usingFallback" :error="pageError" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="student-achievements-layout">
        <div class="student-achievements-main">
          <article class="student-achievements-hero">
            <PandaIllustration :size="138" outfit="#7d56ff" accent="#f6d056" pose="wave" />
            <div class="student-achievements-level">
              <span>Your Level</span>
              <strong>Level {{ rewards.currentLevelNumber }}</strong>
              <small>{{ levelTitle }}</small>
              <ProgressBar :progress="rewards.progressToNextLevelPercent" />
              <em
                >{{ formatNumber(rewards.totalPoints) }} XP - {{ rewards.progressToNextLevelPercent }}% to next
                level</em
              >
            </div>
            <div class="student-achievements-stats">
              <div v-for="stat in achievementStats" :key="stat.label" class="student-achievements-stat">
                <span><IconGlyph :name="stat.icon" :size="20" /></span>
                <strong>{{ stat.value }}</strong>
                <small>{{ stat.label }}</small>
              </div>
            </div>
          </article>

          <div class="student-achievements-top-grid">
            <article class="panel student-achievements-chart-card">
              <div class="row-between">
                <div class="student-section-heading">
                  <span><IconGlyph name="progress" :size="20" /></span>
                  <strong>Learning Progress</strong>
                </div>
                <span class="student-achievements-range">Recent lessons</span>
              </div>
              <div class="student-achievements-chart">
                <span
                  v-for="point in chartPoints"
                  :key="point.left"
                  :style="{ left: point.left, bottom: point.bottom }"
                />
              </div>
              <p>
                <IconGlyph name="star" :size="16" />
                {{ progress.lastActivitySummary || 'Your progress trend updates as you complete lessons and quizzes.' }}
              </p>
            </article>

            <article class="panel student-achievements-badges-card">
              <div class="row-between">
                <div class="student-section-heading">
                  <span><IconGlyph name="trophy" :size="20" /></span>
                  <strong>Your Achievements</strong>
                </div>
                <button class="student-link-button" type="button" @click="router.push('/student/rewards')">
                  Open Rewards
                </button>
              </div>
              <div v-if="badgeCards.length" class="student-achievements-badge-grid">
                <article v-for="badge in badgeCards" :key="badge.id" class="student-achievements-badge">
                  <div class="student-achievements-badge-icon" :class="`is-${badge.tone}`">
                    <IconGlyph :name="badge.icon" :size="24" />
                  </div>
                  <strong>{{ badge.title }}</strong>
                  <span>{{ badge.subtitle }}</span>
                </article>
              </div>
              <StatePanel
                v-else
                icon="rewards"
                title="No badges yet"
                message="Badges will appear here after you complete lessons, quizzes, and streak goals."
              />
            </article>
          </div>

          <div class="student-achievements-bottom-grid">
            <article class="panel student-achievements-milestones-card">
              <div class="student-section-heading">
                <span><IconGlyph name="rewards" :size="20" /></span>
                <strong>Recent Milestones</strong>
              </div>
              <div v-if="milestoneCards.length" class="student-achievements-milestone-list">
                <article v-for="item in milestoneCards" :key="item.title" class="student-achievements-milestone">
                  <span :class="`is-${item.tone}`"><IconGlyph :name="item.icon" :size="18" /></span>
                  <div>
                    <strong>{{ item.title }}</strong>
                    <small>{{ item.subtitle }}</small>
                  </div>
                  <b>{{ item.reward }}</b>
                </article>
              </div>
              <StatePanel
                v-else
                icon="rewards"
                title="No milestones yet"
                message="Recent point milestones will appear here after you complete learning activities."
              />
            </article>

            <article class="panel student-achievements-module-card">
              <div class="row-between">
                <div class="student-section-heading">
                  <span><IconGlyph name="learn" :size="20" /></span>
                  <strong>Recent Lesson Progress</strong>
                </div>
                <button class="student-link-button" type="button" @click="router.push('/student/learn')">
                  Open Learn
                </button>
              </div>
              <div v-if="moduleProgress.length" class="student-achievements-module-list">
                <article v-for="module in moduleProgress" :key="module.title" class="student-achievements-module-row">
                  <span :class="`is-${module.tone}`">{{ module.id }}</span>
                  <div>
                    <strong>{{ module.title }}</strong>
                    <ProgressBar :progress="module.progress" :tone="module.barTone" />
                  </div>
                  <b>{{ module.progress }}%</b>
                </article>
              </div>
              <StatePanel
                v-else
                icon="learn"
                title="No lesson progress yet"
                message="Recent lesson progress will appear here after you start learning."
              />
            </article>
          </div>
        </div>

        <aside class="student-achievements-side">
          <article class="panel student-achievements-progress-card">
            <div class="student-section-heading">
              <strong>Overall Progress</strong>
            </div>
            <ProgressRing
              :progress="progress.overallCompletionPercent"
              title="Course Progress"
              subtitle="You're doing great!"
            />
            <div class="student-achievements-progress-mini">
              <strong>{{ progress.completedLessons }} / {{ progress.totalLessonsStarted }}</strong>
              <span>Units Completed</span>
              <ProgressBar :progress="progress.overallCompletionPercent" />
            </div>
          </article>

          <article class="student-achievements-encourage-card">
            <strong>Keep learning, keep growing!</strong>
            <p>You're doing amazing!</p>
            <span><IconGlyph name="fun" :size="28" /></span>
          </article>
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
/**
 * Student achievement / badge / streak surface.
 *
 * <p>Aggregates badge progress, milestones, lesson completion
 * markers, and the streak builder into a single dense layout.
 * Readiness lists are computed from the same dashboard payload so
 * the page stays single-fetch, and empty states use custom
 * achievement-tone cards rather than the generic StatePanel for
 * morale-positive copy.</p>
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import ProgressRing from '@/components/common/ProgressRing.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useResource } from '@/composables/useResource'
import {
  studentBadges,
  studentPointsHistory,
  studentProgress as studentProgressDemo,
  studentRewards,
  studentStreak,
} from '@/demo/data'
import { formatDate, formatNumber } from '@/utils/format'

const router = useRouter()
const badgesResource = useResource(() => studentApi.badges(), studentBadges)
const streakResource = useResource(() => studentApi.streak(), studentStreak)
const rewardsResource = useResource(() => studentApi.rewards(), studentRewards)
const progressResource = useResource(() => studentApi.progress(), studentProgressDemo)
const pointsResource = useResource(() => studentApi.pointsHistory(), studentPointsHistory)

const emptyStreak = {
  currentStreak: 0,
  bestStreak: 0,
  lastLearningDate: null,
}
const emptyRewards = {
  totalPoints: 0,
  currentLevelNumber: 1,
  progressToNextLevelPercent: 0,
  streak: emptyStreak,
  badges: [],
  recentPoints: [],
}
const emptyProgress = {
  totalLessonsStarted: 0,
  completedLessons: 0,
  quizAverage: 0,
  overallCompletionPercent: 0,
  weakAreas: [],
  lastActivitySummary: null,
  recentLessons: [],
}

const badges = computed(() => badgesResource.data.value ?? (badgesResource.usingFallback.value ? studentBadges : []))
const streak = computed(
  () => streakResource.data.value ?? (streakResource.usingFallback.value ? studentStreak : emptyStreak),
)
const rewards = computed(
  () => rewardsResource.data.value ?? (rewardsResource.usingFallback.value ? studentRewards : emptyRewards),
)
const progress = computed(
  () => progressResource.data.value ?? (progressResource.usingFallback.value ? studentProgressDemo : emptyProgress),
)
const pointsHistory = computed(
  () =>
    pointsResource.data.value ??
    (pointsResource.usingFallback.value ? studentPointsHistory : rewards.value.recentPoints),
)
const pageLoading = computed(
  () =>
    badgesResource.loading.value ||
    streakResource.loading.value ||
    rewardsResource.loading.value ||
    progressResource.loading.value ||
    pointsResource.loading.value,
)
const pageError = computed(
  () =>
    badgesResource.error.value ||
    streakResource.error.value ||
    rewardsResource.error.value ||
    progressResource.error.value ||
    pointsResource.error.value,
)
const usingFallback = computed(
  () =>
    badgesResource.usingFallback.value ||
    streakResource.usingFallback.value ||
    rewardsResource.usingFallback.value ||
    progressResource.usingFallback.value ||
    pointsResource.usingFallback.value,
)
const levelTitle = computed(() => {
  if (rewards.value.currentLevelNumber >= 10) return 'Mandarin Champion'
  if (rewards.value.currentLevelNumber >= 5) return 'Confident Speaker'
  if (rewards.value.currentLevelNumber >= 3) return 'Little Learner'
  return 'New Explorer'
})

const achievementStats = computed(() => [
  { icon: 'streak', value: String(streak.value.currentStreak), label: 'Day Streak' },
  { icon: 'star', value: formatNumber(rewards.value.totalPoints), label: 'XP' },
  { icon: 'rewards', value: `${progress.value.quizAverage}%`, label: 'Quiz Avg' },
  {
    icon: 'shield',
    value: String(badges.value.length > 0 ? badges.value.length : rewards.value.badges.length),
    label: 'Badges',
  },
])

const badgeCards = computed(() =>
  badges.value.slice(0, 5).map((badge) => ({
    id: badge.badgeId,
    icon: iconForBadge(badge.iconKey),
    title: badge.name,
    subtitle: badge.description,
    tone: toneForBadge(badge.iconKey),
  })),
)

const chartPoints = computed(() => {
  const lessons = progress.value.recentLessons.length
    ? progress.value.recentLessons
    : [{ completionPercent: progress.value.overallCompletionPercent }]

  return lessons.slice(0, 6).map((lesson, index, collection) => ({
    left: `${collection.length === 1 ? 50 : Math.round((index / Math.max(collection.length - 1, 1)) * 82 + 6)}%`,
    bottom: `${Math.max(10, Math.min(78, lesson.completionPercent))}%`,
  }))
})

const milestoneCards = computed(() =>
  pointsHistory.value.slice(0, 4).map((item) => ({
    icon: iconForSource(item.sourceType),
    title: item.reason,
    subtitle: `${formatDate(item.createdAt)} - ${formatSource(item.sourceType)}`,
    reward: `${item.pointsDelta >= 0 ? '+' : ''}${item.pointsDelta} XP`,
    tone: toneForSource(item.sourceType),
  })),
)

type ProgressTone = 'primary' | 'success' | 'warning' | 'info'

const moduleProgress = computed<
  Array<{
    id: string
    title: string
    progress: number
    tone: string
    barTone: ProgressTone
  }>
>(() =>
  progress.value.recentLessons.slice(0, 5).map((lesson, index) => ({
    id: lesson.lessonId ? `L${lesson.lessonId}` : String(index + 1),
    title: lesson.lessonTitle || `Lesson ${index + 1}`,
    progress: Math.round(lesson.completionPercent),
    tone: lesson.completionPercent >= 80 ? 'green' : lesson.completionPercent >= 50 ? 'orange' : 'blue',
    barTone: lesson.completionPercent >= 80 ? 'success' : lesson.completionPercent >= 50 ? 'warning' : 'info',
  })),
)

function iconForSource(sourceType: string) {
  const normalized = sourceType.toUpperCase()
  if (normalized.includes('QUIZ')) return 'quiz'
  if (normalized.includes('STREAK')) return 'streak'
  if (normalized.includes('LESSON')) return 'status'
  return 'star'
}

function iconForBadge(iconKey?: string | null) {
  const normalized = (iconKey ?? '').toUpperCase()
  if (normalized.includes('STREAK') || normalized.includes('FIRE')) return 'streak'
  if (normalized.includes('QUIZ')) return 'quiz'
  if (normalized.includes('SPEAK')) return 'moderation'
  if (normalized.includes('LESSON') || normalized.includes('BOOK')) return 'learn'
  if (normalized.includes('LEVEL') || normalized.includes('CROWN')) return 'trophy'
  return 'trophy'
}

function toneForBadge(iconKey?: string | null) {
  const normalized = (iconKey ?? '').toUpperCase()
  if (normalized.includes('STREAK') || normalized.includes('FIRE')) return 'pink'
  if (normalized.includes('QUIZ')) return 'blue'
  if (normalized.includes('LESSON') || normalized.includes('BOOK')) return 'green'
  if (normalized.includes('LEVEL') || normalized.includes('CROWN')) return 'orange'
  return 'gold'
}

function toneForSource(sourceType: string) {
  const normalized = sourceType.toUpperCase()
  if (normalized.includes('QUIZ')) return 'blue'
  if (normalized.includes('STREAK')) return 'orange'
  if (normalized.includes('LESSON')) return 'green'
  return 'purple'
}

function formatSource(sourceType: string) {
  return sourceType
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

function reloadAll() {
  void badgesResource.reload()
  void streakResource.reload()
  void rewardsResource.reload()
  void progressResource.reload()
  void pointsResource.reload()
}
</script>
