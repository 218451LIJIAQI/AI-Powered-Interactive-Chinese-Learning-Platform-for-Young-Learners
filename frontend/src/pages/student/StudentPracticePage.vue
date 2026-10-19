<template>
  <div class="page-stack student-practice-page">
    <PageHeaderBlock
      :title="t('studentPractice.pageTitle')"
      :description="t('studentPractice.pageDescription')"
      icon="practice"
    >
      <template #before>
        <LearningBreadcrumb :items="practiceBreadcrumb" data-testid="practice-breadcrumb" />
      </template>
    </PageHeaderBlock>

    <DataNotice
      :using-fallback="usingFallback"
      :error="actionError"
      :success="actionSuccess"
      :error-title="t('studentPractice.noticeErrorTitle')"
      :success-title="t('studentPractice.noticeSuccessTitle')"
      :fallback-title="t('studentPractice.noticeFallbackTitle')"
      :fallback-message="t('studentPractice.noticeFallbackMessage')"
    />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <div class="student-practice-layout">
        <div class="student-practice-main">
          <nav class="panel student-practice-selector" :aria-label="t('studentPractice.activityLibrary')">
            <button
              v-for="option in activityOptions"
              :key="option.title"
              type="button"
              class="student-practice-option"
              :class="{ 'is-active': option.active }"
              :aria-current="option.active ? 'true' : undefined"
              @click="option.action"
            >
              <span class="student-practice-option-icon" :class="`is-${option.tone}`" aria-hidden="true">
                <IconGlyph :name="option.icon" :size="24" />
              </span>
              <span class="student-practice-option-label">{{ option.title }}</span>
            </button>
          </nav>

          <section class="student-practice-stage" aria-labelledby="student-practice-stage-title">
            <div class="student-practice-stage-top">
              <div class="student-practice-stage-copy">
                <h2 id="student-practice-stage-title" :lang="cjkLang(selectedStageTitle)">{{ selectedStageTitle }}</h2>
                <div class="student-practice-stage-meta">
                  <button
                    type="button"
                    class="btn btn-sm student-practice-level"
                    :disabled="!hasMultipleLevels"
                    @click="goToNextActivityLevel()"
                  >
                    {{ selectedStageLevelLabel }}
                    <IconGlyph name="chevronRight" :size="16" />
                  </button>
                  <LearningStageTag :stage="selectedLearningStage" />
                </div>
              </div>

              <dl class="student-practice-stage-stats">
                <div class="student-practice-stat">
                  <span class="student-practice-stat-icon is-accent" aria-hidden="true">
                    <IconGlyph name="star" :size="18" />
                  </span>
                  <dt>{{ t('studentPractice.pointsEarned') }}</dt>
                  <dd>{{ formatNumber(dashboard.points) }} XP</dd>
                </div>
                <div class="student-practice-stat">
                  <span class="student-practice-stat-icon is-success" aria-hidden="true">
                    <IconGlyph name="status" :size="18" />
                  </span>
                  <dt>{{ t('studentPractice.libraryComplete') }}</dt>
                  <dd>{{ activityCompletionLabel }}</dd>
                </div>
              </dl>
            </div>

            <div class="student-practice-stage-body">
              <section class="student-practice-activity-card" data-testid="practice-player" :aria-label="playerLabel">
                <div class="student-practice-card-head">
                  <span class="student-practice-card-badge">{{ selectedActivityBadge }}</span>
                  <span class="student-practice-card-status">{{ currentStatusLabel }}</span>
                </div>
                <h3 class="student-practice-card-title" :lang="cjkLang(selectedActivityTitle)">
                  {{ selectedActivityTitle }}
                </h3>
                <p class="student-practice-card-instructions">{{ selectedActivityInstructions }}</p>

                <p v-if="loadingActivity" class="student-practice-card-loading" role="status">
                  <span class="spinner is-sm" aria-hidden="true" />
                  {{ t('studentPractice.loading') }}
                </p>
                <template v-else>
                  <FlashcardPlayer
                    v-if="selectedActivityType === 'FLASHCARD' && flashcard"
                    :key="`flashcard-${flashcard.id}-${playerKey}`"
                    :flashcard="flashcard"
                    :locked="playerLocked"
                    :submitting="submittingActivity"
                    @submit="submitActivity"
                  />
                  <MatchingPlayer
                    v-else-if="selectedActivityType === 'MATCHING_GAME' && matchingDetail"
                    :key="`matching-${matchingDetail.id}-${playerKey}`"
                    :game="matchingDetail"
                    :locked="playerLocked"
                    :submitting="submittingActivity"
                    @submit="submitActivity"
                  />
                  <DragDropPlayer
                    v-else-if="selectedActivityType === 'DRAG_DROP_ACTIVITY' && dragDropDetail"
                    :key="`dragdrop-${dragDropDetail.id}-${playerKey}`"
                    :activity="dragDropDetail"
                    :locked="playerLocked"
                    :submitting="submittingActivity"
                    @submit="submitActivity"
                  />
                  <ListeningPlayer
                    v-else-if="selectedActivityType === 'LISTENING_EXERCISE' && listeningDetail"
                    :key="`listening-${listeningDetail.id}-${playerKey}`"
                    :exercise="listeningDetail"
                    :locked="playerLocked"
                    :submitting="submittingActivity"
                    @submit="submitActivity"
                  />
                  <StoryPlayer
                    v-else-if="selectedActivityType === 'STORY_ACTIVITY' && storyDetail"
                    :key="`story-${storyDetail.id}-${playerKey}`"
                    :story="storyDetail"
                    :locked="playerLocked"
                    :submitting="submittingActivity"
                    @submit="submitActivity"
                  />
                  <TracingPad
                    v-else-if="selectedActivityType === 'TRACING_EXERCISE' && tracingDetail"
                    :key="`tracing-${tracingDetail.id}-${playerKey}`"
                    :exercise="tracingDetail"
                    :locked="playerLocked"
                    :submitting="submittingActivity"
                    @submit="submitActivity"
                  />
                  <StatePanel
                    v-else
                    compact
                    icon="practice"
                    :title="selectedStageTitle"
                    :message="emptyPlayerMessage"
                  />
                </template>

                <ActivityResult
                  :result="result"
                  :rows="resultRows"
                  :type="selectedActivityType"
                  :preview="resultPreview"
                  @retry="tryAgain"
                />
              </section>
            </div>

            <div class="student-practice-stage-footer">
              <button
                type="button"
                class="btn btn-secondary"
                :disabled="!hasMultipleLevels || submittingActivity"
                @click="goToPreviousActivityLevel()"
              >
                <IconGlyph name="chevronLeft" :size="16" />
                {{ t('studentPractice.previousLevel') }}
              </button>

              <div class="student-practice-footer-status">
                <strong>{{ currentStatusLabel }}</strong>
                <small>{{ selectedActivitySetLabel }}</small>
              </div>

              <button
                type="button"
                class="btn btn-secondary"
                :disabled="!hasMultipleLevels || submittingActivity"
                @click="goToNextActivityLevel()"
              >
                {{ t('studentPractice.nextLevel') }}
                <IconGlyph name="chevronRight" :size="16" />
              </button>
            </div>
          </section>

          <section class="panel student-practice-card" aria-labelledby="student-practice-review-title">
            <div class="student-practice-card-header">
              <span class="student-practice-head-icon is-warning" aria-hidden="true">
                <IconGlyph name="alert" :size="18" />
              </span>
              <div class="student-practice-card-heading">
                <h2 id="student-practice-review-title" class="card-title">{{ t('studentPractice.reviewWrong') }}</h2>
                <p class="card-subtitle">{{ t('studentPractice.reviewHint') }}</p>
                <span class="tag student-practice-review-count">{{
                  t('studentPractice.itemsToReview', { count: reviewCards.length })
                }}</span>
              </div>
            </div>

            <ul class="student-review-grid">
              <li v-for="card in reviewCards" :key="card.id" class="student-review-card" :class="`is-${card.kind}`">
                <div class="student-review-top">
                  <span class="student-review-label">{{ card.label }}</span>
                  <span class="student-review-visual" :class="`is-${card.visualTone}`" aria-hidden="true">
                    <IconGlyph :name="card.visualIcon" :size="20" />
                  </span>
                </div>
                <strong class="student-review-title" :lang="cjkLang(card.title)">{{ card.title }}</strong>
                <p>{{ card.primary }}</p>
                <small>{{ card.secondary }}</small>
                <button type="button" class="btn btn-secondary btn-sm" @click="card.action">
                  {{ card.cta }}
                </button>
              </li>
            </ul>
          </section>
        </div>

        <aside class="student-practice-side">
          <section class="panel student-practice-card" aria-labelledby="student-practice-progress-title">
            <div class="student-practice-card-header">
              <span class="media-badge small" aria-hidden="true"><IconGlyph name="progress" :size="18" /></span>
              <h2 id="student-practice-progress-title" class="card-title">
                {{ t('studentPractice.practiceProgress') }}
              </h2>
            </div>

            <ProgressRing
              :progress="practiceProgress"
              :title="t('studentPractice.overallProgress')"
              :subtitle="t('studentPractice.confidence')"
            />

            <dl class="student-practice-side-stats">
              <div>
                <dt>{{ t('studentPractice.completedActivities') }}</dt>
                <dd>{{ completedActivities }}</dd>
              </div>
              <div>
                <dt>{{ t('studentPractice.quizAverage') }}</dt>
                <dd>{{ quizAverageLabel }}</dd>
              </div>
            </dl>

            <div class="student-practice-library">
              <div class="student-practice-library-head">
                <span>{{ t('studentPractice.activityLibrary') }}</span>
                <strong>{{ activityCompletionLabel }}</strong>
              </div>
              <ProgressBar :progress="activityCompletionProgress" :label="t('studentPractice.activityLibrary')" />
            </div>
          </section>

          <section class="panel student-practice-card" aria-labelledby="student-practice-attempts-title">
            <div class="student-practice-card-header">
              <span class="media-badge small" aria-hidden="true"><IconGlyph name="quiz" :size="18" /></span>
              <h2 id="student-practice-attempts-title" class="card-title">
                {{ t('studentPractice.recentAttempts') }}
              </h2>
              <button type="button" class="btn btn-link" @click="openQuiz">
                {{ t('studentPractice.viewAll') }}
              </button>
            </div>

            <ul class="student-practice-attempts">
              <li v-for="attempt in quizAttemptCards" :key="attempt.id" class="student-practice-attempt">
                <span class="student-practice-attempt-icon" :class="attempt.scoreTone" aria-hidden="true">
                  <IconGlyph :name="attempt.icon" :size="18" />
                </span>
                <span class="student-practice-attempt-copy">
                  <strong>{{ attempt.title }}</strong>
                  <span>{{ attempt.dateLabel }} - {{ attempt.levelLabel }}</span>
                </span>
                <strong class="student-practice-attempt-score" :class="attempt.scoreTone">{{
                  attempt.scoreLabel
                }}</strong>
                <button
                  type="button"
                  class="btn btn-secondary btn-sm student-practice-attempt-button"
                  @click="openQuizAttempt(attempt.quizId)"
                >
                  {{ t('studentPractice.review') }}
                </button>
              </li>
            </ul>
          </section>
        </aside>
      </div>
    </ResourceSection>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import ProgressRing from '@/components/common/ProgressRing.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import LearningStageTag from '@/components/content/LearningStageTag.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import LearningBreadcrumb, { type BreadcrumbItem } from '@/components/learning/LearningBreadcrumb.vue'
