<template>
  <div class="progress-page page-stack">
    <PageHeaderBlock :title="t('progress.pageTitle')" />

    <ResourceSection
      class="progress-sections"
      :loading="overview.loading.value"
      :error="overview.error.value"
      @retry="overview.reload"
    >
      <template v-if="data">
        <!-- The level: one big figure, the XP still to go, and the five levels as seals on a path. -->
        <section class="progress-hero" aria-labelledby="progress-level-title" data-testid="xp-level">
          <div class="progress-hero-copy">
            <h2 id="progress-level-title" class="progress-overline">{{ t('progress.yourXpLevel') }}</h2>
            <div class="progress-hero-level">
              <strong class="stat-figure progress-hero-number" aria-hidden="true">{{ data.level }}</strong>
              <div class="progress-hero-name">
                <p class="progress-hero-title">{{ t(levelInfo.titleKey) }}</p>
                <p class="progress-hero-xp">
                  <span class="sr-only">{{ t('progress.levelN', { level: data.level }) }} · </span
                  >{{ t('progress.xpOfNext', { xp: data.totalXp, next: data.nextLevelXp }) }}
                </p>
              </div>
            </div>
            <ProgressBar :progress="data.xpIntoLevel" :label="t('progress.toNextLevel')" class="is-lg" />
            <p class="progress-hero-hint">
              {{
                data.totalXp === 0
                  ? t('progress.firstXpHint')
                  : t('progress.xpToLevel', {
                      xp: data.nextLevelXp - data.totalXp,
                      level: data.level + 1,
                      title: t(xpLevelTitleKey(data.level + 1)),
                    })
              }}
            </p>
          </div>

          <ol class="progress-ladder" :aria-label="t('progress.levelsLabel')">
            <li
              v-for="step in ladder"
              :key="step.level"
              :class="{ 'is-reached': step.level <= data.level, 'is-you': step.level === youLevel }"
            >
              <span v-if="step.level === youLevel" class="progress-ladder-you">{{ t('progress.you') }}</span>
              <span
                class="seal progress-ladder-seal"
                :class="{ 'is-outline': step.level === data.level + 1, 'is-muted': step.level > data.level + 1 }"
                lang="zh-CN"
                aria-hidden="true"
                >{{ step.numeral }}</span
              >
              <span class="progress-ladder-title">
                <span class="progress-ladder-number">{{ t('progress.levelN', { level: step.level }) }}</span>
                {{ t(step.titleKey) }}
              </span>
            </li>
          </ol>
        </section>

        <div class="progress-momentum">
          <!-- The streak: the number, then the month with a seal on every day the daily goal was reached. -->
          <section class="progress-streak" aria-labelledby="progress-streak-title" data-testid="streak">
            <div class="progress-streak-head">
              <h2 id="progress-streak-title" class="progress-streak-title">
                <strong class="stat-figure progress-streak-number">{{ data.currentStreak }}</strong>
                <span>{{ t('progress.dayStreak') }}</span>
              </h2>
              <p class="progress-streak-hint">{{ streakHint }}</p>
            </div>
            <StreakCalendar :calendar="data.calendar" :busy="overview.loading.value" @month="showMonth" />
          </section>

          <!-- Where the XP came from: quiet bars by source, then the latest XP as a ledger. -->
          <section class="progress-xp" aria-labelledby="progress-sources-title" data-testid="xp-sources">
            <h2 id="progress-sources-title" class="progress-section-title">{{ t('progress.whereXpComesFrom') }}</h2>
            <ul class="progress-source-list">
              <li v-for="source in sources" :key="source.key" class="progress-source">
                <span class="progress-source-label">{{ source.label }}</span>
                <RouterLink v-if="source.to" class="btn btn-link progress-source-link" :to="source.to">
                  {{ source.action }}<IconGlyph name="chevronRight" :size="14" />
                </RouterLink>
                <strong class="progress-source-xp">{{ t('progress.xpAmount', { xp: source.xp }) }}</strong>
                <ProgressBar
                  :progress="shareOfMax(source.xp, sourceXp)"
                  :label="source.label"
                  class="is-sm progress-source-bar"
                />
              </li>
            </ul>

            <h3 class="progress-recent-title">{{ t('progress.recentXp') }}</h3>
            <p v-if="!data.recent.length" class="progress-empty">{{ t('progress.noXpYet') }}</p>
            <ol v-else class="progress-recent">
              <li v-for="(entry, index) in data.recent" :key="index">
                <span class="progress-recent-what">{{ recentLabel(entry) }}</span>
                <time class="progress-recent-when" :datetime="entry.at">{{ formatShortDate(entry.at) }}</time>
                <strong class="progress-recent-xp">+{{ entry.xp }}</strong>
              </li>
            </ol>
          </section>
        </div>

        <!-- The seal wall: earned badges are stamped, the rest wait in grey with how to earn them. -->
        <section class="progress-seals" aria-labelledby="progress-badges-title" data-testid="badges">
          <div class="progress-seals-head">
            <h2 id="progress-badges-title" class="progress-section-title">{{ t('progress.badges') }}</h2>
            <p class="progress-seals-meta">
              {{
                earnedCount
                  ? t('progress.badgesEarned', { earned: earnedCount, total: data.badges.length })
                  : t('progress.noBadgesYet', { total: data.badges.length })
              }}
            </p>
          </div>
          <ul class="progress-seal-grid">
            <li v-for="badge in data.badges" :key="badge.key">
              <button
                class="progress-badge"
                :class="{ 'is-earned': badge.earned }"
                type="button"
                :data-testid="`badge-${badge.key}`"
                @click="openBadge = badge"
              >
                <span
                  class="seal progress-badge-seal"
                  :class="{ 'is-muted': !badge.earned }"
                  lang="zh-CN"
                  aria-hidden="true"
                >
                  {{ BADGE_SEALS[badge.key].character }}
                </span>
                <span class="progress-badge-copy">
                  <span class="progress-badge-name">{{ t(BADGES[badge.key].nameKey) }}</span>
                  <span class="progress-badge-when">
                    {{ badge.earned ? formatShortDate(badge.earnedAt) : t(BADGES[badge.key].ruleKey) }}
                  </span>
                </span>
                <span class="sr-only">{{ badge.earned ? t('progress.earned') : t('progress.notEarned') }}</span>
              </button>
            </li>
          </ul>
        </section>

        <PlanProgressCard />
      </template>
    </ResourceSection>

    <AppOverlay
      variant="drawer"
      :open="openBadge !== null"
      :title="openBadge ? t(BADGES[openBadge.key].nameKey) : ''"
      :subtitle="
        openBadge?.earned
          ? t('progress.badgeEarnedOn', { date: formatDate(openBadge.earnedAt) })
          : t('progress.badgeNotEarned')
      "
      test-id="badge-detail"
      @close="openBadge = null"
    >
      <div v-if="openBadge" class="progress-badge-detail">
        <figure class="progress-badge-figure">
          <span
            class="seal progress-badge-seal is-large"
            :class="{ 'is-muted': !openBadge.earned }"
            lang="zh-CN"
            aria-hidden="true"
            >{{ BADGE_SEALS[openBadge.key].character }}</span
          >
          <figcaption>
            <span class="hanzi" lang="zh-CN">{{ BADGE_SEALS[openBadge.key].character }}</span>
            {{ t(BADGE_SEALS[openBadge.key].gloss) }}
          </figcaption>
        </figure>
        <StatusPill
          :label="openBadge.earned ? t('progress.earned') : t('progress.notEarned')"
          :tone="openBadge.earned ? 'success' : 'neutral'"
          :icon="openBadge.earned ? 'check' : 'lock'"
        />
        <dl class="kv-list">
          <dt>{{ openBadge.earned ? t('progress.howYouEarnedIt') : t('progress.howToEarnIt') }}</dt>
          <dd>{{ t(BADGES[openBadge.key].ruleKey) }}</dd>
          <template v-if="openBadge.earned">
            <dt>{{ t('progress.earnedOn') }}</dt>
            <dd>{{ formatDate(openBadge.earnedAt) }}</dd>
          </template>
          <dt>{{ t('progress.collection') }}</dt>
          <dd>{{ t('progress.badgesOf', { earned: earnedCount, total: data?.badges.length ?? 0 }) }}</dd>
        </dl>
      </div>
      <template #footer>
        <button class="btn btn-primary" type="button" @click="openBadge = null">{{ t('progress.close') }}</button>
      </template>
    </AppOverlay>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

