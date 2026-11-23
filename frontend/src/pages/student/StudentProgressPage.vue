<template>
  <div class="page-stack">
    <PageHeaderBlock :title="t('nav.progress')" hide-title />

    <PlanProgressCard />

    <div class="progress-sections">
      <div class="progress-row">
        <!-- Am I improving? Each skill, what changed this week, and what to do next. -->
        <section class="panel progress-skills" aria-labelledby="progress-skills-title" data-testid="skills">
          <div class="progress-card-head">
            <h2 id="progress-skills-title" class="card-title">{{ t('progress.skills') }}</h2>
            <p v-if="skills.data.value" class="type-caption">
              {{ weakest ? t('progress.skillsChange') : t('progress.skillsStart') }}
            </p>
          </div>
          <StatePanel
            v-if="skills.loading.value"
            tone="loading"
            spacious
            :title="t('common.loadingTitle')"
            :message="t('common.loadingMessage')"
          />
          <StatePanel
            v-else-if="skills.error.value"
            tone="error"
            spacious
            :title="t('common.errorTitle')"
            :message="skills.error.value"
            @retry="skills.reload"
          />
          <ul v-else class="progress-skill-grid">
            <li v-for="row in skillRows" :key="row.skill" class="progress-skill">
              <span class="progress-skill-name">{{ row.label }}</span>
              <span class="type-caption progress-skill-change">{{ row.change }}</span>
              <strong class="progress-skill-percent">{{ row.percent }}%</strong>
              <ProgressBar class="is-sm progress-skill-bar" :progress="row.percent" :label="row.label" />
            </li>
          </ul>
          <ProgressAnswers />
          <div v-if="skills.data.value && !skills.error.value" class="progress-skills-foot">
            <p v-if="weakest">{{ t('progress.weakest', { skill: t(SKILLS[weakest].label) }) }}</p>
            <RouterLink
              v-if="weakest"
              class="btn btn-secondary btn-sm"
              :to="{ name: 'student-practice', params: { type: PRACTICE_ACTIVITIES[SKILLS[weakest].practice].slug } }"
            >
              {{ t(SKILLS[weakest].action) }}
            </RouterLink>
            <RouterLink v-else class="btn btn-secondary btn-sm" :to="{ name: 'student-learn' }">
              {{ t('progress.startUnit') }}
            </RouterLink>
          </div>
        </section>

        <!-- The rank, kept small: progress is about skills first. -->
        <StatePanel
          v-if="overview.loading.value"
          heading-tag="h2"
          tone="loading"
          spacious
          :title="t('common.loadingTitle')"
          :message="t('common.loadingMessage')"
        />
        <StatePanel
          v-else-if="overview.error.value && !data"
          heading-tag="h2"
          tone="error"
          spacious
          :title="t('common.errorTitle')"
          :message="overview.error.value"
          @retry="overview.reload"
        />
        <section
          v-else-if="data"
          class="panel progress-level"
          aria-labelledby="progress-level-title"
          data-testid="xp-level"
        >
          <h2 id="progress-level-title" class="page-eyebrow">{{ t('progress.yourXpLevel') }}</h2>
          <div class="progress-level-head">
            <strong class="stat-figure" aria-hidden="true">{{ data.level }}</strong>
            <p class="progress-level-title">
              <span class="sr-only">{{ t('progress.levelN', { level: data.level }) }} · </span
              >{{ t(xpLevelTitleKey(data.level)) }}
            </p>
          </div>
          <ProgressBar :progress="data.xpIntoLevel" :label="t('progress.toNextLevel')" class="is-sm" />
          <p class="progress-level-next">
            {{
              t('progress.xpToLevel', {
                xp: data.nextLevelXp - data.totalXp,
                level: data.level + 1,
                title: t(xpLevelTitleKey(data.level + 1)),
              })
            }}
          </p>
        </section>
      </div>

      <div v-if="data" class="progress-row">
        <!-- The streak: the number, then the month with every day the daily goal was reached. -->
        <section class="panel progress-streak" aria-labelledby="progress-streak-title" data-testid="streak">
          <DataNotice v-if="overview.error.value" :error="overview.error.value">
            <template #actions>
              <button class="btn btn-secondary btn-sm" type="button" @click="overview.reload">
                {{ t('common.retry') }}
              </button>
            </template>
          </DataNotice>
          <StreakCalendar :calendar="data.calendar" @month="showMonth">
            <div class="progress-streak-head">
              <strong class="stat-figure" aria-hidden="true">{{ data.currentStreak }}</strong>
              <div>
                <h2 id="progress-streak-title" class="card-title">
                  <span class="sr-only">{{ data.currentStreak }} </span>{{ t('progress.dayStreak') }}
                </h2>
                <p class="type-caption">{{ streakHint }}</p>
              </div>
            </div>
          </StreakCalendar>
        </section>

        <!-- Badges: earned ones are filled, the rest wait dashed with how to earn them. -->
        <section class="panel progress-badges" aria-labelledby="progress-badges-title" data-testid="badges">
          <h2 id="progress-badges-title" class="card-title">{{ t('progress.badges') }}</h2>
          <ul class="progress-badge-grid">
            <li v-for="badge in data.badges" :key="badge.key">
              <button
                class="progress-badge"
                :class="{ 'is-earned': badge.earned }"
                type="button"
                :data-testid="`badge-${badge.key}`"
                @click="openBadge = badge"
              >
                <span class="progress-badge-icon" aria-hidden="true">
                  <IconGlyph :name="BADGES[badge.key].icon" :size="24" />
                </span>
                <span class="progress-badge-name">{{ t(BADGES[badge.key].nameKey) }}</span>
                <span v-if="!badge.earned" class="progress-badge-when">{{ t(BADGES[badge.key].ruleKey) }}</span>
                <span class="sr-only">{{ badge.earned ? t('progress.earned') : t('progress.notEarned') }}</span>
              </button>
            </li>
          </ul>
        </section>
      </div>
    </div>

    <!-- A badge: when it was earned (or that it is not yet) and how it is earned. -->
    <AppOverlay
      :open="openBadge !== null"
      :title="openBadge ? t(BADGES[openBadge.key].nameKey) : ''"
      :subtitle="
        openBadge?.earned
          ? t('progress.badgeEarnedOn', { date: formatDate(openBadge.earnedAt) })
          : t('progress.notEarned')
      "
      test-id="badge-detail"
      @close="openBadge = null"
    >
      <div v-if="openBadge" class="progress-badge-rule" :class="{ 'is-earned': openBadge.earned }">
        <span class="progress-badge-icon is-lg" aria-hidden="true">
          <IconGlyph :name="BADGES[openBadge.key].icon" :size="32" />
        </span>
        <div class="progress-badge-rule-copy">
          <p class="card-title">{{ openBadge.earned ? t('progress.howYouEarnedIt') : t('progress.howToEarnIt') }}</p>
          <p>{{ t(BADGES[openBadge.key].ruleKey) }}</p>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-secondary" type="button" @click="openBadge = null">{{ t('common.close') }}</button>
      </template>
    </AppOverlay>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { xpApi } from '@/api/learn'
