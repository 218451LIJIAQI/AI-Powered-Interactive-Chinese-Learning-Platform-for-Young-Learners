<template>
  <div class="page-stack student-rewards-page">
    <PageHeaderBlock :title="t('studentRewards.title')" :description="t('studentRewards.intro')" icon="rewards">
      <template #actions>
        <button type="button" class="btn btn-secondary" @click="openAchievements">
          <IconGlyph name="trophy" :size="18" />
          {{ t('studentRewards.openAchievements') }}
        </button>
        <button type="button" class="btn btn-primary" @click="openRewardPractice">
          <IconGlyph name="practice" :size="18" />
          {{ t('studentRewards.continuePractice') }}
        </button>
      </template>
    </PageHeaderBlock>

    <DataNotice :using-fallback="usingFallback" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <div class="student-rewards-layout">
        <div class="student-rewards-main">
          <article class="hero-card student-rewards-hero">
            <div class="student-rewards-hero-body">
              <div class="student-rewards-level">
                <span class="student-rewards-level-badge" aria-hidden="true">
                  <IconGlyph name="trophy" :size="32" />
                </span>
                <div class="student-rewards-level-copy">
                  <span>{{ t('studentRewards.yourLevel') }}</span>
                  <strong>{{ t('studentRewards.level', { level: currentLevelNumber }) }}</strong>
                  <span>{{ currentLevelTitle }}</span>
                </div>
              </div>

              <div class="student-rewards-level-progress">
                <div class="student-rewards-level-progress-row">
                  <strong>{{ t('studentRewards.nextLevelProgress', { progress: levelProgressPercent }) }}</strong>
                  <span>{{ t('studentRewards.levelTarget', { level: nextLevelNumber }) }}</span>
                </div>
                <ProgressBar
                  class="is-lg"
                  :progress="levelProgressPercent"
                  :label="t('studentRewards.levelTarget', { level: nextLevelNumber })"
                />
                <p>
                  <strong>{{ t('studentRewards.nextMilestone') }}</strong>
                  {{
                    t('studentRewards.milestoneProgress', { progress: levelProgressPercent, level: nextLevelNumber })
                  }}
                </p>
              </div>

              <ul class="student-rewards-stats">
                <li v-for="stat in heroStats" :key="stat.label" class="student-rewards-stat">
                  <span class="student-rewards-stat-icon" aria-hidden="true">
                    <IconGlyph :name="stat.icon" :size="20" />
                  </span>
                  <strong>{{ stat.value }}</strong>
                  <span>{{ stat.label }}</span>
                </li>
              </ul>
            </div>

            <span class="student-rewards-hero-mascot" aria-hidden="true">
              <PandaIllustration :size="168" outfit="#5a35ec" accent="#ffcc2f" pose="wave" />
            </span>
          </article>

          <section class="panel student-rewards-card" :aria-labelledby="`${baseId}-badges`">
            <div class="card-header">
              <h2 :id="`${baseId}-badges`" class="card-title student-rewards-card-title">
                <span class="student-rewards-title-icon" aria-hidden="true"
                  ><IconGlyph name="trophy" :size="18"
                /></span>
                {{ t('studentRewards.earnedBadges') }}
              </h2>
              <button type="button" class="btn btn-link" @click="openAchievements">
                {{ t('studentRewards.openAchievements') }}
              </button>
            </div>

            <ul v-if="earnedBadgeCards.length" class="student-badge-grid">
              <li v-for="badge in earnedBadgeCards" :key="badge.id" class="student-badge-card">
                <span class="student-badge-emblem" :class="`is-${badge.tone}`" aria-hidden="true">
                  <IconGlyph :name="badge.icon" :size="26" />
                </span>
                <strong>{{ badge.title }}</strong>
                <span>{{ badge.subtitle }}</span>
                <span class="student-badge-status">
                  <IconGlyph name="status" :size="14" />
                  {{ badge.statusLabel }}
                </span>
              </li>
            </ul>
            <StatePanel
              v-else
              tone="empty"
              icon="trophy"
              :title="t('studentRewards.noBadges')"
              :message="t('studentRewards.noBadgesHint')"
              compact
            >
              <template #actions>
                <button type="button" class="btn btn-secondary btn-sm" @click="openRewardPractice">
                  <IconGlyph name="practice" :size="16" />
                  {{ t('studentRewards.startPractice') }}
                </button>
              </template>
            </StatePanel>
          </section>

          <section class="panel student-rewards-card" :aria-labelledby="`${baseId}-journey`">
            <div class="card-header">
              <div>
                <h2 :id="`${baseId}-journey`" class="card-title student-rewards-card-title">
                  <span class="student-rewards-title-icon" aria-hidden="true">
                    <IconGlyph name="progress" :size="18" />
                  </span>
                  {{ t('studentRewards.journey') }}
                </h2>
                <p class="card-subtitle">{{ t('studentRewards.journeyHint') }}</p>
              </div>
            </div>

            <ol class="student-rewards-journey">
              <li
                v-for="step in journeySteps"
                :key="step.level"
                class="student-rewards-journey-step"
                :class="`is-${step.status}`"
                :aria-current="step.status === 'current' ? 'step' : undefined"
              >
                <span class="student-rewards-journey-node" aria-hidden="true">
                  <IconGlyph :name="step.icon" :size="20" />
                </span>
                <div class="student-rewards-journey-copy">
                  <strong>{{ t('studentRewards.level', { level: step.level }) }}</strong>
                  <span>{{ step.title }}</span>
                  <small>{{ step.caption }}</small>
                </div>
              </li>
            </ol>
          </section>
        </div>

        <aside class="student-rewards-side">
          <section class="panel student-rewards-card" :aria-labelledby="`${baseId}-summary`">
            <div class="card-header">
              <h2 :id="`${baseId}-summary`" class="card-title">{{ t('studentRewards.progressSummary') }}</h2>
              <StatusPill :label="t('studentRewards.liveProgress')" tone="success" />
            </div>

            <ul class="student-rewards-list">
              <li v-for="goal in progressSummary" :key="goal.title" class="student-rewards-goal">
                <span class="student-rewards-row-icon" :class="`is-${goal.tone}`" aria-hidden="true">
                  <IconGlyph :name="goal.icon" :size="18" />
                </span>
                <div class="student-rewards-goal-copy">
                  <div class="student-rewards-goal-head">
                    <strong>{{ goal.title }}</strong>
                    <span>{{ goal.count }}</span>
                  </div>
                  <ProgressBar class="is-sm" :progress="goal.progress" :label="goal.title" />
                  <small>{{ goal.statusLabel }}</small>
                </div>
              </li>
            </ul>

            <div class="card-footer">
              <button type="button" class="btn btn-ghost btn-sm" @click="openRewardPractice">
                <IconGlyph name="rewards" :size="16" />
                {{ t('studentRewards.practiceXp') }}
              </button>
            </div>
          </section>

          <article class="student-rewards-tip">
            <span class="student-rewards-tip-icon" aria-hidden="true"><IconGlyph name="rewards" :size="22" /></span>
            <div>
              <strong>{{ t('studentRewards.keepEarning') }}</strong>
              <p>{{ t('studentRewards.earningHint') }}</p>
            </div>
          </article>

          <section class="panel student-rewards-card" :aria-labelledby="`${baseId}-history`">
            <div class="card-header">
              <h2 :id="`${baseId}-history`" class="card-title">{{ t('studentRewards.history') }}</h2>
              <button type="button" class="btn btn-link" @click="openProfile">
                {{ t('studentRewards.pointsActivity') }}
              </button>
            </div>

            <ul v-if="rewardHistoryCards.length" class="student-rewards-list">
              <li v-for="item in rewardHistoryCards" :key="item.id" class="student-rewards-row">
                <span class="student-rewards-row-icon is-purple" aria-hidden="true">
                  <IconGlyph :name="item.icon" :size="18" />
                </span>
                <div class="student-rewards-row-copy">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.date }}</span>
                </div>
                <strong class="student-rewards-points">{{ item.pointsLabel }} XP</strong>
              </li>
            </ul>
            <StatePanel
              v-else
              tone="empty"
              icon="streak"
              :title="t('studentRewards.noHistory')"
              :message="t('studentRewards.noHistoryHint')"
              compact
            />
          </section>

          <section class="panel student-rewards-card" :aria-labelledby="`${baseId}-achievements`">
            <div class="card-header">
              <h2 :id="`${baseId}-achievements`" class="card-title">{{ t('studentRewards.achievements') }}</h2>
              <button type="button" class="btn btn-link" @click="openAchievements">
                {{ t('studentRewards.open') }}
              </button>
            </div>

            <ul v-if="achievementFeed.length" class="student-rewards-list">
              <li v-for="item in achievementFeed" :key="item.id" class="student-rewards-row">
                <span class="student-rewards-row-icon" :class="`is-${item.tone}`" aria-hidden="true">
                  <IconGlyph :name="item.icon" :size="18" />
                </span>
                <div class="student-rewards-row-copy">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.subtitle }}</span>
                  <small v-if="item.date">{{ item.date }}</small>
                </div>
                <span class="chip">{{ item.reward }}</span>
              </li>
            </ul>
            <StatePanel
              v-else
              tone="empty"
              icon="star"
              :title="t('studentRewards.noAchievements')"
              :message="t('studentRewards.noAchievementsHint')"
              compact
            />
          </section>
        </aside>
      </div>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import {
  activityLibrary,
  studentDashboard as studentDashboardDemo,
  studentPointsHistory,
  studentProgress as studentProgressDemo,
  studentRewards,
} from '@/demo/data'
import { studentRewardsMessages } from '@/plugins/studentRewardsMessages'
import {
  clampPercent,
  formatDate,
  formatNumber,
  nonNegativeInteger,
  ratioPercent,
  toFiniteNumber,
} from '@/utils/format'
import { inferPracticeActivityFromText, normalizePracticeActivity, practiceRoute } from '@/utils/practiceRoute'

