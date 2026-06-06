<template>
  <div class="page-stack student-rewards-page">
    <section class="student-simple-header">
      <h1>
        Rewards <span><IconGlyph name="rewards" :size="28" /></span>
      </h1>
      <p>Keep learning, earn points, and unlock milestones that make practice feel playful.</p>
    </section>

    <DataNotice :using-fallback="usingFallback" :error="pageError" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="student-rewards-layout">
        <div class="student-rewards-main">
          <article class="student-rewards-hero">
            <span class="student-rewards-confetti is-1" />
            <span class="student-rewards-confetti is-2" />
            <span class="student-rewards-confetti is-3" />
            <span class="student-rewards-hero-pagoda" />

            <div class="student-rewards-level">
              <span class="student-rewards-level-badge"><IconGlyph name="trophy" :size="44" /></span>
              <div class="student-rewards-level-copy">
                <small>Your Level</small>
                <strong>Level {{ currentLevelNumber }}</strong>
                <span>{{ currentLevelTitle }}</span>
              </div>
              <div class="student-rewards-level-progress">
                <ProgressBar :progress="levelProgressPercent" />
                <strong>{{ levelProgressPercent }}% to next level</strong>
              </div>
            </div>

            <div class="student-rewards-hero-center">
              <div class="student-rewards-top-stats">
                <div v-for="stat in heroStats" :key="stat.label" class="student-rewards-top-stat">
                  <span class="student-rewards-top-icon"><IconGlyph :name="stat.icon" :size="20" /></span>
                  <div>
                    <small>{{ stat.label }}</small>
                    <strong>{{ stat.value }}</strong>
                  </div>
                </div>
              </div>

              <div class="student-rewards-next-card">
                <span class="student-rewards-next-icon"><IconGlyph name="rewards" :size="26" /></span>
                <div class="student-rewards-next-copy">
                  <strong>Next Milestone</strong>
                  <p>Progress is {{ levelProgressPercent }}% toward Level {{ nextLevelNumber }}.</p>
                  <div class="student-rewards-next-progress">
                    <ProgressBar :progress="levelProgressPercent" />
                    <span>Level {{ nextLevelNumber }} target</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="student-rewards-hero-mascot">
              <PandaIllustration :size="188" outfit="#7c58ff" accent="#f6d056" pose="wave" />
            </div>
          </article>

          <article class="panel student-rewards-badges-card">
            <div class="row-between">
              <div class="student-section-heading">
                <span><IconGlyph name="trophy" :size="20" /></span>
                <strong>Earned Badges</strong>
              </div>
              <button type="button" class="student-link-button" @click="openAchievements">
                Open Achievements
              </button>
            </div>

            <div v-if="earnedBadgeCards.length" class="student-badge-grid">
              <article
                v-for="badge in earnedBadgeCards"
                :key="badge.id"
                class="student-badge-card"
                :class="{ 'is-locked': badge.locked }"
              >
                <div class="student-badge-emblem" :class="`is-${badge.tone}`">
                  <span><IconGlyph :name="badge.icon" :size="26" /></span>
                  <small v-if="badge.locked"><IconGlyph name="safe" :size="14" /></small>
                </div>
                <strong>{{ badge.title }}</strong>
                <span>{{ badge.subtitle }}</span>
                <div class="student-badge-status is-earned">
                  <IconGlyph name="status" :size="12" /> {{ badge.statusLabel }}
                </div>
              </article>
            </div>
            <div v-else class="student-badges-empty">
              <div class="student-badges-empty-head">
                <span><IconGlyph name="trophy" :size="22" /></span>
                <div>
                  <strong>No earned badges yet</strong>
                  <small>Finish your first lesson, quiz, or streak to claim your first badge.</small>
                </div>
              </div>
              <ul class="student-badges-empty-list">
                <li><IconGlyph name="learn" :size="14" /> Complete one lesson to unlock the Pinyin Explorer badge</li>
                <li><IconGlyph name="quiz" :size="14" /> Score 80%+ in a quiz to unlock Quiz Starter</li>
                <li><IconGlyph name="streak" :size="14" /> Learn 7 days in a row to unlock 7-Day Streak</li>
              </ul>
              <button type="button" class="student-badges-empty-cta" @click="openRewardPractice">
                <IconGlyph name="practice" :size="16" />
                Start practising
                <IconGlyph name="chevronRight" :size="14" />
              </button>
            </div>

            <div class="student-badges-divider" />

            <div class="row-between">
              <div class="student-section-heading">
                <span><IconGlyph name="safe" :size="20" /></span>
                <strong>Locked Badges</strong>
              </div>
              <span class="student-badges-meta">{{ lockedBadgeCards.length }} to unlock</span>
            </div>

            <div class="student-badge-grid student-badge-grid--locked">
              <article v-for="badge in lockedBadgeCards" :key="badge.id" class="student-badge-card is-locked">
                <div class="student-badge-emblem is-gray">
                  <span><IconGlyph :name="badge.icon" :size="26" /></span>
                  <small><IconGlyph name="safe" :size="14" /></small>
                </div>
                <strong>{{ badge.title }}</strong>
                <span>{{ badge.subtitle }}</span>
                <div class="student-badge-status is-locked">
                  <IconGlyph name="safe" :size="12" /> {{ badge.statusLabel }}
                </div>
              </article>
            </div>

            <p class="student-badges-note">
              Keep going! Reward progress stays aligned with your real learning activity and streak data.
            </p>
          </article>

          <article class="panel student-rewards-journey-card">
            <div class="student-section-heading">
              <span><IconGlyph name="progress" :size="20" /></span>
              <div>
                <strong>Your Learning Journey</strong>
                <p>Milestones reached so far and what comes next.</p>
              </div>
            </div>

            <div class="student-journey-track">
              <span class="student-journey-line" />
              <article
                v-for="step in journeySteps"
                :key="step.level"
                class="student-journey-step"
                :class="{
                  'is-complete': step.status === 'complete',
                  'is-current': step.status === 'current',
                  'is-future': step.status === 'future',
                }"
              >
                <span class="student-journey-node"><IconGlyph :name="step.icon" :size="24" /></span>
                <strong>Level {{ step.level }}</strong>
                <span>{{ step.title }}</span>
                <small>{{ step.caption }}</small>
              </article>
            </div>
          </article>
        </div>

        <aside class="student-rewards-side">
          <article class="panel student-weekly-goals-card">
            <div class="row-between">
              <div class="student-section-heading">
                <span><IconGlyph name="trophy" :size="20" /></span>
                <strong>Reward Progress Goals</strong>
              </div>
              <span class="student-weekly-reset">Live progress</span>
            </div>

            <div class="student-weekly-goal-list">
              <article v-for="goal in weeklyGoals" :key="goal.title" class="student-weekly-goal-item">
                <span class="student-weekly-goal-icon" :class="`is-${goal.tone}`"
                  ><IconGlyph :name="goal.icon" :size="18"
                /></span>
                <div class="student-weekly-goal-copy">
                  <strong>{{ goal.title }}</strong>
                  <ProgressBar :progress="goal.progress" />
                </div>
                <div class="student-weekly-goal-side">
                  <span>{{ goal.count }}</span>
                  <strong>{{ goal.statusLabel }}</strong>
                </div>
              </article>
            </div>

            <button type="button" class="student-weekly-bonus" @click="openRewardPractice">
              <span><IconGlyph name="rewards" :size="18" /></span>
              Open practice to keep earning XP
            </button>
          </article>

          <article class="student-rewards-promo-card">
            <div class="student-rewards-promo-copy">
              <strong>Keep Earning Points!</strong>
              <p>
                Reward tracking focuses on points, levels, streaks, and badges so every practice session shows visible
                progress.
              </p>
              <button type="button" class="btn btn-primary" @click="openRewardPractice">Continue Practice</button>
            </div>
            <div class="student-rewards-promo-chest"><IconGlyph name="rewards" :size="58" /></div>
          </article>

          <article class="panel student-reward-history-card">
            <div class="row-between">
              <div class="student-section-heading">
                <span><IconGlyph name="file" :size="20" /></span>
                <strong>Reward History</strong>
              </div>
              <button type="button" class="student-link-button" @click="openProfile">
                Points Activity
              </button>
            </div>

            <div v-if="rewardHistoryCards.length" class="student-reward-history-list">
              <article v-for="item in rewardHistoryCards" :key="item.id" class="student-reward-history-item">
                <span class="student-reward-history-icon"><IconGlyph :name="item.icon" :size="18" /></span>
                <div class="student-reward-history-copy">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.date }}</span>
                </div>
                <strong class="student-reward-history-points">{{ item.pointsLabel }} XP</strong>
              </article>
            </div>
            <div v-else class="student-reward-history-empty">
              <span><IconGlyph name="streak" :size="18" /></span>
              <div>
                <strong>No reward history yet</strong>
                <small>Complete a lesson, quiz, or daily streak to log your first XP entry.</small>
              </div>
            </div>
          </article>

          <article class="panel student-rewards-achievements-card">
            <div class="row-between">
              <div class="student-section-heading">
                <span><IconGlyph name="star" :size="20" /></span>
                <strong>Recent Achievements</strong>
              </div>
              <button type="button" class="student-link-button" @click="openAchievements">
                Open
              </button>
            </div>

            <div class="student-achievement-list">
              <article v-for="item in achievementFeed" :key="item.id" class="student-achievement-item">
                <span class="student-achievement-icon" :class="`is-${item.tone}`"
                  ><IconGlyph :name="item.icon" :size="18"
                /></span>
                <div class="student-achievement-copy">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.subtitle }}</span>
                  <small>{{ item.date }}</small>
                </div>
                <strong class="student-achievement-reward">{{ item.reward }}</strong>
              </article>
            </div>
          </article>
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useResource } from '@/composables/useResource'
import {
  activityLibrary,
  studentDashboard as studentDashboardDemo,
  studentPointsHistory,
  studentProgress as studentProgressDemo,
  studentRewards,
} from '@/demo/data'
import { clampPercent, formatDate, formatNumber, nonNegativeInteger, ratioPercent, toFiniteNumber } from '@/utils/format'
import { inferPracticeActivityFromText, normalizePracticeActivity, practiceRoute } from '@/utils/practiceRoute'

