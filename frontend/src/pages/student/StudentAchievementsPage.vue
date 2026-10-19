<template>
  <div class="page-stack student-achievements-page">
    <PageHeaderBlock
      :title="t('studentAchievements.pageTitle')"
      :description="t('studentAchievements.pageDescription')"
      icon="trophy"
    >
      <template #actions>
        <button class="btn btn-secondary" type="button" @click="openRewards">
          <IconGlyph name="rewards" :size="18" />
          {{ t('studentAchievements.openRewards') }}
        </button>
      </template>
    </PageHeaderBlock>

    <DataNotice :using-fallback="usingFallback" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <div class="student-achievements-layout">
        <div class="student-achievements-main">
          <article class="hero-card student-achievements-hero">
            <span class="student-achievements-hero-mascot" aria-hidden="true">
              <PandaIllustration :size="128" outfit="#5a35ec" accent="#ffcc2f" pose="wave" />
            </span>
            <div class="student-achievements-level">
              <span>{{ t('studentAchievements.yourLevel') }}</span>
              <strong>{{ t('studentAchievements.level', { level: currentLevelNumber }) }}</strong>
              <span>{{ levelTitle }}</span>
              <ProgressBar :progress="levelProgressPercent" :label="t('studentAchievements.yourLevel')" />
              <small>{{
                t('studentAchievements.nextLevel', {
                  points: formatNumber(rewards.totalPoints),
                  progress: levelProgressPercent,
                })
              }}</small>
            </div>
            <ul class="student-achievements-stats">
              <li v-for="stat in achievementStats" :key="stat.label" class="student-achievements-stat">
                <span class="student-achievements-stat-icon" aria-hidden="true">
                  <IconGlyph :name="stat.icon" :size="18" />
                </span>
                <strong>{{ stat.value }}</strong>
                <span>{{ stat.label }}</span>
              </li>
            </ul>
          </article>

          <div class="student-achievements-grid">
            <section class="panel student-achievements-card" :aria-labelledby="`${baseId}-chart`">
              <div class="card-header">
                <h2 :id="`${baseId}-chart`" class="card-title student-achievements-card-title">
                  <span class="student-achievements-title-icon" aria-hidden="true">
                    <IconGlyph name="progress" :size="18" />
                  </span>
                  {{ t('studentAchievements.learningProgress') }}
                </h2>
                <span class="tag">{{ t('studentAchievements.recentLessons') }}</span>
              </div>
              <ol
                v-if="moduleProgress.length"
                class="student-achievements-chart"
                :aria-label="t('studentAchievements.chartLabel')"
              >
                <li v-for="module in moduleProgress" :key="module.id" class="student-achievements-chart-bar">
                  <span class="student-achievements-chart-value">{{ module.progress }}%</span>
                  <span class="student-achievements-chart-track" aria-hidden="true">
                    <span :class="`is-${module.tone}`" :style="{ height: `${module.progress}%` }" />
                  </span>
                  <span class="student-achievements-chart-label" :title="module.title">
                    <span class="sr-only">{{ module.title }}</span>
                    <span aria-hidden="true">{{ module.id }}</span>
                  </span>
                </li>
              </ol>
              <p class="student-achievements-note">
                <IconGlyph name="star" :size="16" />
                <span>{{ progressTrendSummary }}</span>
              </p>
            </section>

            <section class="panel student-achievements-card" :aria-labelledby="`${baseId}-badges`">
              <div class="card-header">
                <h2 :id="`${baseId}-badges`" class="card-title student-achievements-card-title">
                  <span class="student-achievements-title-icon" aria-hidden="true">
                    <IconGlyph name="trophy" :size="18" />
                  </span>
                  {{ t('studentAchievements.achievements') }}
                </h2>
                <button class="btn btn-link" type="button" @click="openRewards">
                  {{ t('studentAchievements.openRewards') }}
                </button>
              </div>
              <ul v-if="badgeCards.length" class="student-achievements-badge-grid">
                <li v-for="badge in badgeCards" :key="badge.id" class="student-achievements-badge">
                  <span class="student-achievements-badge-icon" :class="`is-${badge.tone}`" aria-hidden="true">
                    <IconGlyph :name="badge.icon" :size="22" />
                  </span>
                  <strong>{{ badge.title }}</strong>
                  <span>{{ badge.subtitle }}</span>
                </li>
              </ul>
              <StatePanel
                v-else
                icon="rewards"
                :title="t('studentAchievements.noBadges')"
                :message="t('studentAchievements.noBadgesHint')"
                compact
              />
            </section>

            <section class="panel student-achievements-card" :aria-labelledby="`${baseId}-milestones`">
              <div class="card-header">
                <h2 :id="`${baseId}-milestones`" class="card-title student-achievements-card-title">
                  <span class="student-achievements-title-icon" aria-hidden="true">
                    <IconGlyph name="rewards" :size="18" />
                  </span>
                  {{ t('studentAchievements.milestones') }}
                </h2>
              </div>
              <ul v-if="milestoneCards.length" class="student-achievements-list">
                <li v-for="item in milestoneCards" :key="item.title" class="student-achievements-row">
                  <span class="student-achievements-row-icon" :class="`is-${item.tone}`" aria-hidden="true">
                    <IconGlyph :name="item.icon" :size="18" />
                  </span>
                  <div class="student-achievements-row-copy">
                    <strong>{{ item.title }}</strong>
                    <span>{{ item.subtitle }}</span>
                  </div>
                  <strong class="student-achievements-points">{{ item.reward }}</strong>
                </li>
              </ul>
              <StatePanel
                v-else
                icon="rewards"
                :title="t('studentAchievements.noMilestones')"
                :message="t('studentAchievements.noMilestonesHint')"
                compact
              />
            </section>

            <section class="panel student-achievements-card" :aria-labelledby="`${baseId}-lessons`">
              <div class="card-header">
                <h2 :id="`${baseId}-lessons`" class="card-title student-achievements-card-title">
                  <span class="student-achievements-title-icon" aria-hidden="true">
                    <IconGlyph name="learn" :size="18" />
                  </span>
                  {{ t('studentAchievements.lessonProgress') }}
                </h2>
              </div>
              <ul v-if="moduleProgress.length" class="student-achievements-list">
                <li v-for="module in moduleProgress" :key="module.title" class="student-achievements-row">
                  <span class="student-achievements-row-icon is-label" :class="`is-${module.tone}`">{{
                    module.id
                  }}</span>
                  <div class="student-achievements-row-copy">
                    <strong>{{ module.title }}</strong>
                    <ProgressBar
                      class="is-sm"
                      :progress="module.progress"
                      :tone="module.barTone"
                      :label="module.title"
                    />
                  </div>
                  <strong class="student-achievements-percent">{{ module.progress }}%</strong>
                </li>
              </ul>
              <StatePanel
                v-else
                icon="learn"
                :title="t('studentAchievements.noLessonProgress')"
                :message="t('studentAchievements.noLessonProgressHint')"
                compact
              />
              <div class="card-footer">
                <button class="btn btn-ghost btn-sm" type="button" @click="openLearn">
                  {{ t('studentAchievements.openLearn') }}
                </button>
                <RouterLink
                  class="btn btn-secondary btn-sm"
                  to="/student/progress"
                  data-testid="achievements-progress-link"
                >
                  {{ t('studentAchievements.openProgress') }}
                </RouterLink>
              </div>
            </section>
          </div>
        </div>

        <aside class="student-achievements-side">
          <section class="panel student-achievements-card" :aria-labelledby="`${baseId}-overall`">
            <h2 :id="`${baseId}-overall`" class="card-title">{{ t('studentAchievements.overallProgress') }}</h2>
            <ProgressRing
              :progress="overallProgressPercent"
              :title="t('studentAchievements.courseProgress')"
              :subtitle="t('studentAchievements.doingGreat')"
            />
            <div class="student-achievements-progress-mini">
              <div class="student-achievements-progress-row">
                <span>{{ t('studentAchievements.unitsCompleted') }}</span>
                <strong>{{ completedLessonCount }} / {{ startedLessonCount }}</strong>
              </div>
              <ProgressBar :progress="overallProgressPercent" :label="t('studentAchievements.unitsCompleted')" />
            </div>
            <RouterLink class="btn btn-link student-achievements-progress-link" to="/student/progress">
              {{ t('studentAchievements.openProgress') }}
              <IconGlyph name="chevronRight" :size="16" />
            </RouterLink>
          </section>

          <article class="student-achievements-tip">
            <span class="student-achievements-tip-icon" aria-hidden="true"><IconGlyph name="fun" :size="20" /></span>
            <div>
              <strong>{{ t('studentAchievements.keepLearning') }}</strong>
              <p>{{ t('studentAchievements.steadyTip') }}</p>
            </div>
          </article>
        </aside>
      </div>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRouter } from 'vue-router'

import { studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import ProgressRing from '@/components/common/ProgressRing.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { useStatusLabels } from '@/composables/useStatusLabels'
import {
  studentBadges,
  studentPointsHistory,
  studentProgress as studentProgressDemo,
  studentRewards,
  studentStreak,
} from '@/demo/data'
import { studentAchievementsMessages } from '@/plugins/studentAchievementsMessages'
import { clampPercent, formatDate, formatNumber, nonNegativeInteger } from '@/utils/format'

const { t } = useI18n({
  messages: {
    en: { studentAchievements: studentAchievementsMessages.en },
    ms: { studentAchievements: studentAchievementsMessages.ms },
    'zh-CN': { studentAchievements: studentAchievementsMessages['zh-CN'] },
  },
})
const { statusLabel } = useStatusLabels()

const router = useRouter()
const baseId = useId()
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
const currentLevelNumber = computed(() => Math.max(1, nonNegativeInteger(rewards.value.currentLevelNumber, 1)))
const levelTitle = computed(() => {
  if (currentLevelNumber.value >= 10) return t('studentAchievements.champion')
  if (currentLevelNumber.value >= 5) return t('studentAchievements.confident')
  if (currentLevelNumber.value >= 3) return t('studentAchievements.developingLearner')
  return t('studentAchievements.explorer')
})
const levelProgressPercent = computed(() => clampPercent(rewards.value.progressToNextLevelPercent))
const quizAveragePercent = computed(() => clampPercent(progress.value.quizAverage))
const overallProgressPercent = computed(() => clampPercent(progress.value.overallCompletionPercent))
const completedLessonCount = computed(() => nonNegativeInteger(progress.value.completedLessons))
const startedLessonCount = computed(() => nonNegativeInteger(progress.value.totalLessonsStarted))
const progressTrendSummary = computed(
  () => progress.value.lastActivitySummary || t('studentAchievements.progressFallback'),
)

const achievementStats = computed(() => [
  {
    icon: 'streak',
    value: String(nonNegativeInteger(streak.value.currentStreak)),
    label: t('studentAchievements.dayStreak'),
  },
  { icon: 'star', value: formatNumber(rewards.value.totalPoints), label: t('studentAchievements.xp') },
  { icon: 'rewards', value: `${quizAveragePercent.value}%`, label: t('studentAchievements.quizAverage') },
  {
    icon: 'shield',
    value: String(badgeCount.value),
    label: t('studentAchievements.badges'),
  },
])
const badgeCount = computed(() => (badges.value.length > 0 ? badges.value.length : rewards.value.badges.length))

const badgeCards = computed(() =>
  badges.value.slice(0, 5).map((badge) => ({
    id: badge.badgeId,
    icon: iconForBadge(badge.iconKey),
    title: badge.name,
    subtitle: badge.description,
    tone: toneForBadge(badge.iconKey),
  })),
)

const milestoneCards = computed(() =>
  pointsHistory.value.slice(0, 4).map((item) => ({
    icon: iconForSource(item.sourceType),
    title: item.reason,
    subtitle: `${formatDate(item.createdAt)} - ${formatSource(item.sourceType)}`,
    reward: `${signedIntegerLabel(item.pointsDelta)} XP`,
    tone: toneForSource(item.sourceType),
  })),
)

type ProgressTone = 'primary' | 'success' | 'warning' | 'info'
type SourceIcon = 'quiz' | 'star' | 'status' | 'streak'
type BadgeIcon = 'learn' | 'moderation' | 'quiz' | 'streak' | 'trophy'
type BadgeTone = 'blue' | 'gold' | 'green' | 'orange' | 'pink'
type SourceTone = 'blue' | 'green' | 'orange' | 'purple'
type ModuleTone = 'blue' | 'green' | 'orange'

const moduleProgress = computed<
  Array<{
    id: string
    title: string
    progress: number
    tone: ModuleTone
    barTone: ProgressTone
  }>
>(() =>
  progress.value.recentLessons.slice(0, 5).map((lesson, index) => {
    const completionPercent = clampPercent(lesson.completionPercent)
    const progressTone = toneForProgress(completionPercent)

    return {
      id: lessonIdLabel(lesson.lessonId, index),
      title: lesson.lessonTitle || lessonTitleFallback(index),
      progress: completionPercent,
      tone: progressTone.tone,
      barTone: progressTone.barTone,
    }
  }),
)

function iconForSource(sourceType: string): SourceIcon {
  const normalized = sourceType.toUpperCase()
  if (normalized.includes('QUIZ')) return 'quiz'
  if (normalized.includes('STREAK')) return 'streak'
  if (normalized.includes('LESSON')) return 'status'
  return 'star'
}

function iconForBadge(iconKey?: string | null): BadgeIcon {
  const normalized = (iconKey ?? '').toUpperCase()
  if (normalized.includes('STREAK') || normalized.includes('FIRE')) return 'streak'
  if (normalized.includes('QUIZ')) return 'quiz'
  if (normalized.includes('SPEAK')) return 'moderation'
  if (normalized.includes('LESSON') || normalized.includes('BOOK')) return 'learn'
  return 'trophy'
}

function toneForBadge(iconKey?: string | null): BadgeTone {
  const normalized = (iconKey ?? '').toUpperCase()
  if (normalized.includes('STREAK') || normalized.includes('FIRE')) return 'pink'
  if (normalized.includes('QUIZ')) return 'blue'
  if (normalized.includes('LESSON') || normalized.includes('BOOK')) return 'green'
  if (normalized.includes('LEVEL') || normalized.includes('CROWN')) return 'orange'
  return 'gold'
}

function toneForSource(sourceType: string): SourceTone {
  const normalized = sourceType.toUpperCase()
  if (normalized.includes('QUIZ')) return 'blue'
  if (normalized.includes('STREAK')) return 'orange'
  if (normalized.includes('LESSON')) return 'green'
  return 'purple'
}

function formatSource(sourceType: string): string {
  return statusLabel('pointsSource', sourceType)
}

function signedIntegerLabel(value: number): string {
  const rounded = Math.round(Number.isFinite(value) ? value : 0)
  return rounded > 0 ? `+${rounded}` : String(rounded)
}

function lessonIdLabel(lessonId: number | null | undefined, index: number): string {
  return lessonId ? `L${lessonId}` : String(index + 1)
}

function lessonTitleFallback(index: number): string {
  return t('studentAchievements.lesson', { number: index + 1 })
}

function reloadAll(): void {
  void badgesResource.reload()
  void streakResource.reload()
  void rewardsResource.reload()
  void progressResource.reload()
  void pointsResource.reload()
}

function toneForProgress(progressPercent: number): { tone: ModuleTone; barTone: ProgressTone } {
  if (progressPercent >= 80) return { tone: 'green', barTone: 'success' }
  return progressPercent >= 50 ? { tone: 'orange', barTone: 'warning' } : { tone: 'blue', barTone: 'info' }
}

function openRewards(): void {
  void router.push('/student/rewards')
}

function openLearn(): void {
  void router.push('/student/learn')
}
</script>

<style scoped>
.student-achievements-page {
  display: grid;
  gap: var(--page-gap);
}

.student-achievements-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 320px);
  gap: var(--page-gap);
  align-items: start;
}