import ActivityResult from '@/components/practice/ActivityResult.vue'
import DragDropPlayer from '@/components/practice/DragDropPlayer.vue'
import FlashcardPlayer from '@/components/practice/FlashcardPlayer.vue'
import ListeningPlayer from '@/components/practice/ListeningPlayer.vue'
import MatchingPlayer from '@/components/practice/MatchingPlayer.vue'
import StoryPlayer from '@/components/practice/StoryPlayer.vue'
import TracingPad from '@/components/practice/TracingPad.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useResource } from '@/composables/useResource'
import { gradeDemoActivity } from '@/demo/activityKeys'
import {
  activityLibrary,
  demoActivitySources,
  dragDropActivities,
  dragDropActivity,
  flashcardActivities,
  listeningActivities,
  listeningActivity,
  matchingActivities,
  matchingActivity,
  studentDashboard as studentDashboardDemo,
  studentProgress as studentProgressDemo,
  studentWrongAnswers,
  storyActivities,
  storyActivity,
  studentLessonPlayer,
  tracingActivities,
  tracingActivity,
} from '@/demo/data'
import { learningContextMessages } from '@/plugins/learningContextMessages'
import { studentPracticeMessages } from '@/plugins/studentPracticeMessages'
import { useAuthStore } from '@/stores/auth'
import type {
  StudentActivityCompletionResponse,
  StudentActivitySummaryResponse,
  StudentDragDropActivityResponse,
  StudentFlashcardResponse,
  StudentListeningExerciseResponse,
  StudentMatchingGameResponse,
  StudentStoryActivityResponse,
  StudentTracingExerciseResponse,
} from '@/types/api'
import {
  cjkLang,
  dragDropResultLabels,
  listeningResultLabels,
  mapResultsToItems,
  matchingResultLabels,
  storyResultLabels,
  type ActivityItemLabel,
  type ActivitySubmission,
} from '@/utils/activityCompletion'
import { resolveApiError } from '@/utils/apiError'
import { clampPercent, formatDate, formatNumber, ratioPercent } from '@/utils/format'
import {
  inferPracticeActivityFromText,
  normalizePracticeActivity,
  practiceRoute,
  type StudentPracticeActivityType,
} from '@/utils/practiceRoute'
import { sortByLearningStage } from '@/utils/learningStage'