type BadgeIcon = 'star' | 'learn' | 'live' | 'trophy'
type JourneyStatus = 'complete' | 'current' | 'future'

const router = useRouter()
const fallbackAchievementDateIso = new Date().toISOString()

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

const levelTitles: Record<number, string> = {
  1: 'Little Starter',
  2: 'Pinyin Explorer',
  3: 'Little Learner',
  4: 'Word Builder',
  5: 'Sentence Maker',
}

function levelTitleFor(levelNumber: number): string {
  return levelTitles[levelNumber] ?? `Level ${levelNumber}`
}

const currentLevelNumber = computed(() => Math.max(1, nonNegativeInteger(rewards.value.currentLevelNumber, 1)))
const nextLevelNumber = computed(() => currentLevelNumber.value + 1)
const levelProgressPercent = computed(() => clampPercent(rewards.value.progressToNextLevelPercent))
const currentStreakCount = computed(() => nonNegativeInteger(rewards.value.streak.currentStreak))
const quizAveragePercent = computed(() => clampPercent(progress.value.quizAverage))
const currentLevelTitle = computed(() => levelTitleFor(currentLevelNumber.value))
const quizzesAbove80 = computed(
  () => dashboard.value.recentQuizResults.filter((item) => ratioPercent(item.score, item.maxScore) >= 80).length,
)
const completedLessonsForGoal = computed(() => Math.min(8, nonNegativeInteger(progress.value.completedLessons)))
const completedActivityCount = computed(() => activities.value.items.filter((item) => item.completed).length)
const completedActivitiesForGoal = computed(() => Math.min(5, completedActivityCount.value))
const quizGoalCount = computed(() => Math.min(3, quizzesAbove80.value))

