<template>
  <div class="page-stack student-progress-page" data-testid="student-progress-page">
    <PageHeaderBlock
      :title="t('studentProgress.title')"
      :description="t('studentProgress.description')"
      icon="progress"
    >
      <template #actions>
        <RouterLink class="btn btn-secondary" to="/student/achievements">
          {{ t('studentProgress.viewAchievements') }}
        </RouterLink>
        <RouterLink class="btn btn-primary" to="/student/learn">{{ t('studentProgress.continueLearning') }}</RouterLink>
      </template>
    </PageHeaderBlock>

    <DataNotice
      :using-fallback="resource.usingFallback.value"
      :fallback-title="t('studentProgress.previewTitle')"
      :fallback-message="t('studentProgress.previewMessage')"
    />

    <ResourceSection :loading="resource.loading.value" :error="resource.error.value" @retry="reload">
      <section class="student-progress-stats" :aria-label="t('studentProgress.title')">
        <article class="panel student-progress-stat">
          <small>{{ t('studentProgress.overallCompletion') }}</small>
          <strong>{{ formatPercent(overview.overallPercent) }}</strong>
          <ProgressBar :progress="overview.overallPercent" :label="t('studentProgress.overallCompletion')" />
        </article>
        <article class="panel student-progress-stat">
          <small>{{ t('studentProgress.lessonsCompleted') }}</small>
          <strong>{{
            t('studentProgress.lessonsCompletedValue', {
              completed: overview.completedLessons,
              started: overview.startedLessons,
            })
          }}</strong>
          <span class="muted">{{ t('studentProgress.lessonsStartedHint') }}</span>
        </article>
        <article class="panel student-progress-stat">
          <small>{{ t('studentProgress.quizAverage') }}</small>
          <strong>{{ formatPercent(overview.quizAverage) }}</strong>
          <span class="muted">{{ t('studentProgress.quizAverageHint') }}</span>
        </article>
        <article class="panel student-progress-stat">
          <small>{{ t('studentProgress.masteredLessons') }}</small>
          <strong>{{ masteryCounts.MASTERED }}</strong>
          <span class="muted">{{ t('studentProgress.masteredHint', { threshold: MASTERY_QUIZ_THRESHOLD }) }}</span>
        </article>
      </section>

      <p v-if="summary.lastActivitySummary" class="student-progress-last">
        <IconGlyph name="history" :size="18" />
        <span>
          <strong>{{ t('studentProgress.lastActivity') }}:</strong> {{ summary.lastActivitySummary }}
        </span>
      </p>

      <section class="student-progress-layout">
        <article class="panel student-progress-lessons">
          <div class="card-header">
            <h2 class="card-title student-progress-heading">
              <span class="student-progress-title-icon" aria-hidden="true"><IconGlyph name="learn" :size="18" /></span>
              {{ t('studentProgress.lessonsTitle') }}
            </h2>
            <span class="tag" aria-live="polite">{{ t('studentProgress.lessonCount', visibleLessons.length) }}</span>
          </div>

          <div class="student-progress-controls">
            <div class="field">
              <label class="field-label" for="progress-mastery-filter">{{ t('studentProgress.filterLabel') }}</label>
              <select id="progress-mastery-filter" v-model="masteryFilter" class="select">
                <option v-for="option in filterOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </div>
            <div class="field">
              <label class="field-label" for="progress-sort">{{ t('studentProgress.sortLabel') }}</label>
              <select id="progress-sort" v-model="lessonSort" class="select">
                <option v-for="option in sortOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </div>
          </div>

          <ul v-if="visibleLessons.length" class="student-progress-lesson-list">
            <li
              v-for="lesson in visibleLessons"
              :key="lesson.lessonId"
              class="student-progress-lesson"
              data-testid="progress-lesson-row"
            >
              <div class="student-progress-lesson-head">
                <RouterLink
                  class="student-progress-lesson-title"
                  :to="lessonRoute(lesson.lessonId)"
                  :aria-label="t('studentProgress.openLessonAria', { title: lesson.lessonTitle })"
                >
                  {{ lesson.lessonTitle }}
                </RouterLink>
                <MasteryChip :mastery="lesson.mastery" />
              </div>
              <div class="student-progress-lesson-bar">
                <ProgressBar
                  :progress="lesson.completionPercent"
                  :tone="barTone(lesson.mastery)"
                  :label="t('studentProgress.completionLabel', { title: lesson.lessonTitle })"
                />
                <b>{{ formatPercent(lesson.completionPercent) }}</b>
              </div>
              <div class="student-progress-lesson-meta">
                <span>
                  {{ t('studentProgress.bestQuiz') }}:
                  <strong>{{
                    bestQuizPercent(lesson) === null
                      ? t('studentProgress.noQuizYet')
                      : formatPercent(bestQuizPercent(lesson))
                  }}</strong>
                </span>
                <span v-if="lesson.lastAccessedAt">
                  {{ t('studentProgress.lastAccessed', { date: formatDate(lesson.lastAccessedAt) }) }}
                </span>
                <RouterLink class="btn btn-link student-progress-open" :to="lessonRoute(lesson.lessonId)">
                  {{ t('studentProgress.openLesson') }}
                </RouterLink>
              </div>
            </li>
          </ul>
          <StatePanel
            v-else-if="allLessons.length"
            icon="filter"
            :title="t('studentProgress.noFilteredTitle')"
            :message="t('studentProgress.noFilteredMessage')"
            compact
          >
            <template #actions>
              <button class="btn btn-secondary btn-sm" type="button" @click="masteryFilter = 'ALL'">
                {{ t('studentProgress.filterAll') }}
              </button>
            </template>
          </StatePanel>
          <StatePanel
            v-else
            icon="learn"
            :title="t('studentProgress.noLessonsTitle')"
            :message="t('studentProgress.noLessonsMessage')"
          />
        </article>

        <aside class="student-progress-side">
          <article class="panel student-progress-card">
            <h2 class="card-title student-progress-heading">
              <span class="student-progress-title-icon" aria-hidden="true"><IconGlyph name="star" :size="18" /></span>
              {{ t('studentProgress.masteryOverview') }}
            </h2>
            <div class="student-progress-mastery-grid" role="group" :aria-label="t('studentProgress.filterLabel')">
              <button
                v-for="level in LESSON_MASTERY_ORDER"
                :key="level"
                type="button"
                class="student-progress-mastery-count"
                :class="{ 'is-active': masteryFilter === level }"
                :aria-pressed="masteryFilter === level"
                @click="toggleMasteryFilter(level)"
              >
                <MasteryChip :mastery="level" />
                <strong>{{ masteryCounts[level] }}</strong>
              </button>
            </div>

            <h3 class="student-progress-subheading">{{ t('studentProgress.legendTitle') }}</h3>
            <dl class="student-progress-legend" data-testid="progress-mastery-legend">
              <div v-for="item in legendItems" :key="item.level">
                <dt><MasteryChip :mastery="item.level" /></dt>
                <dd>{{ item.text }}</dd>
              </div>
            </dl>
          </article>

          <article class="panel student-progress-card">
            <div>
              <h2 class="card-title student-progress-heading">
                <span class="student-progress-title-icon" aria-hidden="true"><IconGlyph name="hint" :size="18" /></span>
                {{ t('studentProgress.weakAreasTitle') }}
              </h2>
              <p class="card-subtitle student-progress-card-hint">{{ t('studentProgress.weakAreasHint') }}</p>
            </div>
            <ul v-if="weakAreas.length" class="student-progress-weak-list">
              <li v-for="area in weakAreas" :key="area.title" data-testid="progress-weak-area">
                <RouterLink v-if="area.lessonId" class="student-progress-weak-link" :to="lessonRoute(area.lessonId)">
                  <span>{{ area.title }}</span>
                  <small>{{ t('studentProgress.reviewLesson') }} <IconGlyph name="chevronRight" :size="14" /></small>
                </RouterLink>
                <span v-else class="student-progress-weak-text">{{ area.title }}</span>
              </li>
            </ul>
            <StatePanel
              v-else
              icon="status"
              :title="t('studentProgress.noWeakAreasTitle')"
              :message="t('studentProgress.noWeakAreasMessage')"
              compact
            />
          </article>
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'

import { studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import MasteryChip from '@/components/common/MasteryChip.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { useStatusLabels } from '@/composables/useStatusLabels'
import { studentProgress as studentProgressDemo } from '@/demo/data'
import { studentProgressMessages } from '@/plugins/studentProgressMessages'
import type { LessonMastery } from '@/types/api'
import { formatDate, formatPercent } from '@/utils/format'
import { LESSON_MASTERY_ORDER, MASTERY_QUIZ_THRESHOLD, normalizeMastery } from '@/utils/mastery'
import {
  bestQuizPercent,
  countMastery,
  emptyStudentProgress,
  filterLessonsByMastery,
  lessonRoute,
  progressOverview,
  resolveWeakAreas,
  sortLessons,
  type LessonSort,
  type MasteryFilter,
} from '@/utils/progressSummary'

const { t } = useI18n({
  messages: {
    en: { studentProgress: studentProgressMessages.en },
    ms: { studentProgress: studentProgressMessages.ms },
    'zh-CN': { studentProgress: studentProgressMessages['zh-CN'] },
  },
})
const { statusLabel } = useStatusLabels()

const resource = useResource(() => studentApi.progress(), studentProgressDemo)

const emptySummary = emptyStudentProgress()

const masteryFilter = ref<MasteryFilter>('ALL')
const lessonSort = ref<LessonSort>('recent')

const summary = computed(() => resource.data.value ?? emptySummary)
const allLessons = computed(() => summary.value.lessons.filter((lesson) => lesson && lesson.lessonId > 0))
const overview = computed(() => progressOverview(summary.value))
const masteryCounts = computed(() => countMastery(allLessons.value))
const visibleLessons = computed(() =>
  sortLessons(filterLessonsByMastery(allLessons.value, masteryFilter.value), lessonSort.value),
)
const weakAreas = computed(() => resolveWeakAreas(summary.value.weakAreas, allLessons.value))

const filterOptions = computed(() => [
  { value: 'ALL' as MasteryFilter, label: t('studentProgress.filterAll') },
  ...LESSON_MASTERY_ORDER.map((level) => ({ value: level as MasteryFilter, label: statusLabel('mastery', level) })),
])
const sortOptions = computed(() => [
  { value: 'recent' as LessonSort, label: t('studentProgress.sortRecent') },
  { value: 'needsAttention' as LessonSort, label: t('studentProgress.sortNeedsAttention') },
  { value: 'mastery' as LessonSort, label: t('studentProgress.sortMastery') },
  { value: 'title' as LessonSort, label: t('studentProgress.sortTitle') },
])
const legendItems = computed(() => [
  { level: 'NOT_STARTED' as LessonMastery, text: t('studentProgress.legendNotStarted') },
  { level: 'IN_PROGRESS' as LessonMastery, text: t('studentProgress.legendInProgress') },
  { level: 'COMPLETED' as LessonMastery, text: t('studentProgress.legendCompleted') },
  {
    level: 'MASTERED' as LessonMastery,
    text: t('studentProgress.legendMastered', { threshold: MASTERY_QUIZ_THRESHOLD }),
  },
])

function barTone(mastery: string | null | undefined): 'primary' | 'success' | 'warning' | 'info' {
  const level = normalizeMastery(mastery)
  if (level === 'MASTERED') return 'warning'
  if (level === 'COMPLETED') return 'success'
  if (level === 'IN_PROGRESS') return 'info'
  return 'primary'
}

function toggleMasteryFilter(level: LessonMastery): void {
  masteryFilter.value = masteryFilter.value === level ? 'ALL' : level
}

function reload(): Promise<void> {
  return resource.reload()
}
</script>

<style scoped>
.student-progress-page {
  display: grid;
  gap: var(--page-gap);
}

.student-progress-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr));
  gap: var(--grid-gap);
}