const { t } = useI18n({
  messages: {
    en: { studentPractice: studentPracticeMessages.en, learningContext: learningContextMessages.en },
    ms: { studentPractice: studentPracticeMessages.ms, learningContext: learningContextMessages.ms },
    'zh-CN': { studentPractice: studentPracticeMessages['zh-CN'], learningContext: learningContextMessages['zh-CN'] },
  },
})

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/** FR-LRN-02: when practice was opened from a lesson (`?lessonId=`), show its module and lesson. */
interface PracticeLessonContext {
  lessonId: number
  title: string
  moduleTitle?: string | null
}
const contextLessonId = computed(() => {
  const raw = Array.isArray(route.query.lessonId) ? route.query.lessonId[0] : route.query.lessonId
  const parsed = Number(raw)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})
const lessonContext = ref<PracticeLessonContext | null>(null)
let lessonContextSequence = 0

watch(
  contextLessonId,
  async (lessonId) => {
    const sequence = ++lessonContextSequence
    lessonContext.value = null
    if (!lessonId) return
    if (auth.isPreviewSession) {
      if (lessonId === studentLessonPlayer.lessonId) {
        lessonContext.value = {
          lessonId,
          title: studentLessonPlayer.title,
          moduleTitle: studentLessonPlayer.moduleTitle,
        }
      }
      return
    }
    try {
      const lesson = await studentApi.lessonPlayer(lessonId)
      if (sequence === lessonContextSequence) {
        lessonContext.value = { lessonId, title: lesson.title, moduleTitle: lesson.moduleTitle }
      }
    } catch {
      // The breadcrumb is optional context; the practice library still works without it.
    }
  },
  { immediate: true },
)
type PracticeActivityType = StudentPracticeActivityType

const activitiesResource = useResource(() => studentApi.activityLibrary({ size: 50 }), activityLibrary)
const wrongAnswersResource = useResource(() => studentApi.wrongAnswers(), studentWrongAnswers)
const dashboardResource = useResource(() => studentApi.dashboard(), studentDashboardDemo)
const progressResource = useResource(() => studentApi.progress(), studentProgressDemo)

const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const selectedActivityType = ref<PracticeActivityType>('FLASHCARD')
const selectedActivityIndexByType = ref<Record<PracticeActivityType, number>>({
  FLASHCARD: 0,
  MATCHING_GAME: 0,
  LISTENING_EXERCISE: 0,
  TRACING_EXERCISE: 0,
  DRAG_DROP_ACTIVITY: 0,
  STORY_ACTIVITY: 0,
})
const requestedActivityType = computed(() => normalizePracticeActivity(route.query.activity) ?? 'FLASHCARD')
const loadingActivity = ref(false)
let activityDetailSequence = 0
const submittingActivity = ref(false)
const matchingDetail = ref<StudentMatchingGameResponse | null>(null)
const listeningDetail = ref<StudentListeningExerciseResponse | null>(null)
const tracingDetail = ref<StudentTracingExerciseResponse | null>(null)
const dragDropDetail = ref<StudentDragDropActivityResponse | null>(null)
const storyDetail = ref<StudentStoryActivityResponse | null>(null)

/** Server (or preview-graded) result of the current attempt; null while the learner is answering. */
const result = ref<StudentActivityCompletionResponse | null>(null)
const resultPreview = ref(false)
/** Bumped to remount the current player with a fresh state ("Try again", level change). */
const playerKey = ref(0)

const activities = computed(
  () =>
    activitiesResource.data.value ??
    (activitiesResource.usingFallback.value
      ? activityLibrary
      : { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 }),
)
const wrongAnswers = computed(
  () => wrongAnswersResource.data.value ?? (wrongAnswersResource.usingFallback.value ? studentWrongAnswers : []),
)
const emptyDashboard = {
  ...studentDashboardDemo,
  points: 0,
  recentQuizResults: [],
  unfinishedLessons: [],
  weakAreas: [],
}
const emptyProgress = {
  ...studentProgressDemo,
  quizAverage: 0,
  overallCompletionPercent: 0,
  weakAreas: [],
  recentLessons: [],
}
const selectedFlashcardId = computed(() => selectedActivityForType('FLASHCARD')?.id ?? null)
const flashcardResource = useResource<StudentFlashcardResponse | null>(
  () => (selectedFlashcardId.value ? studentApi.flashcard(selectedFlashcardId.value) : Promise.resolve(null)),
  () => previewFlashcardDetail(selectedFlashcardId.value),
)
const flashcard = computed(() => (selectedFlashcardId.value === null ? null : flashcardResource.data.value))
const dashboard = computed(
  () => dashboardResource.data.value ?? (dashboardResource.usingFallback.value ? studentDashboardDemo : emptyDashboard),
)
const progress = computed(
  () => progressResource.data.value ?? (progressResource.usingFallback.value ? studentProgressDemo : emptyProgress),
)

