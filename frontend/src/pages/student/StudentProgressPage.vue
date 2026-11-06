<template>
  <div class="progress-page page-stack">
    <PageHeaderBlock :title="t('progress.pageTitle')" hide-title />

    <PlanProgressCard />

    <ResourceSection
      class="progress-sections"
      :loading="(overview.loading.value && !data) || skills.loading.value"
      :error="overview.error.value"
      @retry="overview.reload"
    >
      <template v-if="data">
        <div class="progress-row">
          <!-- Am I improving? Each skill, what changed this week, and what to do next. -->
          <section class="panel progress-skills" aria-labelledby="progress-skills-title" data-testid="skills">
            <div class="progress-card-head">
              <h2 id="progress-skills-title" class="card-title">{{ t('progress.skills') }}</h2>
              <p class="type-caption">{{ skillsStarted ? t('progress.skillsChange') : t('progress.skillsStart') }}</p>
            </div>
            <p v-if="skills.error.value" class="field-error" role="alert">{{ skills.error.value }}</p>
            <ul v-else class="progress-skill-grid">
              <li v-for="row in skillRows" :key="row.skill" class="progress-skill">
                <span class="progress-skill-name">{{ row.label }}</span>
                <span class="type-caption progress-skill-change">{{ row.change }}</span>
                <strong class="progress-skill-percent">{{ row.percent }}%</strong>
                <ProgressBar class="is-sm progress-skill-bar" :progress="row.percent" :label="row.label" />
              </li>
            </ul>
            <div v-if="!skills.error.value" class="progress-skills-foot">
              <p v-if="weakest" class="type-body">{{ t('progress.weakest', { skill: t(SKILLS[weakest].label) }) }}</p>
              <RouterLink v-if="weakest" class="btn btn-secondary btn-sm" :to="practiceRoute(weakest)">
                {{ t(SKILLS[weakest].action) }}
              </RouterLink>
              <RouterLink v-else class="btn btn-secondary btn-sm" :to="{ name: 'student-learn' }">
                {{ t('progress.startUnit') }}
              </RouterLink>
            </div>
          </section>

          <!-- The XP level, kept small: progress is about skills first. -->
          <section class="panel progress-level" aria-labelledby="progress-level-title" data-testid="xp-level">
            <h2 id="progress-level-title" class="page-eyebrow">{{ t('progress.yourXpLevel') }}</h2>
            <div class="progress-level-head">
              <strong class="stat-figure" aria-hidden="true">{{ data.level }}</strong>
              <div>
                <p class="progress-level-title">{{ t(levelInfo.titleKey) }}</p>
                <p class="type-caption">
                  <span class="sr-only">{{ t('progress.levelN', { level: data.level }) }} · </span
                  >{{ t('progress.xpOfNext', { xp: data.totalXp, next: data.nextLevelXp }) }}
                </p>
              </div>
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
            <ol class="progress-level-steps" :aria-label="t('progress.levelsLabel')">
              <li v-for="step in LEVEL_STEPS" :key="step" :class="{ 'is-reached': step <= data.level }">
                <span aria-hidden="true">{{ step }}</span>
                <span class="sr-only"
                  >{{ t('progress.levelN', { level: step }) }} · {{ t(xpLevelTitleKey(step)) }}</span
                >
              </li>
            </ol>
          </section>
        </div>

        <div class="progress-row">
          <!-- The streak: the number, then the month with every day the daily goal was reached. -->
          <section class="panel progress-streak" aria-labelledby="progress-streak-title" data-testid="streak">
            <StreakCalendar :calendar="data.calendar" :busy="overview.loading.value" @month="showMonth">
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
            <div class="progress-card-head">
              <h2 id="progress-badges-title" class="card-title">{{ t('progress.badges') }}</h2>
              <p class="type-caption">
                {{ t('progress.badgesEarned', { earned: earnedCount, total: data.badges.length }) }}
              </p>
            </div>
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
                  <span class="progress-badge-when">
                    {{ badge.earned ? formatShortDate(badge.earnedAt) : t(BADGES[badge.key].ruleKey) }}
                  </span>
                  <span class="sr-only">{{ badge.earned ? t('progress.earned') : t('progress.notEarned') }}</span>
                </button>
              </li>
            </ul>
          </section>
        </div>
      </template>
    </ResourceSection>

    <AppOverlay
      :open="openBadge !== null"
      :title="openBadge ? t(BADGES[openBadge.key].nameKey) : ''"
      :subtitle="
        openBadge?.earned
          ? t('progress.badgeEarnedOn', { date: formatShortDate(openBadge.earnedAt) })
          : t('progress.badgeNotEarned')
      "
      test-id="badge-detail"
      @close="openBadge = null"
    >
      <div v-if="openBadge" class="progress-badge-detail">
        <div class="progress-badge-rule" :class="{ 'is-earned': openBadge.earned }">
          <span class="progress-badge-icon is-lg" aria-hidden="true">
            <IconGlyph :name="BADGES[openBadge.key].icon" :size="32" />
          </span>
          <div class="progress-badge-rule-copy">
            <StatusPill
              :label="openBadge.earned ? t('progress.earned') : t('progress.notEarned')"
              :tone="openBadge.earned ? 'success' : 'outline'"
              :icon="openBadge.earned ? 'check' : 'lock'"
            />
            <p class="card-title">{{ openBadge.earned ? t('progress.howYouEarnedIt') : t('progress.howToEarnIt') }}</p>
            <p>{{ t(BADGES[openBadge.key].ruleKey) }}</p>
          </div>
        </div>
        <dl class="kv-list">
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