const { t } = useI18n({
  messages: {
    en: { studentRewards: studentRewardsMessages.en },
    ms: { studentRewards: studentRewardsMessages.ms },
    'zh-CN': { studentRewards: studentRewardsMessages['zh-CN'] },
  },
})

type BadgeIcon = 'star' | 'learn' | 'live' | 'trophy'
type JourneyStatus = 'complete' | 'current' | 'future'

const router = useRouter()
const baseId = useId()

const resource = useResource(() => studentApi.rewards(), studentRewards)
const historyResource = useResource(() => studentApi.pointsHistory(), studentPointsHistory)
const dashboardResource = useResource(() => studentApi.dashboard(), studentDashboardDemo)
const progressResource = useResource(() => studentApi.progress(), studentProgressDemo)
const activitiesResource = useResource(() => studentApi.activityLibrary({ size: 20 }), activityLibrary)

const emptyRewards = {
  totalPoints: 0,
  currentLevelNumber: 1,
  progressToNextLevelPercent: 0,
  streak: {
    currentStreak: 0,
    bestStreak: 0,
    lastLearningDate: null,
  },
  badges: [],
  recentPoints: [],
}
const emptyDashboard = {
  fullName: '',
  currentLevel: '',
  learningProgressPercent: 0,
  dailyStreak: 0,
  points: 0,
  badgeCount: 0,
  recentQuizResults: [],
  weakAreas: [],
  unfinishedLessons: [],
  recentNotifications: [],
  recommendedNextStep: null,
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
const emptyActivities = {
  items: [],
  page: 0,
  size: 0,
  totalItems: 0,
  totalPages: 0,
}

const rewards = computed(() => resource.data.value ?? (resource.usingFallback.value ? studentRewards : emptyRewards))
const pointsHistory = computed(
  () => historyResource.data.value ?? (historyResource.usingFallback.value ? studentPointsHistory : []),
)
const dashboard = computed(
  () => dashboardResource.data.value ?? (dashboardResource.usingFallback.value ? studentDashboardDemo : emptyDashboard),
)
const progress = computed(
  () => progressResource.data.value ?? (progressResource.usingFallback.value ? studentProgressDemo : emptyProgress),
)
const activities = computed(
  () => activitiesResource.data.value ?? (activitiesResource.usingFallback.value ? activityLibrary : emptyActivities),
)
const recommendedPracticeActivity = computed(() => {
  const nextIncomplete = activities.value.items.find((item) => !item.completed)
  const fromLibrary = normalizePracticeActivity(nextIncomplete?.type)
  if (fromLibrary) return fromLibrary

  return inferPracticeActivityFromText(progress.value.weakAreas[0] ?? dashboard.value.recommendedNextStep)
})

const usingFallback = computed(
  () =>
    resource.usingFallback.value ||
    historyResource.usingFallback.value ||
    dashboardResource.usingFallback.value ||
    progressResource.usingFallback.value ||
    activitiesResource.usingFallback.value,
)
const pageLoading = computed(
  () =>
    resource.loading.value ||
    historyResource.loading.value ||
    dashboardResource.loading.value ||
    progressResource.loading.value ||
    activitiesResource.loading.value,
)
const pageError = computed(
  () =>
    resource.error.value ||
    historyResource.error.value ||
    dashboardResource.error.value ||
    progressResource.error.value ||
    activitiesResource.error.value,
)

function levelTitleFor(levelNumber: number): string {
  const keys: Record<number, string> = {
    1: 'starter',
    2: 'pinyinExplorer',
    3: 'developingLearner',
    4: 'wordBuilder',
    5: 'sentenceMaker',
  }
  const key = keys[levelNumber]
  return key ? t(`studentRewards.${key}`) : t('studentRewards.level', { level: levelNumber })
}

const currentLevelNumber = computed(() => Math.max(1, nonNegativeInteger(rewards.value.currentLevelNumber, 1)))
const nextLevelNumber = computed(() => currentLevelNumber.value + 1)
const levelProgressPercent = computed(() => clampPercent(rewards.value.progressToNextLevelPercent))
const currentStreakCount = computed(() => nonNegativeInteger(rewards.value.streak.currentStreak))
const quizAveragePercent = computed(() => clampPercent(progress.value.quizAverage))
const currentLevelTitle = computed(() => levelTitleFor(currentLevelNumber.value))
const recentQuizCount = computed(() => dashboard.value.recentQuizResults.length)
const quizzesAbove80 = computed(
  () => dashboard.value.recentQuizResults.filter((item) => ratioPercent(item.score, item.maxScore) >= 80).length,
)
const completedLessonCount = computed(() => nonNegativeInteger(progress.value.completedLessons))
const startedLessonCount = computed(() =>
  Math.max(completedLessonCount.value, nonNegativeInteger(progress.value.totalLessonsStarted)),
)
const completedActivityCount = computed(() => activities.value.items.filter((item) => item.completed).length)

const heroStats = computed(() => [
  { label: t('studentRewards.totalPoints'), value: formatNumber(rewards.value.totalPoints), icon: 'star' },
  {
    label: t('studentRewards.currentStreak'),
    value: t('studentRewards.days', { count: currentStreakCount.value }),
    icon: 'streak',
  },
  {
    label: t('studentRewards.badgeCount'),
    value: String(nonNegativeInteger(dashboard.value.badgeCount)),
    icon: 'rewards',
  },
])

// Only badges actually returned by the rewards API are shown (D21: no invented or derived badges).
const earnedBadgeCards = computed(() =>
  rewards.value.badges.slice(0, 10).map((badge, index) => ({
    id: `earned-${badge.badgeId}`,
    title: badge.name,
    subtitle: badge.description,
    statusLabel: badge.awardedAt
      ? t('studentRewards.earnedOn', { date: formatDate(badge.awardedAt) })
      : t('studentRewards.earned'),
    icon: badgeIconFor(badge.iconKey),
    tone: earnedBadgeTone(index),
  })),
)

// Recent achievements are built only from real learner data, dated only with real timestamps (D21).
const achievementFeed = computed(() => {
  const items: Array<{
    id: string
    icon: string
    title: string
    subtitle: string
    reward: string
    date: string
    tone: string
  }> = []

  if (currentStreakCount.value > 0) {
    const lastLearningDate = rewards.value.streak.lastLearningDate
    items.push({
      id: 'streak',
      icon: 'star',
      title: t('studentRewards.streakAchievement'),
      subtitle: t('studentRewards.streakAchievementHint', { count: currentStreakCount.value }),
      reward: t('studentRewards.dayStreak', { count: currentStreakCount.value }),
      date: lastLearningDate ? formatDate(lastLearningDate) : '',
      tone: 'purple',
    })
  }

  const latestQuiz = dashboard.value.recentQuizResults[0]
  if (latestQuiz) {
    items.push({
      id: 'quiz',
      icon: 'quiz',
      title: t('studentRewards.quizProgress'),
      subtitle: t('studentRewards.quizProgressHint', { score: quizAveragePercent.value }),
      reward: t('studentRewards.average', { score: quizAveragePercent.value }),
      date: latestQuiz.completedAt ? formatDate(latestQuiz.completedAt) : '',
      tone: 'green',
    })
  }

  if (completedActivityCount.value > 0) {
    items.push({
      id: 'practice',
      icon: 'live',
      title: t('studentRewards.practiceExplorer'),
      subtitle: t('studentRewards.practiceExplorerHint', { count: completedActivityCount.value }),
      reward: t('studentRewards.activities', { count: completedActivityCount.value }),
      date: '',
      tone: 'orange',
    })
  }

  return items
})

// Progress summary uses the learner's real counts only; no invented weekly targets (D21).
const progressSummary = computed(() => [
  {
    title: t('studentRewards.lessonsCompleted'),
    count: `${completedLessonCount.value} / ${startedLessonCount.value}`,
    progress: ratioPercent(completedLessonCount.value, startedLessonCount.value),
    statusLabel: t('studentRewards.ofStartedLessons'),
    icon: 'learn',
    tone: 'green',
  },
  {
    title: t('studentRewards.activitiesCompleted'),
    count: `${completedActivityCount.value} / ${activities.value.items.length}`,
    progress: ratioPercent(completedActivityCount.value, activities.value.items.length),
    statusLabel: t('studentRewards.ofAvailableActivities'),
    icon: 'live',
    tone: 'purple',
  },
  {
    title: t('studentRewards.quizzesAbove80'),
    count: `${quizzesAbove80.value} / ${recentQuizCount.value}`,
    progress: ratioPercent(quizzesAbove80.value, recentQuizCount.value),
    statusLabel: t('studentRewards.ofRecentQuizzes'),
    icon: 'clip',
    tone: 'orange',
  },
])

const rewardHistoryCards = computed(() =>
  pointsHistory.value.map((item, index) => ({
    id: item.id,
    title: item.reason,
    date: formatDate(item.createdAt),
    pointsLabel: signedIntegerLabel(item.pointsDelta),
    icon: rewardHistoryIcon(index),
  })),
)

const journeySteps = computed(() =>
  Array.from({ length: 5 }, (_, index) => {
    const level = index + 1
    const status = journeyStatusFor(level)
    return {
      level,
      title: levelTitleFor(level),
      caption: journeyCaptionFor(level, status),
      status,
      icon: journeyIconFor(status),
    }
  }),
)

function badgeIconFor(iconKey: string | null | undefined): BadgeIcon {
  if (iconKey === 'star') return 'star'
  if (iconKey === 'book') return 'learn'
  if (iconKey === 'mic') return 'live'
  return 'trophy'
}

function earnedBadgeTone(index: number): string {
  if (index === 0) return 'gold'
  if (index === 1) return 'green'
  return 'blue'
}

function rewardHistoryIcon(index: number): string {
  if (index === 0) return 'rewards'
  if (index === 1) return 'learn'
  return 'status'
}

function journeyStatusFor(level: number): JourneyStatus {
  if (level < currentLevelNumber.value) return 'complete'
  return level === currentLevelNumber.value ? 'current' : 'future'
}

function journeyCaptionFor(level: number, status: JourneyStatus): string {
  if (status === 'complete') return t('studentRewards.completed')
  if (status === 'current') return t('studentRewards.nextLevelProgress', { progress: levelProgressPercent.value })
  return level === nextLevelNumber.value ? t('studentRewards.nextUp') : t('studentRewards.futureLevel')
}

function journeyIconFor(status: JourneyStatus): string {
  if (status === 'complete') return 'star'
  if (status === 'current') return 'trophy'
  return 'progress'
}

function openAchievements(): void {
  void router.push('/student/achievements')
}

function openProfile(): void {
  void router.push('/student/profile')
}

function openRewardPractice(): void {
  void router.push(practiceRoute(recommendedPracticeActivity.value))
}

function signedIntegerLabel(value: number): string {
  const rounded = Math.round(toFiniteNumber(value))
  return rounded > 0 ? `+${rounded}` : String(rounded)
}

function reloadAll(): void {
  void resource.reload()
  void historyResource.reload()
  void dashboardResource.reload()
  void progressResource.reload()
  void activitiesResource.reload()
}
</script>

<style scoped>
.student-rewards-page {
  display: grid;
  gap: var(--page-gap);
}

.student-rewards-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 360px);
  gap: var(--page-gap);
  align-items: start;
}