const usingFallback = computed(
  () =>
    activitiesResource.usingFallback.value ||
    wrongAnswersResource.usingFallback.value ||
    flashcardResource.usingFallback.value ||
    dashboardResource.usingFallback.value ||
    progressResource.usingFallback.value,
)
const pageLoading = computed(
  () =>
    activitiesResource.loading.value ||
    wrongAnswersResource.loading.value ||
    flashcardResource.loading.value ||
    dashboardResource.loading.value ||
    progressResource.loading.value,
)
const pageError = computed(
  () =>
    activitiesResource.error.value ||
    wrongAnswersResource.error.value ||
    flashcardResource.error.value ||
    dashboardResource.error.value ||
    progressResource.error.value,
)

/** Preview attempts are graded locally (demo answer keys) and never saved. */
const previewMode = computed(() =>
  selectedActivityType.value === 'FLASHCARD'
    ? flashcardResource.usingFallback.value
    : activitiesResource.usingFallback.value,
)
const playerLocked = computed(() => submittingActivity.value || result.value !== null)

const totalPracticeItems = computed(() => Math.max(activities.value.items.length, 1))
const completedActivities = computed(() => activities.value.items.filter((item) => item.completed).length)
const quizAverageLabel = computed(() => `${clampPercent(progress.value.quizAverage)}%`)
const activityCompletionProgress = computed(() => ratioPercent(completedActivities.value, totalPracticeItems.value))
const activityCompletionLabel = computed(() => `${completedActivities.value} / ${totalPracticeItems.value}`)
const practiceProgress = computed(() =>
  clampPercent(progress.value.overallCompletionPercent || activityCompletionProgress.value),
)
const currentActivitySet = computed(() => activitiesByType(selectedActivityType.value))
const currentActivityLevelNumber = computed(() => selectedActivityIndexByType.value[selectedActivityType.value] + 1)
const selectedActivitySetLabel = computed(() => {
  const total = currentActivitySet.value.length
  if (total <= 0) return t('studentPractice.noLevels')

  return t('studentPractice.levelOf', { current: Math.min(currentActivityLevelNumber.value, total), total })
})
const hasMultipleLevels = computed(() => currentActivitySet.value.length > 1)

/** Summary row of the current activity (any type, including flashcards). */
const currentSummary = computed(() => selectedActivityForType(selectedActivityType.value))
const selectedLearningStage = computed(() => currentSummary.value?.learningStage ?? null)

const currentDetail = computed<{ id: number; title: string; instructions?: string | null } | null>(() => {
  switch (selectedActivityType.value) {
    case 'FLASHCARD':
      // A flashcard title is optional; the front text names the card, as in the admin library.
      return flashcard.value ? { ...flashcard.value, title: flashcard.value.title ?? flashcard.value.frontText } : null
    case 'MATCHING_GAME':
      return matchingDetail.value
    case 'LISTENING_EXERCISE':
      return listeningDetail.value
    case 'TRACING_EXERCISE':
      return tracingDetail.value
    case 'DRAG_DROP_ACTIVITY':
      return dragDropDetail.value
    case 'STORY_ACTIVITY':
      return storyDetail.value
  }
  return null
})

const selectedStageTitle = computed(() => {
  if (selectedActivityType.value === 'FLASHCARD') return t('studentPractice.flashcards')
  return currentSummary.value?.title ?? activityLabel(selectedActivityType.value)
})
const selectedStageLevelLabel = computed(() => {
  const total = currentActivitySet.value.length
  if (total > 1) return t('studentPractice.levelOf', { current: currentActivityLevelNumber.value, total })
  const hskLevel = currentSummary.value?.hskLevel
  return hskLevel ? t('studentPractice.level', { level: hskLevel }) : activityLabel(selectedActivityType.value)
})
const selectedActivityBadge = computed(() => currentSummary.value?.topic || activityLabel(selectedActivityType.value))
const selectedActivityTitle = computed(
  () => currentDetail.value?.title ?? currentSummary.value?.title ?? activityLabel(selectedActivityType.value),
)
const practiceBreadcrumb = computed<BreadcrumbItem[]>(() => {
  const context = lessonContext.value
  const typeLabel = activityLabel(selectedActivityType.value)
  return [
    ...(context
      ? ([
          { kind: 'section', label: t('learningContext.learn'), to: { name: 'student-learn' } },
          { kind: 'module', label: context.moduleTitle },
          {
            kind: 'lesson',
            label: context.title,
            to: { name: 'student-learn', query: { lessonId: String(context.lessonId) } },
          },
        ] satisfies BreadcrumbItem[])
      : []),
    { kind: 'section', label: t('learningContext.practice') },
    { kind: 'section', label: typeLabel },
    { kind: 'activity', label: selectedActivityTitle.value === typeLabel ? null : selectedActivityTitle.value },
  ]
})
const selectedActivityInstructions = computed(() => {
  if (!currentSummary.value) return t('studentPractice.noActivityType')
  const authored = currentDetail.value?.instructions?.trim()
  if (authored) return authored
  const defaults: Record<PracticeActivityType, string> = {
    FLASHCARD: 'flashcardInstruction',
    MATCHING_GAME: 'matchingInstruction',
    LISTENING_EXERCISE: 'listeningInstruction',
    TRACING_EXERCISE: 'traceInstruction',
    DRAG_DROP_ACTIVITY: 'arrangeInstruction',
    STORY_ACTIVITY: 'storyInstruction',
  }
  return t(`studentPractice.${defaults[selectedActivityType.value]}`)
})
const currentStatusLabel = computed(() => {
  const summary = currentSummary.value
  if (!summary) return t('studentPractice.unavailable')
  if (summary.completed) return t('studentPractice.completed')
  return t('studentPractice.xpReward', { points: summary.xpReward ?? 0 })
})
const playerLabel = computed(() =>
  t('studentPractice.playerLabel', {
    activity: activityLabel(selectedActivityType.value),
    title: selectedActivityTitle.value,
  }),
)
const emptyPlayerMessage = computed(() => {
  if (selectedActivityType.value === 'FLASHCARD' && currentSummary.value) return t('studentPractice.noFlashcard')
  return t('studentPractice.noActivityType')
})