import { xpApi } from '@/api/learn'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import AppOverlay from '@/components/common/AppOverlay.vue'
import StreakCalendar from '@/components/learn/StreakCalendar.vue'
import PlanProgressCard from '@/components/plan/PlanProgressCard.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { learnMessages } from '@/plugins/learnMessages'
import { progressMessages } from '@/plugins/progressMessages'
import type { BadgeKey, ProgressOverview } from '@/types/learn'
import { formatDate, formatShortDate, shareOfMax } from '@/utils/format'
import { BADGES } from '@/utils/learnBadges'
import { xpLevelFor, xpLevelTitleKey } from '@/utils/xpLevel'
import '@/styles/learn-progress.css'

/**
 * Progress tells the student's growth as a story: the XP level as one big figure with the five levels stamped on a
 * path, the streak as a month of seals, where the XP came from, the seal wall of badges and the learning plan.
 */

/** Each badge is stamped with a character that says what it is for (decorative; the badge name is the label). */
const BADGE_SEALS: Readonly<Record<BadgeKey, { character: string; gloss: string }>> = {
  FIRST_STEPS: { character: '始', gloss: 'progress.sealBegin' },
  STREAK_EXPLORER: { character: '恒', gloss: 'progress.sealPersevere' },
  XP_STARTER: { character: '火', gloss: 'progress.sealSpark' },
  QUIZ_STAR: { character: '星', gloss: 'progress.sealStar' },
  LESSON_FINISHER: { character: '成', gloss: 'progress.sealAccomplish' },
  TEN_DAY_STREAK: { character: '十', gloss: 'progress.sealTen' },
  UNIT_CHAMPION: { character: '冠', gloss: 'progress.sealChampion' },
  XP_300: { character: '百', gloss: 'progress.sealHundred' },
}