const heroStats = computed(() => [
  { label: 'Total Points', value: formatNumber(rewards.value.totalPoints), icon: 'star' },
  { label: 'Current Streak', value: `${currentStreakCount.value} days`, icon: 'streak' },
  { label: 'Achievements', value: String(nonNegativeInteger(dashboard.value.badgeCount)), icon: 'rewards' },
])

const earnedBadgeCards = computed(() => {
  const base = rewards.value.badges.map((badge, index) => ({
    id: `earned-${badge.badgeId}`,
    title: badge.name,
    subtitle: badge.description,
    statusLabel: earnedBadgeStatusLabel(index),
    icon: badgeIconFor(badge.iconKey),
    tone: earnedBadgeTone(index),
    locked: false,
  }))

  const derived = [
    {
      id: 'earned-streak',
      title: '7-Day Streak',
      subtitle: 'Learnt for 7 days in a row',
      statusLabel: 'Mastered',
      icon: 'streak',
      tone: 'purple',
      locked: false,
    },
    {
      id: 'earned-quiz',
      title: 'Quiz Starter',
      subtitle: 'Scored above 80% in a quiz',
      statusLabel: 'In progress',
      icon: 'quiz',
      tone: 'orange',
      locked: false,
    },
  ]

  const filteredDerived = derived.filter((item) => {
    if (item.id === 'earned-streak') return currentStreakCount.value >= 7
    if (item.id === 'earned-quiz') return quizzesAbove80.value > 0
    return true
  })

  return [...base, ...filteredDerived].slice(0, 5)
})

const lockedBadgeCards = [
  {
    id: 'lock-1',
    title: 'Sentence Builder',
    subtitle: 'Unlock with more rearrange practice',
    statusLabel: 'Locked',
    icon: 'clip',
  },
  {
    id: 'lock-2',
    title: 'Story Starter',
    subtitle: 'Unlock after story activity streaks',
    statusLabel: 'Locked',
    icon: 'learn',
  },
  {
    id: 'lock-3',
    title: 'Character Master',
    subtitle: 'Unlock after tracing drills',
    statusLabel: 'Locked',
    icon: 'language',
  },
  {
    id: 'lock-4',
    title: 'Quiz Whiz',
    subtitle: 'Unlock with stronger quiz consistency',
    statusLabel: 'Locked',
    icon: 'quiz',
  },
  {
    id: 'lock-5',
    title: 'Culture Explorer',
    subtitle: 'Unlock from culture lesson progress',
    statusLabel: 'Locked',
    icon: 'globe',
  },
]

const achievementFeed = computed(() => [
  {
    id: 'a-1',
    icon: 'star',
    title: '7-Day Streak!',
    subtitle: `You have learned for ${currentStreakCount.value} days in a row.`,
    reward: `${currentStreakCount.value} day streak`,
    date: formatDate(pointsHistory.value[0]?.createdAt ?? fallbackAchievementDateIso),
    tone: 'purple',
  },
  {
    id: 'a-2',
    icon: 'quiz',
    title: 'Quiz Progress',
    subtitle: `Average quiz score is now ${quizAveragePercent.value}%.`,
    reward: `${quizAveragePercent.value}% average`,
    date: formatDate(dashboard.value.recentQuizResults[0]?.completedAt ?? fallbackAchievementDateIso),
    tone: 'green',
  },
  {
    id: 'a-3',
    icon: 'live',
    title: 'Practice Explorer',
    subtitle: `${activities.value.items.length} practice activities are available in your library.`,
    reward: `${activities.value.items.length} activities`,
    date: formatDate(pointsHistory.value[1]?.createdAt ?? fallbackAchievementDateIso),
    tone: 'orange',
  },
])