const resultLabels = computed<ActivityItemLabel[]>(() => {
  switch (selectedActivityType.value) {
    case 'MATCHING_GAME':
      return matchingDetail.value ? matchingResultLabels(matchingDetail.value.leftItems) : []
    case 'DRAG_DROP_ACTIVITY':
      return dragDropDetail.value ? dragDropResultLabels(dragDropDetail.value.items) : []
    case 'LISTENING_EXERCISE':
      if (!listeningDetail.value) return []
      return listeningDetail.value.freeResponse
        ? [{ key: 'response', label: t('studentPractice.resultFreeResponse') }]
        : listeningResultLabels(listeningDetail.value.questions)
    case 'STORY_ACTIVITY':
      return storyDetail.value ? storyResultLabels(storyDetail.value.questions) : []
    default:
      return []
  }
})
const resultRows = computed(() =>
  result.value ? mapResultsToItems(result.value.results ?? [], resultLabels.value, result.value.passed) : [],
)

const activityOptions = computed(() => [
  {
    title: t('studentPractice.flashcards'),
    icon: 'file',
    tone: 'primary',
    active: selectedActivityType.value === 'FLASHCARD',
    action: () => openActivityType('FLASHCARD'),
  },
  {
    title: t('studentPractice.multipleChoice'),
    icon: 'quiz',
    tone: 'warning',
    active: false,
    action: openQuiz,
  },
  {
    title: t('studentPractice.matching'),
    icon: 'fun',
    tone: 'primary',
    active: selectedActivityType.value === 'MATCHING_GAME',
    action: () => openActivityType('MATCHING_GAME'),
  },
  {
    title: t('studentPractice.listening'),
    icon: 'live',
    tone: 'info',
    active: selectedActivityType.value === 'LISTENING_EXERCISE',
    action: () => openActivityType('LISTENING_EXERCISE'),
  },
  {
    title: t('studentPractice.speaking'),
    icon: 'moderation',
    tone: 'neutral',
    active: false,
    action: openAiHelper,
  },
  {
    title: t('studentPractice.characterWriting'),
    icon: 'clip',
    tone: 'accent',
    active: selectedActivityType.value === 'TRACING_EXERCISE',
    action: () => openActivityType('TRACING_EXERCISE'),
  },
  {
    title: t('studentPractice.dragDrop'),
    icon: 'practice',
    tone: 'success',
    active: selectedActivityType.value === 'DRAG_DROP_ACTIVITY',
    action: () => openActivityType('DRAG_DROP_ACTIVITY'),
  },
  {
    title: t('studentPractice.storyActivity'),
    icon: 'learn',
    tone: 'accent',
    active: selectedActivityType.value === 'STORY_ACTIVITY',
    action: () => openActivityType('STORY_ACTIVITY'),
  },
])

const quizAttemptCards = computed(() =>
  dashboard.value.recentQuizResults.map((quizResult) => {
    const scorePercent = ratioPercent(quizResult.score, quizResult.maxScore)

    return {
      id: quizResult.attemptId,
      title: quizResult.quizTitle,
      dateLabel: formatDate(quizResult.completedAt, { month: 'short', day: 'numeric', year: 'numeric' }),
      levelLabel: `${quizResult.score} / ${quizResult.maxScore}`,
      scoreLabel: `${scorePercent}%`,
      scoreTone: scoreTone(scorePercent),
      quizId: quizResult.quizId,
      icon: scoreIcon(scorePercent),
    }
  }),
)

const reviewCards = computed(() => {
  const directCards = wrongAnswers.value.slice(0, 3).map((item) => ({
    id: `wrong-${item.attemptId}-${item.questionId}`,
    kind: 'wrong-answer',
    label: t('studentPractice.question'),
    title: item.questionText,
    primary: t('studentPractice.yourAnswer', { answer: item.studentAnswer }),
    secondary: t('studentPractice.correctAnswer', { answer: item.correctAnswer }),
    visualIcon: 'quiz',
    visualTone: 'purple',
    cta: t('studentPractice.practiceAgain'),
    action: () => openQuizAttempt(item.quizId ?? null),
  }))

  const fillerCards = progress.value.weakAreas.slice(0, Math.max(0, 3 - directCards.length)).map((area, index) => {
    const activityType = inferPracticeActivityFromText(area)
    return {
      id: `focus-${index}-${area}`,
      kind: 'focus',
      label: t('studentPractice.focusArea'),
      title: area,
      primary: t('studentPractice.targetedHint'),
      secondary: t('studentPractice.reinforceHint'),
      visualIcon: activityIcon(activityType),
      visualTone: focusVisualTone(index),
      cta: t('studentPractice.openActivity', { activity: activityLabel(activityType) }),
      action: () => openPracticeRoute(activityType),
    }
  })

  return [...directCards, ...fillerCards]
})

function activityLabel(type: PracticeActivityType): string {
  const keys: Record<PracticeActivityType, string> = {
    FLASHCARD: 'flashcards',
    MATCHING_GAME: 'matching',
    LISTENING_EXERCISE: 'listening',
    TRACING_EXERCISE: 'characterWriting',
    DRAG_DROP_ACTIVITY: 'dragDrop',
    STORY_ACTIVITY: 'storyActivity',
  }
  return t(`studentPractice.${keys[type]}`)
}

function activityIcon(type: PracticeActivityType): string {
  switch (type) {
    case 'FLASHCARD':
      return 'file'
    case 'MATCHING_GAME':
      return 'fun'
    case 'LISTENING_EXERCISE':
      return 'live'
    case 'TRACING_EXERCISE':
      return 'clip'
    case 'DRAG_DROP_ACTIVITY':
      return 'practice'
    case 'STORY_ACTIVITY':
      return 'learn'
  }
}

function scoreTone(scorePercent: number): string {
  if (scorePercent >= 80) return 'is-green'
  return scorePercent >= 70 ? 'is-yellow' : 'is-red'
}

function scoreIcon(scorePercent: number): string {
  if (scorePercent >= 80) return 'status'
  return scorePercent >= 70 ? 'clip' : 'refresh'
}

function focusVisualTone(index: number): string {
  if (index === 0) return 'gold'
  return index === 1 ? 'peach' : 'mint'
}

watch(selectedFlashcardId, () => {
  resetAttempt()
  void flashcardResource.reload()
})

watch(
  requestedActivityType,
  (type) => {
    void selectActivityType(type)
  },
  { immediate: true },
)