.student-rewards-main,
.student-rewards-side {
  display: grid;
  gap: var(--page-gap);
  align-content: start;
  align-items: start;
  min-width: 0;
}

/* Level hero --------------------------------------------------------------- */
.student-rewards-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: var(--space-4);
}

.student-rewards-hero-body {
  display: grid;
  gap: var(--space-5);
  min-width: 0;
}

.student-rewards-level {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  min-width: 0;
}

.student-rewards-level-badge {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: var(--space-16);
  height: var(--space-16);
  border-radius: var(--radius-lg);
  background: var(--color-accent-500);
  color: var(--text-on-accent);
  box-shadow: var(--shadow-accent);
}

.student-rewards-level-copy {
  display: grid;
  gap: var(--space-0-5);
  min-width: 0;
}

.student-rewards-level-copy span {
  color: var(--text-on-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}

.student-rewards-level-copy strong {
  color: var(--text-on-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-4xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.student-rewards-level-progress {
  display: grid;
  gap: var(--space-2);
  max-width: var(--reading-max);
}

.student-rewards-level-progress-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-2);
  font-size: var(--font-size-sm);
}

.student-rewards-level-progress-row span,
.student-rewards-level-progress p {
  color: var(--text-on-primary);
}

.student-rewards-level-progress p {
  display: grid;
  gap: var(--space-0-5);
  margin: 0;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.student-rewards-level-progress p strong {
  color: var(--text-on-primary);
}

.student-rewards-level-progress :deep(.progress-bar) {
  background: var(--color-white-a24);
}

.student-rewards-level-progress :deep(.progress-bar > span) {
  background: var(--color-accent-500);
}

.student-rewards-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 140px), 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-rewards-stat {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  grid-template-rows: auto auto;
  column-gap: var(--space-3);
  align-items: center;
  padding: var(--space-3);
  border: 1px solid var(--color-white-a24);
  border-radius: var(--radius-lg);
  background: var(--surface-on-brand);
}

.student-rewards-stat-icon {
  display: grid;
  grid-row: span 2;
  place-items: center;
  width: var(--space-10);
  height: var(--space-10);
  border-radius: var(--radius-md);
  background: var(--color-white);
  color: var(--color-primary-600);
}

.student-rewards-stat strong {
  color: var(--text-on-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
  overflow-wrap: anywhere;
}

.student-rewards-stat span:last-child {
  color: var(--text-on-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.student-rewards-hero-mascot {
  display: block;
  margin-bottom: calc(-1 * var(--card-padding));
}

/* Cards -------------------------------------------------------------------- */
.student-rewards-card {
  display: grid;
  gap: var(--space-4);
}

.student-rewards-card .card-header {
  align-items: center;
  flex-wrap: wrap;
}

.student-rewards-card .card-header > div {
  display: grid;
  gap: var(--space-1);
}

.student-rewards-card-title {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
}

.student-rewards-title-icon {
  display: grid;
  place-items: center;
  width: var(--space-8);
  height: var(--space-8);
  border-radius: var(--radius-sm);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

/* Badges ------------------------------------------------------------------- */
.student-badge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 148px), 1fr));
  gap: var(--grid-gap);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-badge-card {
  display: grid;
  grid-template-rows: auto auto 1fr auto;
  justify-items: center;
  gap: var(--space-1-5);
  min-width: 0;
  padding: var(--space-4) var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
  text-align: center;
}

.student-badge-card > strong {
  margin-top: var(--space-1-5);
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.student-badge-card > span:not([class]) {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
  overflow-wrap: anywhere;
}

.student-badge-emblem {
  display: grid;
  place-items: center;
  width: var(--space-16);
  height: var(--space-16);
  border-radius: var(--radius-round);
  box-shadow: var(--shadow-sm);
}

.student-badge-emblem.is-gold {
  background: var(--color-accent-500);
  color: var(--text-on-accent);
}

.student-badge-emblem.is-green {
  background: var(--color-success-strong);
  color: var(--color-white);
}

.student-badge-emblem.is-blue {
  background: var(--color-info-strong);
  color: var(--color-white);
}

.student-badge-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  max-width: 100%;
  margin-top: var(--space-1);
  padding: var(--space-0-5) var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--color-success-soft);
  color: var(--color-success-text);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  line-height: var(--line-height-snug);
}

.student-badge-status .icon-glyph {
  flex: 0 0 auto;
}

/* Journey ------------------------------------------------------------------ */
.student-rewards-journey {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-rewards-journey-step {
  position: relative;
  display: grid;
  align-content: start;
  justify-items: center;
  gap: var(--space-2);
  min-width: 0;
  padding: 0 var(--space-1);
  text-align: center;
}

/* Connector between nodes. */
.student-rewards-journey-step + .student-rewards-journey-step::before {
  position: absolute;
  top: calc(var(--space-12) / 2);
  right: calc(50% + var(--space-6));
  left: calc(-50% + var(--space-6));
  height: var(--border-width-thick);
  border-radius: var(--radius-pill);
  background: var(--border-default);
  content: '';
}

.student-rewards-journey-step.is-complete + .student-rewards-journey-step::before {
  background: var(--color-success);
}

.student-rewards-journey-node {
  display: grid;
  place-items: center;
  width: var(--space-12);
  height: var(--space-12);
  border: var(--border-width-thick) solid var(--border-default);
  border-radius: var(--radius-round);
  background: var(--surface-sunken);
  color: var(--text-secondary);
}

.is-complete .student-rewards-journey-node {
  border-color: var(--color-success);
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.is-current .student-rewards-journey-node {
  border-color: var(--color-accent-600);
  background: var(--color-accent-500);
  color: var(--text-on-accent);
  box-shadow: var(--shadow-accent);
}

.student-rewards-journey-copy {
  display: grid;
  gap: var(--space-0-5);
  min-width: 0;
}

.student-rewards-journey-copy strong {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
}

.student-rewards-journey-copy span {
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.student-rewards-journey-copy small {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.is-complete .student-rewards-journey-copy small {
  color: var(--color-success-text);
}

.is-current .student-rewards-journey-copy small {
  color: var(--text-brand);
}

/* Side lists --------------------------------------------------------------- */
.student-rewards-list {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-rewards-list > li {
  padding: var(--space-3) 0;
  border-top: 1px solid var(--border-subtle);
}

.student-rewards-list > li:first-child {
  padding-top: 0;
  border-top: 0;
}

.student-rewards-list > li:last-child {
  padding-bottom: 0;
}

.student-rewards-goal,
.student-rewards-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
}

.student-rewards-goal {
  grid-template-columns: auto minmax(0, 1fr);
}

.student-rewards-row-icon {
  display: grid;
  place-items: center;
  width: var(--space-10);
  height: var(--space-10);
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.student-rewards-row-icon.is-green {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.student-rewards-row-icon.is-orange {
  background: var(--color-accent-100);
  color: var(--color-accent-800);
}

.student-rewards-goal-copy,
.student-rewards-row-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.student-rewards-goal-head {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
}

.student-rewards-goal-head strong,
.student-rewards-row-copy strong {
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.student-rewards-goal-head span {
  flex: 0 0 auto;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  font-variant-numeric: tabular-nums;
}

.student-rewards-goal-copy small,
.student-rewards-row-copy span,
.student-rewards-row-copy small {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
  overflow-wrap: anywhere;
}

.student-rewards-points {
  color: var(--color-success-text);
  font-size: var(--font-size-md);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.student-rewards-card .card-footer {
  justify-content: flex-start;
}

.student-rewards-tip {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-accent-200);
  border-radius: var(--radius-lg);
  background: var(--color-accent-50);
}

.student-rewards-tip-icon {
  display: grid;
  place-items: center;
  width: var(--space-10);
  height: var(--space-10);
  border-radius: var(--radius-md);
  background: var(--color-accent-100);
  color: var(--color-accent-800);
}

.student-rewards-tip strong {
  color: var(--text-primary);
  font-size: var(--font-size-md);
}

.student-rewards-tip p {
  margin: var(--space-1) 0 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

@media (max-width: 1200px) {
  .student-rewards-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-rewards-side {
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 320px), 1fr));
  }
}

@media (max-width: 900px) {
  .student-rewards-hero-mascot {
    display: none;
  }

  .student-rewards-hero {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .student-rewards-journey {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-3);
  }

  .student-rewards-journey-step {
    grid-template-columns: auto minmax(0, 1fr);
    justify-items: start;
    align-items: center;
    gap: var(--space-3);
    padding: 0;
    text-align: left;
  }

  .student-rewards-journey-step + .student-rewards-journey-step::before {
    display: none;
  }

  .student-rewards-level-copy strong {
    font-size: var(--font-size-3xl);
  }
}
</style>