.student-achievements-main,
.student-achievements-side {
  display: grid;
  gap: var(--page-gap);
  align-content: start;
  align-items: start;
  min-width: 0;
}

.student-achievements-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 340px), 1fr));
  gap: var(--grid-gap);
}

/* Hero --------------------------------------------------------------------- */
.student-achievements-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) minmax(0, 1.2fr);
  align-items: center;
  gap: var(--space-6);
}

.student-achievements-hero-mascot {
  display: block;
  margin-block: calc(-1 * var(--space-2));
}

.student-achievements-level {
  display: grid;
  gap: var(--space-1-5);
  min-width: 0;
}

.student-achievements-level > span {
  color: var(--text-on-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}

.student-achievements-level > strong {
  color: var(--text-on-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-4xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.student-achievements-level small {
  color: var(--text-on-primary);
  font-size: var(--font-size-sm);
}

.student-achievements-level :deep(.progress-bar) {
  margin-top: var(--space-1-5);
  background: var(--color-white-a24);
}

.student-achievements-level :deep(.progress-bar > span) {
  background: var(--color-accent-500);
}

.student-achievements-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2-5);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-achievements-stat {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  grid-template-rows: auto auto;
  column-gap: var(--space-2-5);
  align-items: center;
  padding: var(--space-2-5) var(--space-3);
  border: 1px solid var(--color-white-a24);
  border-radius: var(--radius-md);
  background: var(--surface-on-brand);
}

.student-achievements-stat-icon {
  display: grid;
  grid-row: span 2;
  place-items: center;
  width: var(--space-8);
  height: var(--space-8);
  border-radius: var(--radius-sm);
  background: var(--color-white);
  color: var(--color-primary-600);
}

.student-achievements-stat strong {
  color: var(--text-on-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.student-achievements-stat > span:last-child {
  color: var(--text-on-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

/* Cards -------------------------------------------------------------------- */
.student-achievements-card {
  display: grid;
  align-content: start;
  gap: var(--space-4);
}

.student-achievements-card .card-header {
  align-items: center;
  flex-wrap: wrap;
}

.student-achievements-card-title {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
}

.student-achievements-title-icon {
  display: grid;
  place-items: center;
  width: var(--space-8);
  height: var(--space-8);
  border-radius: var(--radius-sm);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

/* Column chart ------------------------------------------------------------- */
.student-achievements-chart {
  display: grid;
  grid-auto-columns: minmax(0, 1fr);
  grid-auto-flow: column;
  gap: var(--space-3);
  height: calc(var(--space-20) * 2);
  margin: 0;
  padding: var(--space-3) var(--space-3) 0;
  border-radius: var(--radius-lg);
  background: repeating-linear-gradient(to top, var(--border-subtle) 0 1px, transparent 1px 25%), var(--surface-subtle);
  list-style: none;
}

.student-achievements-chart-bar {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  justify-items: center;
  gap: var(--space-1);
  min-width: 0;
}

.student-achievements-chart-value {
  color: var(--text-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  font-variant-numeric: tabular-nums;
}

.student-achievements-chart-track {
  display: flex;
  align-items: flex-end;
  width: min(100%, var(--space-10));
  height: 100%;
}

.student-achievements-chart-track > span {
  width: 100%;
  min-height: var(--space-1);
  border-radius: var(--radius-xs) var(--radius-xs) 0 0;
  background: var(--color-info);
}

.student-achievements-chart-track > .is-green {
  background: var(--color-success);
}

.student-achievements-chart-track > .is-orange {
  background: var(--color-accent-500);
}

.student-achievements-chart-label {
  padding-block: var(--space-1-5);
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.student-achievements-note {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-accent-50);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.student-achievements-note .icon-glyph {
  margin-top: var(--space-0-5);
  color: var(--color-accent-800);
}

/* Badges ------------------------------------------------------------------- */
.student-achievements-badge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 96px), 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-achievements-badge {
  display: grid;
  align-content: start;
  justify-items: center;
  gap: var(--space-1);
  min-width: 0;
  text-align: center;
}

.student-achievements-badge strong {
  margin-top: var(--space-1);
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.student-achievements-badge > span:last-child {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
  overflow-wrap: anywhere;
}

.student-achievements-badge-icon {
  display: grid;
  place-items: center;
  width: var(--space-12);
  height: var(--space-12);
  border-radius: var(--radius-lg);
  background: var(--color-accent-500);
  color: var(--text-on-accent);
  box-shadow: var(--shadow-sm);
}

.student-achievements-badge-icon.is-green {
  background: var(--color-success-strong);
  color: var(--color-white);
}

.student-achievements-badge-icon.is-blue {
  background: var(--color-info-strong);
  color: var(--color-white);
}

.student-achievements-badge-icon.is-pink {
  background: var(--color-danger-strong);
  color: var(--color-white);
}

.student-achievements-badge-icon.is-orange {
  background: var(--color-warning-strong);
  color: var(--color-white);
}

/* Lists -------------------------------------------------------------------- */
.student-achievements-list {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-achievements-list > li {
  padding: var(--space-3) 0;
  border-top: 1px solid var(--border-subtle);
}

.student-achievements-list > li:first-child {
  padding-top: 0;
  border-top: 0;
}

.student-achievements-list > li:last-child {
  padding-bottom: 0;
}

.student-achievements-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
}

.student-achievements-row-icon {
  display: grid;
  place-items: center;
  width: var(--space-10);
  height: var(--space-10);
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
  color: var(--color-primary-700);
}

.student-achievements-row-icon.is-label {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-extrabold);
}

.student-achievements-row-icon.is-blue {
  background: var(--color-info-soft);
  color: var(--color-info-text);
}

.student-achievements-row-icon.is-green {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.student-achievements-row-icon.is-orange {
  background: var(--color-accent-100);
  color: var(--color-accent-800);
}

.student-achievements-row-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.student-achievements-row-copy strong {
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.student-achievements-row-copy > span {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
}

.student-achievements-points {
  color: var(--color-success-text);
  font-size: var(--font-size-md);
  white-space: nowrap;
}

.student-achievements-percent {
  color: var(--text-primary);
  font-size: var(--font-size-md);
  font-variant-numeric: tabular-nums;
}

/* Side --------------------------------------------------------------------- */
.student-achievements-progress-mini {
  display: grid;
  gap: var(--space-2);
}

.student-achievements-progress-row {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.student-achievements-progress-row strong {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
}

.student-achievements-progress-link {
  justify-self: start;
}

.student-achievements-tip {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--color-success-border);
  border-radius: var(--radius-lg);
  background: var(--color-success-soft);
}

.student-achievements-tip-icon {
  display: grid;
  place-items: center;
  width: var(--space-10);
  height: var(--space-10);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  color: var(--color-success-text);
}

.student-achievements-tip strong {
  color: var(--text-primary);
  font-size: var(--font-size-md);
}

.student-achievements-tip p {
  margin: var(--space-1) 0 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

@media (max-width: 1200px) {
  .student-achievements-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-achievements-side {
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 320px), 1fr));
  }

  .student-achievements-hero {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .student-achievements-stats {
    grid-column: 1 / -1;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 140px), 1fr));
  }
}

@media (max-width: 640px) {
  .student-achievements-hero {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-achievements-hero-mascot {
    display: none;
  }

  .student-achievements-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