watch(
  () => [
    activitiesResource.loading.value,
    activities.value.items.map((item) => `${item.type}:${item.id}`).join('|'),
    activitiesResource.usingFallback.value,
  ],
  () => {
    clampSelectedActivityIndexes()
    if (activitiesResource.loading.value || requestedActivityType.value === 'FLASHCARD') return
    if (hasLoadedActivityDetail(requestedActivityType.value)) return

    void selectActivityType(requestedActivityType.value)
  },
)

/** Activities of one type in scaffolding order: guided -> scaffolded -> independent -> not set (D23). */
function activitiesByType(type: PracticeActivityType): StudentActivitySummaryResponse[] {
  return sortByLearningStage(activities.value.items.filter((item) => item.type === type))
}

function selectedActivityForType(type: PracticeActivityType): StudentActivitySummaryResponse | null {
  const items = activitiesByType(type)
  if (!items.length) return null

  const index = Math.min(Math.max(selectedActivityIndexByType.value[type], 0), items.length - 1)
  return items[index] ?? null
}

function clampSelectedActivityIndexes(): void {
  ;(Object.keys(selectedActivityIndexByType.value) as PracticeActivityType[]).forEach((type) => {
    const maxIndex = Math.max(activitiesByType(type).length - 1, 0)
    selectedActivityIndexByType.value[type] = Math.min(Math.max(selectedActivityIndexByType.value[type], 0), maxIndex)
  })
}

function resetAttempt(): void {
  result.value = null
  resultPreview.value = false
  playerKey.value += 1
}

function resetActivityDetails(): void {
  matchingDetail.value = null
  listeningDetail.value = null
  tracingDetail.value = null
  dragDropDetail.value = null
  storyDetail.value = null
}

function previewFlashcardDetail(id: number | null): StudentFlashcardResponse | null {
  if (id === null) return null
  return flashcardActivities.find((activity) => activity.id === id) ?? flashcardActivities[0] ?? null
}

function applyPreviewActivityDetail(type: PracticeActivityType, id: number): void {
  if (type === 'MATCHING_GAME')
    matchingDetail.value = matchingActivities.find((activity) => activity.id === id) ?? matchingActivity
  if (type === 'LISTENING_EXERCISE')
    listeningDetail.value = listeningActivities.find((activity) => activity.id === id) ?? listeningActivity
  if (type === 'TRACING_EXERCISE')
    tracingDetail.value = tracingActivities.find((activity) => activity.id === id) ?? tracingActivity
  if (type === 'DRAG_DROP_ACTIVITY')
    dragDropDetail.value = dragDropActivities.find((activity) => activity.id === id) ?? dragDropActivity
  if (type === 'STORY_ACTIVITY')
    storyDetail.value = storyActivities.find((activity) => activity.id === id) ?? storyActivity
}

function hasLoadedActivityDetail(type: PracticeActivityType): boolean {
  if (type === 'MATCHING_GAME') return Boolean(matchingDetail.value)
  if (type === 'LISTENING_EXERCISE') return Boolean(listeningDetail.value)
  if (type === 'TRACING_EXERCISE') return Boolean(tracingDetail.value)
  if (type === 'DRAG_DROP_ACTIVITY') return Boolean(dragDropDetail.value)
  if (type === 'STORY_ACTIVITY') return Boolean(storyDetail.value)
  return true
}

function openActivityType(type: PracticeActivityType): void {
  if (normalizePracticeActivity(route.query.activity) === type) {
    void selectActivityType(type)
    return
  }

  void router.push(practiceRouteKeepingLesson(type))
}

/** Switching activity type keeps the lesson context (`lessonId`) of the breadcrumb. */
function practiceRouteKeepingLesson(type: PracticeActivityType) {
  const target = practiceRoute(type)
  const lessonId = contextLessonId.value
  if (!lessonId) return target
  return { ...target, query: { ...(target.query ?? {}), lessonId: String(lessonId) } }
}

async function selectActivityType(
  type: PracticeActivityType,
  options: { preserveNotice?: boolean } = {},
): Promise<void> {
  selectedActivityType.value = type
  if (!options.preserveNotice) {
    actionError.value = null
    actionSuccess.value = null
  }
  resetAttempt()
  resetActivityDetails()
  // Every selection supersedes a detail request that is still in flight (see the sequence checks below).
  const sequence = ++activityDetailSequence
  loadingActivity.value = false

  if (type === 'FLASHCARD') return

  const activity = selectedActivityForType(type)
  if (!activity) {
    if (activitiesResource.loading.value || !activitiesResource.hasData.value) return

    actionError.value = t('studentPractice.unavailableInLibrary', { activity: activityLabel(type) })
    return
  }

  loadingActivity.value = true
  try {
    if (activitiesResource.usingFallback.value) {
      applyPreviewActivityDetail(type, activity.id)
      if (!options.preserveNotice) {
        actionSuccess.value = t('studentPractice.previewLoaded')
      }
      return
    }

    // A slower response for an activity the learner has already left must not replace the current one.
    if (type === 'MATCHING_GAME') {
      const detail = await studentApi.matchingGame(activity.id)
      if (sequence === activityDetailSequence) matchingDetail.value = detail
    }
    if (type === 'LISTENING_EXERCISE') {
      const detail = await studentApi.listeningExercise(activity.id)
      if (sequence === activityDetailSequence) listeningDetail.value = detail
    }
    if (type === 'TRACING_EXERCISE') {
      const detail = await studentApi.tracingExercise(activity.id)
      if (sequence === activityDetailSequence) tracingDetail.value = detail
    }
    if (type === 'DRAG_DROP_ACTIVITY') {
      const detail = await studentApi.dragDropActivity(activity.id)
      if (sequence === activityDetailSequence) dragDropDetail.value = detail
    }
    if (type === 'STORY_ACTIVITY') {
      const detail = await studentApi.storyActivity(activity.id)
      if (sequence === activityDetailSequence) storyDetail.value = detail
    }
  } catch (reason) {
    if (sequence === activityDetailSequence) {
      actionError.value = resolveApiError(reason, t('studentPractice.loadError', { activity: activityLabel(type) }))
    }
  } finally {
    if (sequence === activityDetailSequence) loadingActivity.value = false
  }
}