const NUMERALS = ['一', '二', '三', '四', '五'] as const

const { t } = useLocalMessages({ progress: progressMessages, learn: learnMessages })

const month = ref<string | null>(null)
const overview = useResource(() => xpApi.progress(month.value))
const data = computed(() => overview.data.value)
const levelInfo = computed(() => xpLevelFor(data.value?.totalXp ?? 0))
/** The ladder stops at Level 5; a student past it stands on the last seal. */
const youLevel = computed(() => Math.min(data.value?.level ?? 1, NUMERALS.length))

const ladder = NUMERALS.map((numeral, index) => ({
  level: index + 1,
  numeral,
  titleKey: xpLevelTitleKey(index + 1),
}))

const openBadge = ref<ProgressOverview['badges'][number] | null>(null)
const earnedCount = computed(() => data.value?.badges.filter((badge) => badge.earned).length ?? 0)

const streakHint = computed(() => {
  const value = data.value
  if (!value) return ''
  if (value.currentStreak === 0) return t('progress.startStreak')
  const best = t('progress.best', { days: value.bestStreak }, value.bestStreak)
  return value.goalReachedToday
    ? `${best} · ${t('progress.goalReachedToday')}`
    : `${best} · ${t('progress.reachGoalToExtend', { days: value.currentStreak + 1 })}`
})

const sources = computed(() => {
  const value = data.value
  if (!value) return []
  const rows: { key: string; label: string; xp: number; to: RouteLocationRaw | null; action: string }[] = [
    {
      key: 'learn',
      label: t('progress.sourceLearn'),
      xp: value.sources.learn,
      to: { name: 'student-learn' },
      action: t('progress.continuePath'),
    },
    {
      key: 'practice',
      label: t('progress.sourcePractice'),
      xp: value.sources.practice,
      to: { name: 'student-practice' },
      action: t('progress.openPractice'),
    },
    { key: 'reviews', label: t('progress.sourceReviews'), xp: value.sources.reviewsAndGoals, to: null, action: '' },
  ]
  if (value.sources.earlier > 0) {
    rows.push({ key: 'earlier', label: t('progress.sourceEarlier'), xp: value.sources.earlier, to: null, action: '' })
  }
  return rows
})

const sourceXp = computed(() => sources.value.map((source) => source.xp))

function recentLabel(entry: ProgressOverview['recent'][number]): string {
  switch (entry.kind) {
    case 'LEARN_LESSON':
      return t('progress.recentLearn', { title: entry.title })
    case 'PRACTICE_SET':
      return t('progress.recentPractice', { title: entry.title })
    case 'QUIZ_STREAK':
      return t('progress.recentQuizStreak', { title: entry.title })
    case 'MISTAKES_REVIEW':
      return t('progress.recentReview')
    case 'DAILY_GOAL':
      return t('progress.recentGoal')
    default:
      return entry.title
  }
}

function showMonth(next: string): void {
  month.value = next
  void overview.reload()
}
</script>