.student-progress-stat {
  display: grid;
  align-content: start;
  gap: var(--space-2);
}

.student-progress-stat small {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.student-progress-stat strong {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.student-progress-stat .muted {
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.student-progress-last {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-info-border);
  border-radius: var(--radius-lg);
  background: var(--color-info-soft);
  color: var(--text-primary);
  font-size: var(--font-size-md);
  line-height: var(--line-height-normal);
  overflow-wrap: anywhere;
}

.student-progress-last .icon-glyph {
  margin-top: var(--space-0-5);
  color: var(--color-info-text);
}

.student-progress-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(300px, 1fr);
  gap: var(--page-gap);
  align-items: start;
}

.student-progress-lessons,
.student-progress-card {
  display: grid;
  align-content: start;
  gap: var(--space-4);
  min-width: 0;
}

.student-progress-lessons .card-header {
  align-items: center;
  flex-wrap: wrap;
}

.student-progress-side {
  display: grid;
  gap: var(--page-gap);
  min-width: 0;
}

.student-progress-heading {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
}

.student-progress-title-icon {
  display: grid;
  place-items: center;
  width: var(--space-8);
  height: var(--space-8);
  border-radius: var(--radius-sm);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.student-progress-card-hint {
  margin-top: var(--space-1);
}

.student-progress-subheading {
  margin: 0;
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
  color: var(--text-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-extrabold);
}

.student-progress-controls {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.student-progress-lesson-list,
.student-progress-weak-list {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-progress-lesson {
  display: grid;
  gap: var(--space-2-5);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  transition: border-color var(--duration-fast) var(--ease-standard);
}

.student-progress-lesson:hover {
  border-color: var(--border-interactive);
}

.student-progress-lesson-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.student-progress-lesson-title {
  color: var(--text-primary);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-extrabold);
  overflow-wrap: anywhere;
}

.student-progress-lesson-title:hover,
.student-progress-lesson-title:focus-visible {
  color: var(--text-link);
  text-decoration: underline;
}

.student-progress-lesson-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-2-5);
  align-items: center;
}

.student-progress-lesson-bar b {
  min-width: var(--space-10);
  color: var(--text-primary);
  font-size: var(--font-size-md);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.student-progress-lesson-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-4);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.student-progress-lesson-meta strong {
  color: var(--text-primary);
}

.student-progress-open {
  margin-left: auto;
}

.student-progress-mastery-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2-5);
}