function moveActivityLevel(type: PracticeActivityType, direction: 1 | -1): boolean {
  const items = activitiesByType(type)
  if (items.length < 2) return false

  const currentIndex = selectedActivityIndexByType.value[type]
  selectedActivityIndexByType.value[type] = (currentIndex + direction + items.length) % items.length
  return true
}

function goToActivityLevel(direction: 1 | -1): void {
  if (submittingActivity.value) return
  actionError.value = null
  actionSuccess.value = null
  if (!moveActivityLevel(selectedActivityType.value, direction)) return

  // Flashcards reload through the selectedFlashcardId watcher; other types load their detail here.
  if (selectedActivityType.value === 'FLASHCARD') {
    resetAttempt()
    return
  }

  void selectActivityType(selectedActivityType.value)
}

function goToPreviousActivityLevel(): void {
  goToActivityLevel(-1)
}

function goToNextActivityLevel(): void {
  goToActivityLevel(1)
}

function sendCompletion(id: number, submission: ActivitySubmission): Promise<StudentActivityCompletionResponse> {
  switch (submission.type) {
    case 'FLASHCARD':
      return studentApi.completeFlashcard(id)
    case 'MATCHING_GAME':
      return studentApi.completeMatchingGame(id, submission.body)
    case 'DRAG_DROP_ACTIVITY':
      return studentApi.completeDragDropActivity(id, submission.body)
    case 'LISTENING_EXERCISE':
      return studentApi.completeListeningExercise(id, submission.body)
    case 'STORY_ACTIVITY':
      return studentApi.completeStoryActivity(id, submission.body)
    case 'TRACING_EXERCISE':
      return studentApi.completeTracingExercise(id, submission.body)
  }
}

async function refreshPracticeProgress(type: PracticeActivityType): Promise<void> {
  await Promise.all([
    activitiesResource.reload(),
    dashboardResource.reload(),
    progressResource.reload(),
    type === 'FLASHCARD' ? flashcardResource.reload() : Promise.resolve(),
  ])
}

async function submitActivity(submission: ActivitySubmission): Promise<void> {
  const type = selectedActivityType.value
  const activityId = currentDetail.value?.id ?? null
  if (submittingActivity.value || result.value || activityId === null || submission.type !== type) return

  actionError.value = null
  actionSuccess.value = null

  if (previewMode.value) {
    const graded = gradeDemoActivity(demoActivitySources, activityId, submission)
    if (!graded) {
      actionError.value = t('studentPractice.previewGradeUnavailable')
      return
    }
    resultPreview.value = true
    result.value = graded
    return
  }

  submittingActivity.value = true
  const attemptKey = playerKey.value
  try {
    const response = await sendCompletion(activityId, submission)
    // The learner may have switched activity while the answer was being saved; keep that player unlocked.
    if (attemptKey === playerKey.value) {
      resultPreview.value = false
      result.value = response
    }
    if (response.passed) await refreshPracticeProgress(type)
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentPractice.completeError', { activity: activityLabel(type) }))
  } finally {
    submittingActivity.value = false
  }
}

function tryAgain(): void {
  actionError.value = null
  actionSuccess.value = null
  resetAttempt()
}

function openQuizAttempt(quizId: number | null): void {
  if (!quizId) {
    openQuiz()
    return
  }

  void router.push({
    name: 'student-quiz',
    params: { quizId: String(quizId) },
  })
}

function reloadAll(): void {
  void activitiesResource.reload()
  void wrongAnswersResource.reload()
  void flashcardResource.reload()
  void dashboardResource.reload()
  void progressResource.reload()
}

function openQuiz(): void {
  void router.push('/student/quiz')
}

function openAiHelper(): void {
  void router.push('/student/ai-helper')
}

function openPracticeRoute(type: PracticeActivityType): void {
  void router.push(practiceRouteKeepingLesson(type))
}
</script>

<style scoped>
.student-practice-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 340px);
  gap: var(--page-gap);
  align-items: start;
}

.student-practice-main,
.student-practice-side {
  display: grid;
  gap: var(--page-gap);
  align-content: start;
  min-width: 0;
}

.student-practice-card {
  display: grid;
  gap: var(--space-4);
  align-content: start;
}

.student-practice-card-header {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  min-width: 0;
}

.student-practice-card-header > .card-title,
.student-practice-card-heading {
  flex: 1 1 0;
  min-width: 0;
  align-self: center;
  overflow-wrap: anywhere;
}

.student-practice-card-header > .btn {
  flex: 0 0 auto;
  align-self: center;
  white-space: nowrap;
}

.student-practice-review-count {
  margin-top: var(--space-2);
}

.student-practice-head-icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

/* Shared icon tile tones */
.is-primary {
  --tile-bg: var(--color-primary-50);
  --tile-fg: var(--color-primary-600);
}

.is-success {
  --tile-bg: var(--color-success-soft);
  --tile-fg: var(--color-success-text);
}

.is-info {
  --tile-bg: var(--color-info-soft);
  --tile-fg: var(--color-info-text);
}

.is-warning {
  --tile-bg: var(--color-warning-soft);
  --tile-fg: var(--color-warning-text);
}

.is-accent {
  --tile-bg: var(--color-accent-100);
  --tile-fg: var(--color-accent-800);
}

.is-neutral {
  --tile-bg: var(--color-ink-100);
  --tile-fg: var(--color-ink-700);
}

/* Activity selector */
.student-practice-selector {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(84px, 1fr));
  gap: var(--space-2);
  padding: var(--space-3);
}

.student-practice-option {
  display: grid;
  justify-items: center;
  align-content: start;
  gap: var(--space-2);
  min-width: 0;
  min-height: 96px;
  padding: var(--space-3) var(--space-2);
  border: 1px solid transparent;
  border-radius: var(--radius-lg);
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  line-height: var(--line-height-snug);
  text-align: center;
  cursor: pointer;
  transition:
    background var(--duration-fast) var(--ease-standard),
    border-color var(--duration-fast) var(--ease-standard),
    color var(--duration-fast) var(--ease-standard);
}

.student-practice-option:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}

.student-practice-option.is-active {
  border-color: var(--color-primary-300);
  background: var(--color-primary-50);
  color: var(--text-primary);
  box-shadow: inset 0 -3px 0 var(--color-primary-500);
}