import { PRACTICE_TYPE_SLUGS, xpApi } from '@/api/learn'
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
import type { PracticeType, ProgressOverview, ProgressSkill } from '@/types/learn'
import { formatDate, formatShortDate } from '@/utils/format'
import { BADGES } from '@/utils/learnBadges'
import { xpLevelFor, xpLevelTitleKey } from '@/utils/xpLevel'
import '@/styles/learn-progress.css'

/**
 * Progress answers "Am I improving?": the plan across the top, the skills with this week's change and the weakest one to
 * practise, the XP level beside them, then the streak calendar and the badges.
 */
const LEVEL_STEPS = [1, 2, 3, 4, 5] as const

/** Each skill, how to practise it, and the Practice tab that does. */
const SKILLS: Readonly<Record<ProgressSkill, { label: string; action: string; practice: PracticeType }>> = {
  VOCABULARY: { label: 'progress.skillVocabulary', action: 'progress.practiseVocabulary', practice: 'FLASHCARDS' },
  LISTENING: { label: 'progress.skillListening', action: 'progress.practiseListening', practice: 'LISTENING' },
  CHARACTERS: { label: 'progress.skillCharacters', action: 'progress.practiseCharacters', practice: 'TRACING' },
  SENTENCES: { label: 'progress.skillSentences', action: 'progress.practiseSentences', practice: 'DRAG_DROP' },
}

const SKILL_ORDER: readonly ProgressSkill[] = ['VOCABULARY', 'LISTENING', 'CHARACTERS', 'SENTENCES']

const { t } = useLocalMessages({ progress: progressMessages, learn: learnMessages })

const month = ref<string | null>(null)
const overview = useResource(() => xpApi.progress(month.value))
const skills = useResource(() => xpApi.progressSkills())
const data = computed(() => overview.data.value)
const levelInfo = computed(() => xpLevelFor(data.value?.totalXp ?? 0))

const weakest = computed(() => skills.data.value?.weakest ?? null)
const skillsStarted = computed(() => weakest.value !== null)

const skillRows = computed(() =>
  SKILL_ORDER.map((skill) => {
    const row = skills.data.value?.skills.find((item) => item.skill === skill)
    const percent = row?.percent ?? 0
    const change = row?.weeklyChange ?? 0
    return {
      skill,
      label: t(SKILLS[skill].label),
      percent,
      change:
        percent === 0 ? t('progress.notStarted') : change > 0 ? t('progress.changeThisWeek', { points: change }) : '',
    }
  }),
)

function practiceRoute(skill: ProgressSkill): RouteLocationRaw {
  return { name: 'student-practice', params: { type: PRACTICE_TYPE_SLUGS[SKILLS[skill].practice] } }
}

const openBadge = ref<ProgressOverview['badges'][number] | null>(null)
const earnedCount = computed(() => data.value?.badges.filter((badge) => badge.earned).length ?? 0)

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