.student-progress-mastery-count {
  display: grid;
  justify-items: start;
  gap: var(--space-1-5);
  min-width: 0;
  padding: var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  color: var(--text-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.student-progress-mastery-count:hover {
  border-color: var(--border-interactive);
  background: var(--surface-hover);
}

.student-progress-mastery-count strong {
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.student-progress-mastery-count.is-active {
  border-color: var(--color-primary-500);
  background: var(--surface-selected);
  box-shadow: inset 0 0 0 1px var(--color-primary-500);
}

.student-progress-legend {
  display: grid;
  gap: var(--space-3);
  margin: 0;
}

.student-progress-legend div {
  display: grid;
  gap: var(--space-1);
}

.student-progress-legend dd {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.student-progress-weak-link,
.student-progress-weak-text {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 1px solid var(--color-warning-border);
  border-radius: var(--radius-md);
  background: var(--color-warning-soft);
  color: var(--text-primary);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-bold);
  overflow-wrap: anywhere;
}

.student-progress-weak-link {
  transition: border-color var(--duration-fast) var(--ease-standard);
}

.student-progress-weak-link:hover {
  border-color: var(--color-warning);
}

.student-progress-weak-link small {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--text-link);
  font-size: var(--font-size-sm);
  text-decoration: underline;
}

@media (max-width: 1200px) {
  .student-progress-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-progress-side {
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 320px), 1fr));
    align-items: start;
  }
}

@media (max-width: 640px) {
  .student-progress-controls {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-progress-open {
    margin-left: 0;
  }
}
</style>