.student-practice-option-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  background: var(--tile-bg);
  color: var(--tile-fg);
}

.student-practice-option-label {
  min-width: 0;
  overflow-wrap: break-word;
}

/* Stage: header band, activity card, level footer */
.student-practice-stage {
  display: grid;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-2xl);
  background: var(--surface-card);
  box-shadow: var(--shadow-sm);
}

.student-practice-stage-top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4) var(--space-6);
  padding: var(--space-6);
  color: var(--text-on-primary);
  background:
    radial-gradient(circle at 95% 0%, var(--color-accent-a48), transparent 30%),
    linear-gradient(135deg, var(--color-primary-700), var(--color-primary-500));
}

.student-practice-stage-copy {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.student-practice-stage-copy h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
  overflow-wrap: anywhere;
}

.student-practice-stage-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.student-practice-level {
  border: 1px solid var(--color-white-a32);
  background: var(--surface-on-brand);
  color: var(--text-on-primary);
}

.student-practice-level:hover:not(:disabled) {
  background: var(--surface-on-brand-hover);
}

.student-practice-stage-stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin: 0;
}

.student-practice-stat {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 0 var(--space-3);
  min-width: 160px;
  padding: var(--space-2-5) var(--space-4);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  color: var(--text-primary);
  box-shadow: var(--shadow-sm);
}

.student-practice-stat-icon {
  grid-row: span 2;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-round);
  background: var(--tile-bg);
  color: var(--tile-fg);
}

.student-practice-stat dt {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.student-practice-stat dd {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.student-practice-stage-body {
  padding: var(--space-6);
  background: linear-gradient(180deg, var(--color-primary-50), var(--surface-subtle));
}

.student-practice-activity-card {
  display: grid;
  gap: var(--space-4);
  max-width: 760px;
  margin-inline: auto;
  padding: var(--space-6);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-card);
  box-shadow: var(--shadow-md);
}

.student-practice-card-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.student-practice-card-badge {
  color: var(--text-brand);
  font-size: var(--type-overline-size);
  font-weight: var(--font-weight-extrabold);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
}

.student-practice-card-status {
  padding: var(--space-0-5) var(--space-2-5);
  border-radius: var(--radius-pill);
  background: var(--color-primary-50);
  color: var(--color-primary-700);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.student-practice-card-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-snug);
  overflow-wrap: anywhere;
}

.student-practice-card-instructions {
  margin: 0;
  color: var(--text-secondary);
  line-height: var(--line-height-normal);
}

.student-practice-card-loading {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  color: var(--text-secondary);
}

.student-practice-stage-footer {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--border-subtle);
}

.student-practice-footer-status {
  display: grid;
  justify-items: center;
  text-align: center;
}

.student-practice-footer-status strong {
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.student-practice-footer-status small {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

/* Review wrong answers */
.student-review-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 220px), 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-review-card {
  display: grid;
  grid-template-rows: auto auto auto 1fr auto;
  gap: var(--space-2);
  min-width: 0;
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-top: 3px solid var(--color-accent-400);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
  overflow-wrap: anywhere;
}

.student-review-card.is-wrong-answer {
  border-top-color: var(--color-danger);
}

.student-review-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.student-review-label {
  color: var(--text-brand);
  font-size: var(--type-overline-size);
  font-weight: var(--font-weight-extrabold);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
}

.student-review-card.is-wrong-answer .student-review-label {
  color: var(--color-danger-text);
}

.student-review-visual {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
}

.student-review-visual.is-purple {
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.student-review-visual.is-gold {
  background: var(--color-accent-100);
  color: var(--color-accent-800);
}

.student-review-visual.is-peach {
  background: var(--color-warning-soft);
  color: var(--color-warning-text);
}

.student-review-visual.is-mint {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.student-review-title {
  font-size: var(--font-size-base);
  line-height: var(--line-height-snug);
}

.student-review-card p {
  margin: 0;
  font-size: var(--font-size-sm);
}

.student-review-card small {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.student-review-card > .btn {
  justify-self: start;
  margin-top: var(--space-1);
}

/* Side */
.student-practice-side-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin: 0;
}

.student-practice-side-stats > div {
  display: grid;
  align-content: space-between;
  gap: var(--space-1);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
}

.student-practice-side-stats dt {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.student-practice-side-stats dd {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
}

.student-practice-library {
  display: grid;
  gap: var(--space-2);
}

.student-practice-library-head {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.student-practice-attempts {
  display: grid;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-practice-attempt {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2) var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
}

.student-practice-attempt-icon {
  grid-row: span 2;
  align-self: start;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  background: var(--surface-card);
  color: var(--text-secondary);
}

.student-practice-attempt-copy {
  display: grid;
  min-width: 0;
}

.student-practice-attempt-copy strong {
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.student-practice-attempt-copy span {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.student-practice-attempt-score {
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.student-practice-attempt-button {
  grid-column: 2 / -1;
  justify-self: start;
}

.student-practice-attempt-icon.is-green,
.student-practice-attempt-score.is-green {
  color: var(--color-success-text);
}

.student-practice-attempt-icon.is-yellow,
.student-practice-attempt-score.is-yellow {
  color: var(--color-warning-text);
}

.student-practice-attempt-icon.is-red,
.student-practice-attempt-score.is-red {
  color: var(--color-danger-text);
}

@media (max-width: 1200px) {
  .student-practice-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-practice-side {
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr));
    align-items: start;
  }
}

@media (max-width: 640px) {
  .student-practice-selector {
    grid-template-columns: repeat(auto-fit, minmax(72px, 1fr));
  }

  .student-practice-option {
    padding-inline: var(--space-1);
    font-size: var(--font-size-xs);
  }

  .student-practice-stage {
    border-radius: var(--radius-xl);
  }

  .student-practice-stage-top {
    padding: var(--space-5) var(--space-4);
  }

  .student-practice-stage-stats,
  .student-practice-stat {
    width: 100%;
  }

  .student-practice-stage-body {
    padding: var(--space-3);
  }

  .student-practice-activity-card {
    padding: var(--space-4);
  }

  .student-practice-stage-footer {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    padding: var(--space-4);
  }

  .student-practice-footer-status {
    grid-column: 1 / -1;
    grid-row: 1;
  }
}
</style>