import AppOverlay from '@/components/common/AppOverlay.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ProgressAnswers from '@/components/learn/ProgressAnswers.vue'
import StreakCalendar from '@/components/learn/StreakCalendar.vue'
import PlanProgressCard from '@/components/plan/PlanProgressCard.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { PRACTICE_ACTIVITIES } from '@/config/practiceActivities'
import { learnBadgeMessages } from '@/plugins/learnBadgeMessages'
import { progressMessages } from '@/plugins/progressMessages'
import type { PracticeType, ProgressOverview, ProgressSkill } from '@/types/learn'
import { formatDate } from '@/utils/format'
import { BADGES } from '@/utils/learnBadges'
import { xpLevelTitleKey } from '@/utils/xpLevel'
import '@/styles/learn-progress.css'

/**
 * Progress answers "Am I improving?": the plan across the top, the skills with this week's change and the weakest one to
 * practise (with this week's answers and hints), the rank beside them, then the streak calendar and the badges.
 */

/** Each skill, how to practise it, and the Practice tab that does. */
const SKILLS: Readonly<Record<ProgressSkill, { label: string; action: string; practice: PracticeType }>> = {
  VOCABULARY: { label: 'progress.skillVocabulary', action: 'progress.practiseVocabulary', practice: 'FLASHCARDS' },
  LISTENING: { label: 'progress.skillListening', action: 'progress.practiseListening', practice: 'LISTENING' },
  CHARACTERS: { label: 'progress.skillCharacters', action: 'progress.practiseCharacters', practice: 'TRACING' },
  SENTENCES: { label: 'progress.skillSentences', action: 'progress.practiseSentences', practice: 'DRAG_DROP' },
}

const { t } = useLocalMessages({ progress: progressMessages, learn: learnBadgeMessages })

const month = ref<string | null>(null)
const overview = useResource(() => xpApi.progress(month.value))
const skills = useResource(xpApi.progressSkills)
const data = overview.data

const weakest = computed(() => skills.data.value?.weakest ?? null)

const skillRows = computed(() =>
  (skills.data.value?.skills ?? []).map(({ skill, percent, weeklyChange }) => ({
    skill,
    label: t(SKILLS[skill].label),
    percent,
    change:
      percent === 0
        ? t('progress.notStarted')
        : weeklyChange > 0
          ? t('progress.changeThisWeek', { points: weeklyChange })
          : '',
  })),
)

const openBadge = ref<ProgressOverview['badges'][number] | null>(null)

const streakHint = computed(() => {
  const value = data.value
  if (!value) return ''
  return value.currentStreak === 0
    ? t('progress.startStreak')
    : t('progress.best', { days: value.bestStreak }, value.bestStreak)
})

function showMonth(next: string): void {
  month.value = next
  void overview.reload()
}
</script>