const weeklyGoals = computed(() => [
  {
    title: 'Complete 8 lessons',
    count: `${completedLessonsForGoal.value} / 8`,
    progress: ratioPercent(completedLessonsForGoal.value, 8),
    statusLabel: 'Tracking lessons',
    icon: 'learn',
    tone: 'green',
  },
  {
    title: 'Finish 5 practice activities',
    count: `${completedActivitiesForGoal.value} / 5`,
    progress: ratioPercent(completedActivitiesForGoal.value, 5),
    statusLabel: 'Tracking practice',
    icon: 'live',
    tone: 'purple',
  },
  {
    title: 'Score 80%+ in 3 quizzes',
    count: `${quizGoalCount.value} / 3`,
    progress: ratioPercent(quizGoalCount.value, 3),
    statusLabel: 'Tracking quizzes',
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

function earnedBadgeStatusLabel(index: number): string {
  return index < 2 ? 'Mastered' : 'In progress'
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
  if (status === 'complete') return 'Completed'
  if (status === 'current') return `${levelProgressPercent.value}% to next level`
  return level === nextLevelNumber.value ? 'Next up' : 'Locked until more progress'
}

function journeyIconFor(status: JourneyStatus): string {
  if (status === 'complete') return 'star'
  if (status === 'current') return 'trophy'
  return 'safe'
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
.student-rewards-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.46fr) 378px;
  gap: 18px;
}

.student-rewards-main,
.student-rewards-side {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.student-rewards-hero {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-columns: 0.86fr 1.18fr 0.66fr;
  gap: 18px;
  padding: 22px;
  border-radius: 30px;
  background:
    radial-gradient(circle at 20% 14%, rgba(255, 255, 255, 0.18), transparent 18%),
    linear-gradient(135deg, #9f7cff 0%, #8b69ff 42%, #caa9ff 100%);
  box-shadow: 0 26px 54px rgba(135, 108, 255, 0.16);
}

.student-rewards-confetti {
  position: absolute;
  width: 10px;
  height: 10px;
  border-radius: 4px;
  background: rgba(255, 210, 84, 0.88);
}

.student-rewards-confetti.is-1 {
  top: 22px;
  right: 248px;
}

.student-rewards-confetti.is-2 {
  top: 48px;
  right: 170px;
  background: rgba(255, 120, 138, 0.82);
}

.student-rewards-confetti.is-3 {
  top: 82px;
  right: 206px;
  background: rgba(255, 173, 63, 0.82);
}

.student-rewards-hero-pagoda {
  position: absolute;
  right: 34px;
  bottom: 18px;
  width: 116px;
  height: 98px;
  opacity: 0.22;
  background: linear-gradient(
    180deg,
    #ffffff 0 12%,
    transparent 12% 18%,
    #fff1cf 18% 30%,
    transparent 30% 36%,
    #ffe6b0 36% 48%,
    transparent 48% 56%,
    #ffd799 56% 100%
  );
  clip-path: polygon(
    50% 0,
    76% 14%,
    100% 34%,
    80% 36%,
    80% 44%,
    100% 52%,
    78% 54%,
    78% 62%,
    96% 70%,
    74% 72%,
    74% 100%,
    26% 100%,
    26% 72%,
    4% 70%,
    22% 62%,
    22% 54%,
    0 52%,
    20% 44%,
    20% 36%,
    0 34%,
    24% 14%
  );
}

.student-rewards-level,
.student-rewards-next-card {
  position: relative;
  z-index: 1;
}

.student-rewards-level {
  display: grid;
  align-content: start;
  gap: 12px;
  color: white;
}

.student-rewards-level-badge {
  width: 98px;
  height: 98px;
  border-radius: 28px;
  display: grid;
  place-items: center;
  font-size: 3rem;
  background: linear-gradient(180deg, rgba(255, 218, 84, 0.96), rgba(245, 161, 28, 0.96));
  box-shadow: 0 18px 28px rgba(100, 65, 180, 0.22);
}

.student-rewards-level-copy small,
.student-rewards-top-stat small {
  display: block;
  color: rgba(255, 255, 255, 0.82);
  font-size: 0.82rem;
}

.student-rewards-level-copy strong {
  display: block;
  margin-top: 10px;
  font-family: 'Fredoka', sans-serif;
  font-size: 2.2rem;
}

.student-rewards-level-copy span {
  display: block;
  margin-top: 4px;
  font-size: 1.2rem;
  font-weight: 600;
}

.student-rewards-level-progress {
  display: grid;
  gap: 10px;
  margin-top: 8px;
}

.student-rewards-level-progress strong {
  font-family: 'Fredoka', sans-serif;
  font-size: 1.4rem;
}

.student-rewards-hero-center {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 16px;
  align-content: start;
}

.student-rewards-top-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  padding: 12px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 28px rgba(87, 66, 189, 0.14);
}

.student-rewards-top-stat {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  padding: 8px 12px;
  color: var(--text);
}

.student-rewards-top-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 1.35rem;
  background: linear-gradient(135deg, rgba(255, 230, 141, 0.86), rgba(255, 255, 255, 0.98));
}

.student-rewards-top-stat strong {
  display: block;
  margin-top: 4px;
  font-family: 'Fredoka', sans-serif;
  font-size: 1.7rem;
}

.student-rewards-top-stat small {
  color: var(--text-soft);
}

.student-rewards-next-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  padding: 16px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18px 28px rgba(87, 66, 189, 0.14);
}

.student-rewards-next-icon {
  width: 68px;
  height: 68px;
  border-radius: 24px;
  display: grid;
  place-items: center;
  font-size: 2rem;
  background: linear-gradient(135deg, rgba(255, 216, 118, 0.92), rgba(245, 161, 28, 0.92));
}

.student-rewards-next-copy p {
  margin: 4px 0 0;
  color: var(--text-soft);
  line-height: 1.5;
}

.student-rewards-next-progress {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.student-rewards-next-progress span {
  color: var(--primary);
  font-weight: 700;
}

.student-rewards-hero-mascot {
  position: relative;
  z-index: 1;
  align-self: end;
  justify-self: center;
}

.student-badge-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
  margin-top: 14px;
}

.student-badge-grid--locked {
  margin-top: 10px;
}

.student-badge-card {
  display: grid;
  justify-items: center;
  gap: 8px;
  text-align: center;
}

.student-badge-card strong {
  font-size: 0.95rem;
  line-height: 1.3;
}

.student-badge-card span {
  color: var(--text-soft);
  font-size: 0.82rem;
  line-height: 1.35;
}

.student-badge-emblem {
  width: 92px;
  height: 92px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  position: relative;
  font-family: 'Fredoka', sans-serif;
  font-size: 2.4rem;
  color: white;
}

.student-badge-emblem small {
  position: absolute;
  right: 4px;
  top: 2px;
  font-size: 1rem;
}

.student-badge-emblem.is-gold {
  background: linear-gradient(180deg, #93d34b, #54b438);
}

.student-badge-emblem.is-green {
  background: linear-gradient(180deg, #80cf49, #53af38);
}

.student-badge-emblem.is-blue {
  background: linear-gradient(180deg, #73b4ff, #2e88f5);
}

.student-badge-emblem.is-purple {
  background: linear-gradient(180deg, #8f69ff, #6946e8);
}

.student-badge-emblem.is-orange {
  background: linear-gradient(180deg, #ffbe57, #ff8f2f);
}

.student-badge-emblem.is-gray {
  color: #9aa4be;
  background: linear-gradient(180deg, #efeff2, #d8d9df);
}

.student-badge-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 2px;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 800;
  line-height: 1;
}

.student-badge-status.is-earned {
  color: #2f9f45;
  background: rgba(95, 202, 108, 0.16);
}

.student-badge-status.is-locked {
  color: #7a849e;
  background: rgba(204, 210, 226, 0.32);
}

.student-badges-divider {
  height: 1px;
  margin: 16px 0 10px;
  background: rgba(232, 236, 247, 0.96);
}

.student-badges-note {
  margin: 14px 0 0;
  color: var(--text-soft);
  text-align: center;
  font-size: 0.92rem;
}

.student-achievement-list,
.student-reward-history-list {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.student-achievement-item,
.student-reward-history-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.student-achievement-icon,
.student-reward-history-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  font-size: 1.2rem;
}

.student-achievement-icon.is-purple {
  background: rgba(133, 97, 255, 0.12);
}

.student-achievement-icon.is-green {
  background: rgba(97, 201, 103, 0.12);
}

.student-achievement-icon.is-orange {
  background: rgba(255, 177, 55, 0.14);
}

.student-achievement-copy,
.student-reward-history-copy {
  display: grid;
  gap: 4px;
}

.student-achievement-copy span,
.student-reward-history-copy span {
  color: var(--text-soft);
  font-size: 0.84rem;
  line-height: 1.4;
}

.student-rewards-journey-card p {
  margin: 4px 0 0;
  color: var(--text-soft);
}

.student-journey-track {
  position: relative;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
  margin-top: 20px;
}

.student-journey-line {
  position: absolute;
  left: 8%;
  right: 8%;
  top: 36px;
  height: 4px;
  border-top: 3px dashed rgba(205, 210, 230, 0.96);
}

.student-journey-step {
  position: relative;
  z-index: 1;
  display: grid;
  justify-items: center;
  gap: 6px;
  text-align: center;
}

.student-journey-node {
  width: 66px;
  height: 66px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 1.8rem;
  background: #eff0f5;
  box-shadow: inset 0 0 0 1px rgba(225, 229, 241, 0.96);
}

.student-journey-step.is-complete .student-journey-node {
  background: linear-gradient(180deg, #8fd94a, #55b73c);
  color: white;
}

.student-journey-step.is-current .student-journey-node {
  background: linear-gradient(180deg, #ffd455, #f5a523);
  color: white;
}

.student-journey-step.is-future .student-journey-node {
  color: #9aa4be;
}

.student-journey-step span,
.student-journey-step small {
  color: var(--text-soft);
}

.student-journey-step.is-complete small {
  color: #4eaf41;
  font-weight: 700;
}

.student-journey-step.is-current small {
  color: var(--primary);
  font-weight: 700;
}

.student-weekly-reset {
  color: var(--text-soft);
  font-size: 0.84rem;
  font-weight: 700;
}

.student-weekly-goal-list {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.student-weekly-goal-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.student-weekly-goal-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  font-size: 1.2rem;
}

.student-weekly-goal-icon.is-green {
  background: rgba(89, 190, 78, 0.12);
}

.student-weekly-goal-icon.is-purple {
  background: rgba(133, 97, 255, 0.12);
}

.student-weekly-goal-icon.is-orange {
  background: rgba(255, 177, 55, 0.14);
}

.student-weekly-goal-copy {
  display: grid;
  gap: 8px;
}

.student-weekly-goal-side {
  display: grid;
  justify-items: end;
  gap: 4px;
}

.student-weekly-goal-side span {
  color: var(--text-soft);
  font-size: 0.86rem;
}

.student-weekly-goal-side strong {
  color: #f0a81b;
}

.student-weekly-bonus {
  margin-top: 16px;
  width: 100%;
  min-height: 54px;
  border-radius: 18px;
  border: 1px solid rgba(255, 214, 104, 0.96);
  background: linear-gradient(180deg, rgba(255, 247, 206, 0.96), rgba(255, 242, 183, 0.96));
  color: #7a5700;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.student-rewards-promo-card {
  position: relative;
  overflow: hidden;
  padding: 20px;
  border-radius: 28px;
  background:
    radial-gradient(circle at 84% 20%, rgba(255, 220, 137, 0.36), transparent 20%),
    linear-gradient(135deg, rgba(255, 245, 224, 0.98), rgba(255, 251, 241, 0.98));
  border: 1px solid rgba(255, 234, 176, 0.92);
  box-shadow: 0 18px 36px rgba(166, 152, 115, 0.12);
}

.student-rewards-promo-copy {
  display: grid;
  gap: 10px;
  max-width: 62%;
}

.student-rewards-promo-copy p {
  margin: 0;
  color: #6a6879;
  line-height: 1.55;
}

.student-rewards-promo-chest {
  position: absolute;
  right: 18px;
  bottom: 10px;
  font-size: 4.8rem;
}

.student-reward-history-points {
  color: #52b748;
}

@media (max-width: 1420px) {
  .student-rewards-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1180px) {
  .student-rewards-hero {
    grid-template-columns: 1fr;
  }

  .student-badge-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .student-journey-track {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .student-journey-line {
    display: none;
  }

  .student-rewards-promo-copy {
    max-width: 100%;
    padding-right: 100px;
  }
}

@media (max-width: 760px) {
  .student-badge-grid,
  .student-rewards-top-stats {
    grid-template-columns: 1fr 1fr;
  }

  .student-weekly-goal-item,
  .student-achievement-item,
  .student-reward-history-item {
    grid-template-columns: 1fr;
    justify-items: start;
  }

  .student-weekly-goal-side {
    justify-items: start;
  }
}

@media (min-width: 900px) {
  .student-rewards-layout {
    grid-template-columns: minmax(0, 1fr) minmax(280px, 0.9fr);
    gap: 18px;
  }

  .student-rewards-main,
  .student-rewards-side {
    gap: 18px;
  }

  .student-rewards-hero {
    grid-template-columns: 0.74fr minmax(0, 1fr) 156px;
    gap: 16px;
    min-height: 210px;
    padding: 22px;
    border-radius: 26px;
  }

  .student-rewards-level-badge {
    width: 84px;
    height: 84px;
    border-radius: 24px;
    font-size: 2.5rem;
  }

  .student-rewards-level-copy strong {
    margin-top: 8px;
    font-size: 1.9rem;
  }

  .student-rewards-level-copy span,
  .student-rewards-level-progress strong {
    font-size: 1rem;
  }

  .student-rewards-level-progress {
    gap: 8px;
    margin-top: 6px;
  }

  .student-rewards-top-stats {
    gap: 10px;
  }

  .student-rewards-top-stat {
    gap: 10px;
    padding: 10px 12px;
  }

  .student-rewards-top-icon {
    width: 40px;
    height: 40px;
    font-size: 1.2rem;
  }

  .student-rewards-top-stat strong {
    font-size: 1.34rem;
  }

  .student-rewards-top-stat small {
    font-size: 0.8rem;
  }

  .student-rewards-next-card {
    grid-template-columns: 58px minmax(0, 1fr);
    gap: 12px;
    padding: 14px;
    border-radius: 18px;
  }

  .student-rewards-next-icon {
    width: 54px;
    height: 54px;
    border-radius: 18px;
    font-size: 1.6rem;
  }

  .student-rewards-next-copy p,
  .student-rewards-next-progress span {
    font-size: 0.86rem;
  }

  .student-rewards-hero-mascot {
    transform: scale(0.9);
    transform-origin: bottom center;
  }

  .student-rewards-badges-card,
  .student-rewards-achievements-card,
  .student-rewards-journey-card,
  .student-weekly-goals-card,
  .student-reward-history-card {
    padding: 18px;
    border-radius: 24px;
  }

  .student-badge-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 12px;
    margin-top: 12px;
  }

  .student-badge-emblem {
    width: 76px;
    height: 76px;
    font-size: 2rem;
  }

  .student-badge-card {
    gap: 8px;
  }

  .student-badge-card strong {
    font-size: 0.88rem;
  }

  .student-badge-card span {
    font-size: 0.76rem;
    line-height: 1.3;
  }

  .student-achievement-item,
  .student-reward-history-item,
  .student-weekly-goal-item {
    gap: 10px;
    font-size: 0.86rem;
  }

  .student-achievement-icon,
  .student-reward-history-icon,
  .student-weekly-goal-icon {
    width: 38px;
    height: 38px;
    border-radius: 13px;
    font-size: 1.08rem;
  }

  .student-achievement-copy span,
  .student-reward-history-copy span,
  .student-weekly-reset,
  .student-weekly-goal-side span {
    font-size: 0.8rem;
  }

  .student-journey-track {
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 12px;
  }

  .student-journey-line {
    display: block;
    top: 30px;
  }

  .student-journey-node {
    width: 58px;
    height: 58px;
    font-size: 1.55rem;
  }

  .student-journey-step strong,
  .student-journey-step span,
  .student-journey-step small {
    font-size: 0.8rem;
  }

  .student-weekly-goal-list,
  .student-achievement-list,
  .student-reward-history-list {
    gap: 12px;
    margin-top: 12px;
  }

  .student-weekly-bonus {
    min-height: 48px;
    border-radius: 16px;
    font-size: 0.86rem;
  }

  .student-rewards-promo-card {
    padding: 18px;
    border-radius: 22px;
  }

  .student-rewards-promo-copy {
    max-width: 72%;
    gap: 8px;
  }

  .student-rewards-promo-copy p {
    font-size: 0.86rem;
    line-height: 1.45;
  }

  .student-rewards-promo-chest {
    font-size: 4rem;
  }
}

.student-rewards-page .student-simple-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 16px;
  padding: 4px 4px 0;
}

.student-rewards-page .student-simple-header h1 {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  color: #1f2647;
  font-family: 'Fredoka', sans-serif;
  font-size: clamp(2rem, 3.4vw, 2.85rem);
  line-height: 1.04;
  letter-spacing: -0.02em;
}

.student-rewards-page .student-simple-header h1 > span {
  display: inline-grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 16px;
  color: #cf7c0e;
  background: linear-gradient(135deg, rgba(255, 226, 145, 0.92), rgba(255, 197, 96, 0.92));
  box-shadow:
    0 12px 22px rgba(255, 175, 71, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.student-rewards-page .student-simple-header p {
  grid-column: 1 / -1;
  max-width: 720px;
  margin: 8px 0 0;
  color: #5a6485;
  font-size: 0.96rem;
  line-height: 1.5;
}

.student-rewards-top-stat {
  min-width: 0;
}

.student-rewards-top-stat > div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.student-rewards-top-stat small {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 0.74rem;
  letter-spacing: 0.01em;
  text-transform: uppercase;
}

.student-rewards-top-stat strong {
  margin-top: 0;
  font-size: 1.55rem;
  line-height: 1.04;
  letter-spacing: -0.01em;
}

.student-rewards-level-copy {
  min-width: 0;
}

.student-rewards-level-copy span {
  display: block;
  min-width: 0;
  font-size: 1.05rem;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.student-rewards-badges-card {
  padding: 20px;
  border-radius: 26px;
  background:
    radial-gradient(circle at 100% 0%, rgba(255, 219, 137, 0.18), transparent 24%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(250, 252, 255, 0.96));
  box-shadow:
    0 18px 38px rgba(112, 121, 176, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.92);
}

.student-badges-meta {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(204, 210, 226, 0.32);
  color: #7a849e;
  font-size: 0.72rem;
  font-weight: 800;
  line-height: 1;
}

.student-badge-grid {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  margin-top: 14px;
}

.student-badge-card {
  position: relative;
  padding: 14px 10px 12px;
  border: 1px solid rgba(231, 235, 248, 0.92);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 10px 22px rgba(124, 132, 184, 0.06);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.student-badge-card:hover {
  transform: translateY(-2px);
  border-color: rgba(126, 96, 255, 0.28);
  box-shadow: 0 18px 32px rgba(124, 132, 184, 0.14);
}

.student-badge-card.is-locked {
  background: rgba(248, 249, 252, 0.96);
  box-shadow: 0 6px 14px rgba(124, 132, 184, 0.05);
}

.student-badge-card.is-locked:hover {
  transform: none;
  border-color: rgba(231, 235, 248, 0.92);
  box-shadow: 0 10px 20px rgba(124, 132, 184, 0.08);
}

.student-badge-card strong {
  margin-top: 2px;
  color: #232b4a;
  font-size: 0.92rem;
  line-height: 1.18;
}

.student-badge-card span {
  flex: 1 1 auto;
  min-height: 32px;
  color: #66718d;
  font-size: 0.74rem;
  line-height: 1.3;
}

.student-badge-card.is-locked strong {
  color: #4f5a7a;
}

.student-badge-emblem {
  width: 72px;
  height: 72px;
  border-radius: 22px;
  box-shadow:
    inset 0 -3px 0 rgba(0, 0, 0, 0.08),
    0 14px 22px rgba(120, 96, 220, 0.16);
}

.student-badge-emblem.is-gold {
  background: linear-gradient(180deg, #ffd75a, #f3a020);
  color: white;
}

.student-badge-emblem.is-green {
  background: linear-gradient(180deg, #8ad55a, #4ea93d);
}

.student-badge-emblem.is-gray {
  background: linear-gradient(180deg, #f3f4f8, #dee0ea);
  color: #8a93ad;
  box-shadow:
    inset 0 -2px 0 rgba(0, 0, 0, 0.04),
    0 8px 14px rgba(150, 156, 180, 0.12);
}

.student-badge-emblem small {
  right: -4px;
  top: -4px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: white;
  color: #7a849e;
  display: grid;
  place-items: center;
  box-shadow: 0 4px 10px rgba(120, 130, 160, 0.18);
}

.student-badge-status {
  margin-top: 4px;
}

.student-badges-empty {
  margin-top: 14px;
  padding: 16px;
  border: 1px dashed rgba(126, 96, 255, 0.28);
  border-radius: 22px;
  background: radial-gradient(circle at 96% 4%, rgba(255, 226, 144, 0.18), transparent 32%), rgba(247, 245, 255, 0.7);
}

.student-badges-empty-head {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.student-badges-empty-head > span {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 16px;
  color: #d9881a;
  background: linear-gradient(135deg, rgba(255, 224, 141, 0.96), rgba(255, 196, 105, 0.96));
  box-shadow: 0 10px 20px rgba(245, 175, 71, 0.18);
}

.student-badges-empty-head strong {
  display: block;
  color: #232b4a;
  font-size: 1rem;
  line-height: 1.2;
}

.student-badges-empty-head small {
  display: block;
  margin-top: 4px;
  color: #66718d;
  font-size: 0.82rem;
  line-height: 1.4;
}

.student-badges-empty-list {
  display: grid;
  gap: 8px;
  margin: 14px 0 12px;
  padding: 0;
  list-style: none;
}

.student-badges-empty-list li {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(231, 235, 248, 0.96);
  color: #2f3a5c;
  font-size: 0.84rem;
  line-height: 1.35;
}

.student-badges-empty-list li > :first-child {
  color: #6d4cff;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: rgba(111, 76, 255, 0.1);
  display: grid;
  place-items: center;
}

.student-badges-empty-cta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 16px;
  border: 0;
  border-radius: 14px;
  background: linear-gradient(135deg, #7657ff, #9d7bff);
  color: white;
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 12px 22px rgba(122, 96, 230, 0.28);
}

.student-badges-empty-cta:hover {
  filter: brightness(1.05);
}

.student-journey-step strong {
  color: #232b4a;
  font-family: 'Fredoka', sans-serif;
  font-size: 0.95rem;
  letter-spacing: -0.01em;
}

.student-journey-step span {
  font-size: 0.82rem;
  font-weight: 700;
}

.student-journey-step small {
  font-size: 0.72rem;
  line-height: 1.3;
}

.student-reward-history-empty {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  margin-top: 12px;
  padding: 14px;
  border: 1px dashed rgba(228, 232, 244, 0.96);
  border-radius: 16px;
  background: rgba(250, 251, 255, 0.92);
}

.student-reward-history-empty > span {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  color: #d9881a;
  background: rgba(255, 196, 105, 0.22);
}

.student-reward-history-empty strong {
  display: block;
  color: #232b4a;
  font-size: 0.92rem;
  line-height: 1.2;
}

.student-reward-history-empty small {
  display: block;
  margin-top: 4px;
  color: #66718d;
  font-size: 0.78rem;
  line-height: 1.4;
}

.student-rewards-achievements-card {
  padding: 18px;
  border-radius: 22px;
  background:
    radial-gradient(circle at 100% 0%, rgba(255, 197, 122, 0.18), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(252, 252, 255, 0.96));
  box-shadow:
    0 18px 32px rgba(120, 130, 180, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.92);
}

.student-rewards-achievements-card .student-achievement-list {
  gap: 10px;
  margin-top: 12px;
}

.student-rewards-achievements-card .student-achievement-item {
  grid-template-columns: 38px minmax(0, 1fr) auto;
  gap: 10px;
  padding: 10px;
  border: 1px solid rgba(231, 235, 248, 0.94);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.92);
}

.student-rewards-achievements-card .student-achievement-icon {
  width: 38px;
  height: 38px;
  border-radius: 13px;
  font-size: 1rem;
}

.student-rewards-achievements-card .student-achievement-copy strong {
  color: #232b4a;
  font-size: 0.86rem;
  line-height: 1.18;
}

.student-rewards-achievements-card .student-achievement-copy span {
  display: block;
  color: #66718d;
  font-size: 0.74rem;
  line-height: 1.35;
}

.student-rewards-achievements-card .student-achievement-copy small {
  display: block;
  margin-top: 2px;
  color: #94a0b9;
  font-size: 0.68rem;
  letter-spacing: 0.01em;
}

.student-achievement-reward {
  padding: 5px 10px;
  border-radius: 999px;
  color: #6d4cff;
  background: rgba(111, 76, 255, 0.1);
  font-family: 'Fredoka', sans-serif;
  font-size: 0.76rem;
  white-space: nowrap;
  align-self: start;
}

.student-badge-grid--locked .student-badge-card {
  background: rgba(248, 249, 252, 0.7);
}

@media (max-width: 1280px) {
  .student-badge-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 1180px) {
  .student-rewards-page .student-simple-header {
    grid-template-columns: 1fr;
    align-items: start;
  }

  .student-badge-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .student-badge-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .student-rewards-achievements-card .student-achievement-item {
    grid-template-columns: 36px minmax(0, 1fr);
  }

  .student-achievement-reward {
    grid-column: 1 / -1;
    justify-self: start;
  }
}
</style>
