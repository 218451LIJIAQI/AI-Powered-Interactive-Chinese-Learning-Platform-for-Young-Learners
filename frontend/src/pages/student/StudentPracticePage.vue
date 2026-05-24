<template>
  <div class="page-stack student-practice-page">
    <DataNotice :using-fallback="usingFallback" :error="actionError || pageError" :success="actionSuccess" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="student-practice-layout">
        <div class="student-practice-main">
          <article class="panel student-practice-selector">
            <button
              v-for="option in activityOptions"
              :key="option.title"
              type="button"
              class="student-practice-option"
              :class="{ 'is-active': option.active }"
              @click="option.action"
            >
              <span class="student-practice-option-icon" :style="{ '--option-tint': option.tint }">
                <IconGlyph :name="option.icon" :size="34" />
              </span>
              <strong>{{ option.title }}</strong>
            </button>
          </article>

          <article class="student-practice-stage">
            <span class="student-practice-stage-glow" />
            <span class="student-practice-stage-cloud is-left" />
            <span class="student-practice-stage-cloud is-right" />

            <div class="student-practice-stage-top">
              <div class="student-practice-stage-copy">
                <div class="row">
                  <h2>{{ selectedStageTitle }}</h2>
                  <span class="student-practice-info"><IconGlyph name="help" :size="14" /></span>
                </div>
                <button type="button" class="student-practice-level" @click="selectedActivityType === 'FLASHCARD' ? router.push('/student/learn') : selectActivityType('FLASHCARD')">
                  {{ selectedStageLevelLabel }}
                  <span><IconGlyph name="chevronDown" :size="16" /></span>
                </button>
              </div>

              <div class="student-practice-stage-stats">
                <div class="student-practice-pill">
                  <span class="student-practice-pill-icon is-gold"><IconGlyph name="star" :size="20" /></span>
                  <div>
                    <small>Points Earned</small>
                    <strong>{{ formatNumber(dashboard.points) }} XP</strong>
                  </div>
                </div>

                <div class="student-practice-pill">
                  <span class="student-practice-pill-icon is-green"><IconGlyph name="status" :size="20" /></span>
                  <div>
                    <small>Library Complete</small>
                    <strong>{{ activityCompletionLabel }}</strong>
                  </div>
                </div>
              </div>
            </div>

            <div class="student-practice-stage-body">
              <span class="student-practice-hill is-back" />
              <span class="student-practice-hill is-front" />
              <span class="student-practice-pagoda" />
              <span class="student-practice-flower is-left" />
              <span class="student-practice-flower is-right" />

              <div v-if="selectedActivityType === 'FLASHCARD'" class="student-practice-flashcard">
                <span class="student-practice-flashcard-badge">{{ flashcard.topic || 'Vocabulary' }}</span>
                <strong>{{ flashcard.frontText }}</strong>
                <span>{{ flashcard.pinyin || 'Pinyin unavailable' }}</span>
                <b>{{ translationLine }}</b>
              </div>

              <div v-else class="student-practice-activity-card">
                <span class="student-practice-flashcard-badge">{{ selectedActivityBadge }}</span>
                <strong>{{ selectedActivityTitle }}</strong>
                <span>{{ selectedActivityInstructions }}</span>

                <div v-if="loadingActivity" class="student-practice-activity-list">
                  <small>Loading activity details...</small>
                </div>

                <div v-else-if="matchingDetail" class="student-practice-activity-list">
                  <small v-for="item in matchingDetail.items" :key="item.id">{{ item.leftText }} to {{ item.rightText }}</small>
                </div>

                <div v-else-if="listeningDetail" class="student-practice-activity-list">
                  <small>{{ listeningDetail.questionPayload || 'Listen to the audio prompt, then complete the exercise.' }}</small>
                  <a v-if="safeListeningAudioUrl" :href="safeListeningAudioUrl" target="_blank" rel="noopener noreferrer">Open audio</a>
                </div>

                <div v-else-if="tracingDetail" class="student-practice-activity-list">
                  <small>{{ tracingDetail.targetCharacter }} - {{ tracingDetail.pinyin || 'Pinyin unavailable' }}</small>
                  <small>{{ tracingDetail.meaning || tracingDetail.instructions || 'Trace the character carefully.' }}</small>
                  <a v-if="safeTracingWorksheetUrl" :href="safeTracingWorksheetUrl" target="_blank" rel="noopener noreferrer">Open worksheet</a>
                </div>

                <div v-else-if="dragDropDetail" class="student-practice-activity-list">
                  <small v-for="item in dragDropDetail.items" :key="item.id">{{ item.dragText }} to {{ item.targetZone }}</small>
                </div>

                <div v-else-if="storyDetail" class="student-practice-activity-list">
                  <small>{{ storyDetail.storyText }}</small>
                  <small v-for="question in storyDetail.questions" :key="question.id">
                    {{ question.questionText }}{{ question.explanation ? ` - ${question.explanation}` : '' }}
                  </small>
                </div>
              </div>

              <div v-if="selectedActivityType === 'FLASHCARD'" class="student-practice-side-actions">
                <button
                  type="button"
                  class="student-practice-circle"
                  :disabled="!flashcardAvailable"
                  aria-label="Play flashcard pronunciation"
                  @click="playCardPronunciation"
                >
                  <IconGlyph name="live" :size="20" />
                </button>
                <button
                  type="button"
                  class="student-practice-circle"
                  :disabled="!flashcardAvailable"
                  :aria-label="showMalayMeaning ? 'Show English meaning' : 'Show Malay meaning'"
                  @click="toggleTranslation"
                >
                  {{ showMalayMeaning ? 'EN' : 'BM' }}
                </button>
              </div>
            </div>

            <div class="student-practice-stage-footer">
              <template v-if="selectedActivityType === 'FLASHCARD'">
                <button type="button" class="student-practice-answer is-soft-red" :disabled="!flashcardAvailable" @click="markFlashcardDone(false)">
                  Keep practicing
                </button>

                <strong>{{ flashcardProgressLabel }}</strong>

                <button type="button" class="student-practice-answer is-soft-green" :disabled="!flashcardAvailable" @click="markFlashcardDone(true)">
                  I know this
                </button>
              </template>

              <template v-else>
                <button type="button" class="student-practice-answer is-soft-red" @click="selectActivityType('FLASHCARD')">
                  Back to cards
                </button>

                <strong>{{ selectedActivityProgressLabel }}</strong>

                <button type="button" class="student-practice-answer is-soft-green" :disabled="submittingActivity" @click="completeSelectedActivity">
                  {{ submittingActivity ? 'Saving...' : 'Complete Activity' }}
                </button>
              </template>
            </div>
          </article>

          <article class="panel student-review-panel">
            <div class="row-between student-review-head">
              <div class="student-section-heading">
                <span class="student-review-alert"><IconGlyph name="alert" :size="14" /></span>
                <div>
                  <strong>Review Wrong Answers</strong>
                  <p>We highlight real mistakes first, then fill the strip with weak-area review when needed.</p>
                </div>
              </div>
              <span class="student-review-count">{{ reviewCards.length }} items to review</span>
            </div>

            <div class="student-review-grid">
              <article
                v-for="card in reviewCards"
                :key="card.id"
                class="student-review-card"
                :class="`is-${card.kind}`"
              >
                <div class="student-review-copy">
                  <span class="student-review-label">{{ card.label }}</span>
                  <strong>{{ card.title }}</strong>
                  <p>{{ card.primary }}</p>
                  <small>{{ card.secondary }}</small>
                </div>

                <div class="student-review-visual" :class="`is-${card.visualTone}`">
                  {{ card.visual }}
                </div>

                <button type="button" class="student-review-button" @click="card.action">
                  {{ card.cta }}
                </button>
              </article>
            </div>
          </article>
        </div>

        <aside class="student-practice-side">
          <article class="panel student-practice-progress-card">
            <div class="student-section-heading">
              <span class="student-practice-progress-icon"><IconGlyph name="progress" :size="18" /></span>
              <strong>Your Practice Progress</strong>
            </div>

            <div class="student-practice-progress-top">
              <ProgressRing :progress="practiceProgress" title="Overall progress" subtitle="Keep building confidence!" />
            </div>

            <div class="student-practice-stat-row">
              <div>
                <small>Activities Completed</small>
                <strong>{{ completedActivities }}</strong>
              </div>
              <div>
                <small>Available Practice Time</small>
                <strong>{{ totalPracticeTime }}</strong>
              </div>
              <div>
                <small>Accuracy</small>
                <strong>{{ accuracyLabel }}</strong>
              </div>
            </div>

            <div class="student-practice-weekly">
              <div class="row-between">
                <strong>Activity Library</strong>
                <span>{{ activityCompletionLabel }}</span>
              </div>
              <ProgressBar :progress="activityCompletionProgress" />
            </div>
          </article>

          <article class="panel student-practice-attempts-card">
            <div class="row-between">
              <div class="student-section-heading">
                <span class="student-practice-progress-icon"><IconGlyph name="quiz" :size="18" /></span>
                <strong>Recent Quiz Attempts</strong>
              </div>
              <button type="button" class="student-link-button" @click="router.push('/student/quiz')">View All</button>
            </div>

            <div class="student-practice-attempts">
              <article v-for="attempt in quizAttemptCards" :key="attempt.id" class="student-practice-attempt">
                <span class="student-practice-attempt-icon"><IconGlyph :name="attempt.icon" :size="18" /></span>

                <div class="student-practice-attempt-copy">
                  <strong>{{ attempt.title }}</strong>
                  <span>{{ attempt.dateLabel }} - {{ attempt.levelLabel }}</span>
                </div>

                <strong class="student-practice-attempt-score" :class="attempt.scoreTone">{{ attempt.scoreLabel }}</strong>

                <button type="button" class="student-practice-attempt-button" @click="openQuizAttempt(attempt.quizId)">
                  Review
                </button>
              </article>
            </div>
          </article>
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>
<script setup lang="ts">
/**
 * Practice studio surface — six interactive activity types
 * (flashcards, matching, listening, tracing, drag-drop, story)
 * curated by admins, surfaced to the right age band based on the
 * student's profile. Completion writes through to the streak / XP
 * pipeline without advancing lesson progress (platform-library
 * decoupling).
 *
 * <p>The page intentionally re-uses the {@link StatePanel} empty
 * / loading components so the UI stays consistent with other
 * student dashboards.</p>
 */
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import ProgressRing from '@/components/common/ProgressRing.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useResource } from '@/composables/useResource'
import {
  activityLibrary,
  dragDropActivity,
  flashcardActivity,
  listeningActivity,
  matchingActivity,
  studentDashboard as studentDashboardDemo,
  studentProgress as studentProgressDemo,
  studentWrongAnswers,
  storyActivity,
  tracingActivity,
} from '@/demo/data'
import type {
  StudentActivityCompletionResponse,
  StudentActivitySummaryResponse,
  StudentDragDropActivityResponse,
  StudentListeningExerciseResponse,
  StudentMatchingGameResponse,
  StudentStoryActivityResponse,
  StudentTracingExerciseResponse,
} from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatDate, formatNumber } from '@/utils/format'
import { safeHttpUrl } from '@/utils/url'

const router = useRouter()
type PracticeActivityType = 'FLASHCARD' | 'MATCHING_GAME' | 'LISTENING_EXERCISE' | 'TRACING_EXERCISE' | 'DRAG_DROP_ACTIVITY' | 'STORY_ACTIVITY'

const activitiesResource = useResource(() => studentApi.activityLibrary({ size: 20 }), activityLibrary)
const wrongAnswersResource = useResource(() => studentApi.wrongAnswers(), studentWrongAnswers)
const dashboardResource = useResource(() => studentApi.dashboard(), studentDashboardDemo)
const progressResource = useResource(() => studentApi.progress(), studentProgressDemo)

const showMalayMeaning = ref(false)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const selectedActivityType = ref<PracticeActivityType>('FLASHCARD')
const loadingActivity = ref(false)
const submittingActivity = ref(false)
const matchingDetail = ref<StudentMatchingGameResponse | null>(null)
const listeningDetail = ref<StudentListeningExerciseResponse | null>(null)
const tracingDetail = ref<StudentTracingExerciseResponse | null>(null)
const dragDropDetail = ref<StudentDragDropActivityResponse | null>(null)
const storyDetail = ref<StudentStoryActivityResponse | null>(null)
const safeListeningAudioUrl = computed(() => safeHttpUrl(listeningDetail.value?.audioUrl))
const safeTracingWorksheetUrl = computed(() => safeHttpUrl(tracingDetail.value?.worksheetUrl))

const activities = computed(() => activitiesResource.data.value ?? (activitiesResource.usingFallback.value ? activityLibrary : { items: [], page: 0, size: 0, totalItems: 0, totalPages: 0 }))
const wrongAnswers = computed(() => wrongAnswersResource.data.value ?? (wrongAnswersResource.usingFallback.value ? studentWrongAnswers : []))
const emptyFlashcard = { ...flashcardActivity, topic: 'Vocabulary', frontText: 'Unavailable', backText: 'Unavailable', completed: false }
const emptyDashboard = { ...studentDashboardDemo, points: 0, recentQuizResults: [], unfinishedLessons: [], weakAreas: [] }
const emptyProgress = { ...studentProgressDemo, quizAverage: 0, overallCompletionPercent: 0, weakAreas: [], recentLessons: [] }
const selectedFlashcardId = computed(
  () => activities.value.items.find((item) => item.type === 'FLASHCARD')?.id ?? null,
)
const flashcardResource = useResource(
  () => (selectedFlashcardId.value ? studentApi.flashcard(selectedFlashcardId.value) : Promise.resolve(emptyFlashcard)),
  flashcardActivity,
)
const flashcard = computed(() => flashcardResource.data.value ?? (flashcardResource.usingFallback.value ? flashcardActivity : emptyFlashcard))
const dashboard = computed(() => dashboardResource.data.value ?? (dashboardResource.usingFallback.value ? studentDashboardDemo : emptyDashboard))
const progress = computed(() => progressResource.data.value ?? (progressResource.usingFallback.value ? studentProgressDemo : emptyProgress))

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

const totalPracticeItems = computed(() => Math.max(activities.value.items.length, 1))
const completedActivities = computed(() => activities.value.items.filter((item) => item.completed).length)
const totalPracticeMinutes = computed(() =>
  Math.round(
    activities.value.items.reduce((sum, item) => sum + (item.estimatedSeconds ?? 0), 0) / 60,
  ),
)
const totalPracticeTime = computed(() => {
  const hours = Math.floor(totalPracticeMinutes.value / 60)
  const minutes = totalPracticeMinutes.value % 60

  if (hours > 0) {
    return `${hours}h ${minutes}m`
  }

  return `${totalPracticeMinutes.value}m`
})

const accuracy = computed(() => progress.value.quizAverage)
const accuracyLabel = computed(() => `${Math.round(accuracy.value)}%`)
const activityCompletionProgress = computed(() => Math.round((completedActivities.value / totalPracticeItems.value) * 100))
const activityCompletionLabel = computed(() => `${completedActivities.value} / ${totalPracticeItems.value}`)
const practiceProgress = computed(() => Math.round(progress.value.overallCompletionPercent || activityCompletionProgress.value))
const flashcardAvailable = computed(() => Boolean(flashcard.value.id))
const flashcardProgressLabel = computed(() => {
  if (!flashcardAvailable.value) return 'No flashcard available'
  return flashcard.value.completed ? 'Flashcard completed' : 'Flashcard review'
})

const flashcardLevelLabel = computed(() => `Level ${flashcard.value.hskLevel ?? 1}`)
const selectedActivity = computed(() =>
  selectedActivityType.value === 'FLASHCARD' ? null : findActivityByType(selectedActivityType.value),
)
const selectedStageTitle = computed(() => {
  if (selectedActivityType.value === 'FLASHCARD') return 'Flashcards'
  return selectedActivity.value?.title ?? activityLabel(selectedActivityType.value)
})
const selectedStageLevelLabel = computed(() => {
  if (selectedActivityType.value === 'FLASHCARD') return flashcardLevelLabel.value
  return selectedActivity.value?.hskLevel ? `Level ${selectedActivity.value.hskLevel}` : activityLabel(selectedActivityType.value)
})
const selectedActivityBadge = computed(() => selectedActivity.value?.topic || activityLabel(selectedActivityType.value))
const selectedActivityTitle = computed(() => {
  if (matchingDetail.value) return matchingDetail.value.title
  if (listeningDetail.value) return listeningDetail.value.title
  if (tracingDetail.value) return tracingDetail.value.title
  if (dragDropDetail.value) return dragDropDetail.value.title
  if (storyDetail.value) return storyDetail.value.title
  return selectedActivity.value?.title ?? activityLabel(selectedActivityType.value)
})
const selectedActivityInstructions = computed(() => {
  if (tracingDetail.value) return tracingDetail.value.instructions || 'Trace the target character and compare your shape.'
  if (dragDropDetail.value) return dragDropDetail.value.instructions || 'Arrange the items in the correct order.'
  if (storyDetail.value) return storyDetail.value.instructions || 'Read the story and answer the questions.'
  if (listeningDetail.value) return 'Listen to the prompt and choose the matching answer.'
  if (matchingDetail.value) return 'Review the matching pairs, then complete the activity.'
  return selectedActivity.value ? 'Load and complete this backend activity from the practice library.' : 'No activity is available for this type yet.'
})
const selectedActivityProgressLabel = computed(() => {
  if (!selectedActivity.value) return 'Unavailable'
  return selectedActivity.value.completed ? 'Completed' : `${selectedActivity.value.xpReward ?? 0} XP`
})
const translationLine = computed(() => {
  if (showMalayMeaning.value) {
    return flashcard.value.malayMeaning || flashcard.value.backText
  }

  return flashcard.value.englishMeaning || flashcard.value.backText
})

const activityOptions = computed(() => [
  { title: 'Flashcards', icon: 'file', tint: '#7c5bff', active: selectedActivityType.value === 'FLASHCARD', action: () => selectActivityType('FLASHCARD') },
  { title: 'Multiple-Choice Quiz', icon: 'quiz', tint: '#ffad50', active: false, action: () => router.push('/student/quiz') },
  { title: 'Matching', icon: 'fun', tint: '#6e67ff', active: selectedActivityType.value === 'MATCHING_GAME', action: () => selectActivityType('MATCHING_GAME') },
  { title: 'Listening', icon: 'live', tint: '#23b8a7', active: selectedActivityType.value === 'LISTENING_EXERCISE', action: () => selectActivityType('LISTENING_EXERCISE') },
  { title: 'Speaking', icon: 'moderation', tint: '#6573a7', active: false, action: () => router.push('/student/ai-helper') },
  { title: 'Character Writing', icon: 'clip', tint: '#f4a03c', active: selectedActivityType.value === 'TRACING_EXERCISE', action: () => selectActivityType('TRACING_EXERCISE') },
  { title: 'Drag-and-Drop', icon: 'practice', tint: '#7bc99b', active: selectedActivityType.value === 'DRAG_DROP_ACTIVITY', action: () => selectActivityType('DRAG_DROP_ACTIVITY') },
  { title: 'Story Activity', icon: 'learn', tint: '#ffcb65', active: selectedActivityType.value === 'STORY_ACTIVITY', action: () => selectActivityType('STORY_ACTIVITY') },
])

const quizAttemptCards = computed(() =>
  dashboard.value.recentQuizResults.map((result) => {
    const scorePercent = result.maxScore > 0 ? Math.round((result.score / result.maxScore) * 100) : 0

    return {
      id: result.attemptId,
      title: result.quizTitle,
      dateLabel: formatDate(result.completedAt, { month: 'short', day: 'numeric', year: 'numeric' }),
      levelLabel: `${result.score} / ${result.maxScore}`,
      scoreLabel: `${scorePercent}%`,
      scoreTone: scorePercent >= 80 ? 'is-green' : scorePercent >= 70 ? 'is-yellow' : 'is-red',
      quizId: result.quizId,
      icon: scorePercent >= 80 ? 'status' : scorePercent >= 70 ? 'clip' : 'refresh',
    }
  }),
)

const reviewCards = computed(() => {
  const directCards = wrongAnswers.value.slice(0, 3).map((item) => ({
    id: `wrong-${item.attemptId}-${item.questionId}`,
    kind: 'wrong-answer',
    label: 'Question',
    title: item.questionText,
    primary: `Your answer: ${item.studentAnswer}`,
    secondary: `Correct answer: ${item.correctAnswer}`,
    visual: item.correctAnswer.slice(0, 2) || 'Word',
    visualTone: 'purple',
    cta: 'Practice Again',
    action: () => router.push('/student/quiz'),
  }))

  const fillerCards = progress.value.weakAreas
    .slice(0, Math.max(0, 3 - directCards.length))
    .map((area, index) => ({
      id: `focus-${index}-${area}`,
      kind: 'focus',
      label: 'Focus Area',
      title: area,
      primary: 'Targeted practice is suggested because your recent progress shows this area needs extra review.',
      secondary: 'Open practice mode to reinforce vocabulary, listening, or character recall.',
      visual: index === 0 ? 'Tone' : index === 1 ? 'Word' : 'Listen',
      visualTone: index === 0 ? 'gold' : index === 1 ? 'peach' : 'mint',
      cta: 'Open Practice',
      action: () => router.push('/student/practice'),
    }))

  return [...directCards, ...fillerCards]
})
function activityLabel(type: PracticeActivityType) {
  return type.replaceAll('_', ' ').toLowerCase().replace(/(^|\s)\w/g, (match) => match.toUpperCase())
}

watch(selectedFlashcardId, () => {
  void flashcardResource.reload()
})

function findActivityByType(type: PracticeActivityType): StudentActivitySummaryResponse | null {
  return activities.value.items.find((item) => item.type === type) ?? null
}

function resetActivityDetails() {
  matchingDetail.value = null
  listeningDetail.value = null
  tracingDetail.value = null
  dragDropDetail.value = null
  storyDetail.value = null
}

function applyPreviewActivityDetail(type: PracticeActivityType) {
  if (type === 'MATCHING_GAME') matchingDetail.value = matchingActivity
  if (type === 'LISTENING_EXERCISE') listeningDetail.value = listeningActivity
  if (type === 'TRACING_EXERCISE') tracingDetail.value = tracingActivity
  if (type === 'DRAG_DROP_ACTIVITY') dragDropDetail.value = dragDropActivity
  if (type === 'STORY_ACTIVITY') storyDetail.value = storyActivity
}

async function selectActivityType(type: PracticeActivityType) {
  selectedActivityType.value = type
  actionError.value = null
  actionSuccess.value = null
  resetActivityDetails()

  if (type === 'FLASHCARD') return

  const activity = findActivityByType(type)
  if (!activity) {
    actionError.value = `${activityLabel(type)} is not available in your activity library yet.`
    return
  }

  loadingActivity.value = true
  try {
    if (activitiesResource.usingFallback.value) {
      applyPreviewActivityDetail(type)
      actionSuccess.value = 'Preview activity loaded. Sign in with live backend data to save completion progress.'
      return
    }

    if (type === 'MATCHING_GAME') matchingDetail.value = await studentApi.matchingGame(activity.id)
    if (type === 'LISTENING_EXERCISE') listeningDetail.value = await studentApi.listeningExercise(activity.id)
    if (type === 'TRACING_EXERCISE') tracingDetail.value = await studentApi.tracingExercise(activity.id)
    if (type === 'DRAG_DROP_ACTIVITY') dragDropDetail.value = await studentApi.dragDropActivity(activity.id)
    if (type === 'STORY_ACTIVITY') storyDetail.value = await studentApi.storyActivity(activity.id)
  } catch (reason) {
    actionError.value = resolveApiError(reason, `Unable to load ${activityLabel(type).toLowerCase()}.`)
  } finally {
    loadingActivity.value = false
  }
}

async function completeActivity(type: PracticeActivityType, id: number): Promise<StudentActivityCompletionResponse> {
  if (type === 'MATCHING_GAME') return studentApi.completeMatchingGame(id)
  if (type === 'LISTENING_EXERCISE') return studentApi.completeListeningExercise(id)
  if (type === 'TRACING_EXERCISE') return studentApi.completeTracingExercise(id)
  if (type === 'DRAG_DROP_ACTIVITY') return studentApi.completeDragDropActivity(id)
  if (type === 'STORY_ACTIVITY') return studentApi.completeStoryActivity(id)
  return studentApi.completeFlashcard(id)
}

async function refreshPracticeProgress() {
  await Promise.all([
    activitiesResource.reload(),
    dashboardResource.reload(),
    progressResource.reload(),
  ])
}

async function completeSelectedActivity() {
  if (submittingActivity.value) return
  const activity = selectedActivity.value
  if (!activity) {
    actionError.value = `${activityLabel(selectedActivityType.value)} is not available to complete.`
    actionSuccess.value = null
    return
  }

  if (activitiesResource.usingFallback.value) {
    actionError.value = 'Preview activities cannot be submitted to the backend. Use a live student account to save progress.'
    actionSuccess.value = null
    return
  }

  actionError.value = null
  actionSuccess.value = null
  submittingActivity.value = true
  try {
    const result = await completeActivity(selectedActivityType.value, activity.id)
    await refreshPracticeProgress()
    actionSuccess.value = `Activity completed. ${result.earnedPoints > 0 ? `+${result.earnedPoints} XP. ` : ''}${result.firstCompletion ? `Total XP: ${formatNumber(result.totalPoints)}.` : 'Progress refreshed.'}`
  } catch (reason) {
    actionError.value = resolveApiError(reason, `Unable to complete ${activityLabel(selectedActivityType.value).toLowerCase()}.`)
  } finally {
    submittingActivity.value = false
  }
}

async function markFlashcardDone(known: boolean) {
  if (submittingActivity.value) return
  actionError.value = null
  actionSuccess.value = null
  if (!flashcardAvailable.value) {
    actionError.value = 'No flashcard is available in your current practice library yet.'
    return
  }

  if (known && flashcardResource.usingFallback.value) {
    actionError.value = 'Preview flashcards cannot be submitted to the backend. Use a live student account to save progress.'
    return
  }

  if (known) {
    try {
      await studentApi.completeFlashcard(flashcard.value.id)
      await refreshPracticeProgress()
    } catch (reason) {
      actionError.value = resolveApiError(reason, 'Unable to save this flashcard progress.')
      return
    }
  }

  if (!known) {
    showMalayMeaning.value = true
    actionSuccess.value = 'No problem. Translation is shown so you can review this card.'
  } else {
    actionSuccess.value = 'Flashcard progress saved. Keep going!'
  }
}

function openQuizAttempt(quizId: number | null) {
  if (!quizId) {
    void router.push('/student/quiz')
    return
  }

  void router.push({
    name: 'student-quiz',
    params: { quizId: String(quizId) },
  })
}

function toggleTranslation() {
  showMalayMeaning.value = !showMalayMeaning.value
  actionError.value = null
  actionSuccess.value = showMalayMeaning.value ? 'Bahasa Melayu meaning shown.' : 'English meaning shown.'
}

function playCardPronunciation() {
  actionError.value = null
  actionSuccess.value = null
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
    actionError.value = 'Pronunciation playback is not available in this browser.'
    return
  }

  const utterance = new SpeechSynthesisUtterance(`${flashcard.value.frontText}. ${flashcard.value.pinyin ?? ''}`)
  utterance.lang = 'zh-CN'
  window.speechSynthesis.cancel()
  window.speechSynthesis.speak(utterance)
  actionSuccess.value = 'Playing Mandarin pronunciation.'
}

function reloadAll() {
  void activitiesResource.reload()
  void wrongAnswersResource.reload()
  void flashcardResource.reload()
  void dashboardResource.reload()
  void progressResource.reload()
}
</script>

<style scoped>
.student-practice-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.44fr) 376px;
  gap: 18px;
}

.student-practice-main,
.student-practice-side {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.student-practice-selector {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 12px;
  padding: 16px;
}

.student-practice-option {
  border: 0;
  padding: 0;
  background: transparent;
  display: grid;
  gap: 8px;
  justify-items: center;
  align-content: start;
  color: var(--text);
  text-align: center;
}

.student-practice-option strong {
  font-size: 0.95rem;
  line-height: 1.3;
}

.student-practice-option-icon {
  width: 112px;
  min-height: 112px;
  border-radius: 22px;
  display: grid;
  place-items: center;
  font-size: 2.6rem;
  background:
    radial-gradient(circle at 28% 22%, rgba(255, 255, 255, 0.95), transparent 22%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(247, 249, 255, 0.98));
  border: 1px solid rgba(231, 235, 247, 0.96);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.95);
}

.student-practice-option.is-active .student-practice-option-icon {
  border-color: rgba(108, 81, 255, 0.78);
  box-shadow:
    inset 0 0 0 1px rgba(108, 81, 255, 0.2),
    0 12px 28px rgba(124, 98, 255, 0.14);
}

.student-practice-option-icon::after {
  content: '';
  width: 66px;
  height: 66px;
  border-radius: 18px;
  background:
    radial-gradient(circle at 30% 25%, rgba(255, 255, 255, 0.98), transparent 24%),
    linear-gradient(135deg, color-mix(in srgb, var(--option-tint) 14%, white), color-mix(in srgb, var(--option-tint) 24%, white));
  position: absolute;
  opacity: 0.55;
}

.student-practice-option-icon {
  position: relative;
  color: color-mix(in srgb, var(--option-tint) 84%, #2e324f);
}

.student-practice-option-icon > * {
  position: relative;
  z-index: 1;
}

.student-practice-stage {
  position: relative;
  overflow: hidden;
  border-radius: 30px;
  background:
    radial-gradient(circle at 18% 14%, rgba(255, 255, 255, 0.22), transparent 16%),
    linear-gradient(135deg, #7c59ff 0%, #8f66ff 38%, #6a51f4 100%);
  box-shadow: 0 28px 56px rgba(121, 95, 255, 0.16);
}

.student-practice-stage-glow {
  position: absolute;
  inset: auto 18px 0 auto;
  width: 42%;
  aspect-ratio: 1;
  transform: translate(0, 42%);
  border-radius: 50%;
  background: rgba(255, 214, 103, 0.16);
  filter: blur(12px);
}

.student-practice-stage-top {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 22px 22px 0;
  color: white;
}

.student-practice-stage-copy h2 {
  margin: 0;
  font-family: 'Fredoka', sans-serif;
  font-size: 2rem;
}

.student-practice-info {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 0.8rem;
  font-weight: 700;
  border: 1px solid rgba(255, 255, 255, 0.36);
  background: rgba(255, 255, 255, 0.12);
}

.student-practice-level {
  margin-top: 10px;
  border: 0;
  min-height: 38px;
  padding: 6px 0;
  background: transparent;
  color: rgba(255, 255, 255, 0.9);
  font-size: 1.05rem;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.student-practice-stage-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.student-practice-pill {
  min-width: 224px;
  padding: 12px 16px;
  border-radius: 22px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.98);
  color: var(--text);
  box-shadow: 0 16px 28px rgba(72, 53, 165, 0.18);
}

.student-practice-pill small {
  display: block;
  color: var(--text-soft);
  font-size: 0.8rem;
}

.student-practice-pill strong {
  font-size: 1.32rem;
}

.student-practice-pill-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 1.3rem;
  color: white;
}

.student-practice-pill-icon.is-gold {
  background: linear-gradient(180deg, #ffd54c, #f1ab1e);
}

.student-practice-pill-icon.is-green {
  background: linear-gradient(180deg, #77d654, #4ab74d);
}

.student-practice-stage-body {
  position: relative;
  min-height: 294px;
  margin-top: 18px;
  padding: 30px 22px 0;
  background:
    linear-gradient(180deg, rgba(242, 248, 255, 0.96) 0%, rgba(230, 239, 255, 0.98) 48%, rgba(224, 244, 195, 0.94) 100%);
}

.student-practice-stage-cloud,
.student-practice-recorded-cloud {
  position: absolute;
  width: 88px;
  height: 24px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.34);
}

.student-practice-stage-cloud::before,
.student-practice-stage-cloud::after {
  content: '';
  position: absolute;
  bottom: 0;
  border-radius: 50%;
  background: inherit;
}

.student-practice-stage-cloud::before {
  left: 12px;
  width: 26px;
  height: 26px;
}

.student-practice-stage-cloud::after {
  right: 12px;
  width: 32px;
  height: 32px;
}

.student-practice-stage-cloud.is-left {
  left: 18px;
  top: 146px;
}

.student-practice-stage-cloud.is-right {
  right: 70px;
  top: 128px;
}

.student-practice-hill {
  position: absolute;
  bottom: -12px;
  border-radius: 50%;
}

.student-practice-hill.is-back {
  left: 10px;
  width: 44%;
  height: 124px;
  transform: none;
  background: linear-gradient(180deg, rgba(154, 209, 240, 0.72), rgba(90, 156, 202, 0.82));
}

.student-practice-hill.is-front {
  right: 10px;
  width: 64%;
  height: 138px;
  transform: none;
  background: linear-gradient(180deg, rgba(190, 225, 121, 0.64), rgba(115, 182, 84, 0.8));
}

.student-practice-pagoda {
  position: absolute;
  right: 76px;
  bottom: 48px;
  width: 106px;
  height: 118px;
  opacity: 0.95;
  background:
    linear-gradient(180deg, #ffce7a 0 10%, transparent 10% 14%, #db7c56 14% 26%, transparent 26% 31%, #f2aa55 31% 40%, transparent 40% 45%, #d16652 45% 58%, transparent 58% 64%, #c65b45 64% 100%);
  clip-path: polygon(50% 0, 74% 12%, 100% 30%, 84% 34%, 84% 40%, 100% 48%, 84% 52%, 84% 58%, 100% 66%, 80% 70%, 80% 100%, 20% 100%, 20% 70%, 0 66%, 16% 58%, 16% 52%, 0 48%, 16% 40%, 16% 34%, 0 30%, 26% 12%);
}

.student-practice-flower {
  position: absolute;
  bottom: 16px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background:
    radial-gradient(circle at 50% 50%, #ffd764 0 18%, transparent 19%),
    radial-gradient(circle at 50% 16%, #8f6aff 0 24%, transparent 25%),
    radial-gradient(circle at 82% 44%, #8f6aff 0 24%, transparent 25%),
    radial-gradient(circle at 64% 84%, #8f6aff 0 24%, transparent 25%),
    radial-gradient(circle at 18% 44%, #8f6aff 0 24%, transparent 25%);
}

.student-practice-flower.is-left {
  left: 20px;
}

.student-practice-flower.is-right {
  right: 18px;
  bottom: 10px;
  transform: scale(0.88);
}

.student-practice-flashcard {
  position: relative;
  z-index: 1;
  width: min(100%, 472px);
  margin: 0 auto;
  min-height: 234px;
  padding: 30px 24px;
  border-radius: 28px;
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 22px 40px rgba(127, 143, 196, 0.16);
  text-align: center;
}

.student-practice-flashcard-badge {
  color: var(--text-soft);
  font-size: 0.82rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.student-practice-flashcard strong {
  font-family: 'Fredoka', sans-serif;
  font-size: clamp(2.8rem, 4vw, 4rem);
  line-height: 1;
}

.student-practice-flashcard span {
  color: #667598;
  font-size: 1.02rem;
}

.student-practice-flashcard b {
  color: var(--primary);
  font-size: 1.8rem;
}

.student-practice-activity-card {
  position: relative;
  z-index: 1;
  width: min(100%, 560px);
  margin: 0 auto;
  min-height: 234px;
  padding: 26px 24px;
  border-radius: 28px;
  display: grid;
  gap: 12px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 22px 40px rgba(127, 143, 196, 0.16);
}

.student-practice-activity-card strong {
  font-family: 'Fredoka', sans-serif;
  font-size: clamp(1.7rem, 3vw, 2.5rem);
  line-height: 1.08;
}

.student-practice-activity-card span,
.student-practice-activity-list small {
  color: #667598;
  line-height: 1.5;
}

.student-practice-activity-list {
  display: grid;
  gap: 8px;
  max-height: 116px;
  overflow: auto;
  padding: 12px;
  border-radius: 16px;
  background: rgba(246, 248, 252, 0.96);
}

.student-practice-activity-list a {
  color: var(--primary);
  font-weight: 800;
}

.student-practice-side-actions {
  position: absolute;
  right: 196px;
  top: 34px;
  z-index: 2;
  display: grid;
  gap: 12px;
}

.student-practice-circle {
  width: 58px;
  height: 58px;
  border: 1px solid rgba(222, 229, 245, 0.96);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.98);
  color: var(--text);
  font-size: 1.2rem;
  font-weight: 700;
  box-shadow: 0 12px 24px rgba(142, 152, 200, 0.12);
}

.student-practice-stage-footer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  padding: 10px 18px 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.76), rgba(255, 255, 255, 0.92));
}

.student-practice-stage-footer strong {
  font-family: 'Fredoka', sans-serif;
  font-size: 1.4rem;
  color: var(--text);
  text-align: center;
}

.student-practice-answer {
  min-height: 50px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-weight: 700;
}

.student-practice-answer.is-soft-red {
  color: #f06057;
  background: linear-gradient(180deg, rgba(255, 245, 242, 0.96), rgba(255, 241, 238, 0.96));
  border-color: rgba(255, 198, 188, 0.72);
}

.student-practice-answer.is-soft-green {
  color: #58b348;
  background: linear-gradient(180deg, rgba(242, 251, 234, 0.98), rgba(239, 248, 230, 0.98));
  border-color: rgba(183, 226, 173, 0.88);
}

.student-review-head {
  gap: 18px;
  align-items: flex-start;
}

.student-review-head p {
  margin: 4px 0 0;
  color: var(--text-soft);
  font-size: 0.92rem;
  line-height: 1.5;
}

.student-review-alert {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: white;
  font-weight: 800;
  background: linear-gradient(180deg, #ff6958, #ff5044);
}

.student-review-count {
  color: var(--text-soft);
  font-size: 0.88rem;
  font-weight: 700;
}

.student-review-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.student-review-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 84px;
  gap: 14px;
  align-items: center;
  padding: 16px;
  border-radius: 22px;
  border: 1px solid rgba(233, 237, 247, 0.96);
  background: rgba(255, 255, 255, 0.98);
}

.student-review-copy {
  display: grid;
  gap: 6px;
}

.student-review-copy p,
.student-review-copy small {
  margin: 0;
  color: #606e90;
  line-height: 1.45;
}

.student-review-label {
  color: #ff5646;
  font-size: 0.78rem;
  font-weight: 800;
  text-transform: uppercase;
}

.student-review-visual {
  width: 84px;
  min-height: 84px;
  border-radius: 20px;
  display: grid;
  place-items: center;
  font-family: 'Fredoka', sans-serif;
  font-size: 2rem;
  background: rgba(250, 244, 255, 0.98);
}

.student-review-visual.is-purple {
  color: #6a50f5;
  background: linear-gradient(180deg, rgba(248, 242, 255, 0.98), rgba(243, 238, 255, 0.98));
}

.student-review-visual.is-gold {
  background: linear-gradient(180deg, rgba(255, 245, 218, 0.98), rgba(255, 239, 199, 0.98));
}

.student-review-visual.is-peach {
  background: linear-gradient(180deg, rgba(255, 240, 231, 0.98), rgba(255, 232, 221, 0.98));
}

.student-review-visual.is-mint {
  background: linear-gradient(180deg, rgba(236, 249, 241, 0.98), rgba(228, 246, 235, 0.98));
}

.student-review-button {
  grid-column: 1 / -1;
  width: fit-content;
  min-height: 38px;
  padding: 8px 16px;
  border: 1px solid rgba(192, 176, 255, 0.72);
  border-radius: 999px;
  background: rgba(246, 243, 255, 0.96);
  color: var(--primary);
  font-weight: 700;
}

.student-practice-progress-card,
.student-practice-attempts-card {
  min-height: 0;
}

.student-practice-progress-icon {
  font-size: 1.2rem;
}

.student-practice-progress-top {
  padding-top: 6px;
}

.student-practice-stat-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(232, 236, 247, 0.96);
}

.student-practice-stat-row small {
  display: block;
  color: var(--text-soft);
  font-size: 0.82rem;
}

.student-practice-stat-row strong {
  display: block;
  margin-top: 6px;
  font-family: 'Fredoka', sans-serif;
  font-size: 1.6rem;
}

.student-practice-weekly {
  display: grid;
  gap: 10px;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid rgba(232, 236, 247, 0.96);
}

.student-practice-weekly span {
  color: var(--text-soft);
  font-size: 0.86rem;
  font-weight: 700;
}

.student-practice-attempts {
  display: grid;
  gap: 10px;
}

.student-practice-attempt {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid rgba(236, 239, 247, 0.96);
}

.student-practice-attempt:last-child {
  border-bottom: 0;
}

.student-practice-attempt-icon {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  font-size: 1.2rem;
  background: linear-gradient(135deg, rgba(246, 242, 255, 0.98), rgba(255, 250, 229, 0.98));
}

.student-practice-attempt-copy {
  display: grid;
  gap: 4px;
}

.student-practice-attempt-copy span {
  color: var(--text-soft);
  font-size: 0.84rem;
}

.student-practice-attempt-score {
  font-family: 'Fredoka', sans-serif;
  font-size: 1.5rem;
}

.student-practice-attempt-score.is-green {
  color: #52b748;
}

.student-practice-attempt-score.is-yellow {
  color: #f5a11f;
}

.student-practice-attempt-score.is-red {
  color: #ff5c54;
}

.student-practice-attempt-button {
  min-width: 84px;
  min-height: 38px;
  border-radius: 14px;
  border: 1px solid rgba(201, 228, 187, 0.96);
  background: rgba(241, 250, 232, 0.98);
  color: #58aa45;
  font-weight: 700;
}

@media (max-width: 1380px) {
  .student-practice-layout {
    grid-template-columns: 1fr;
  }

  .student-practice-selector {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .student-practice-side-actions {
    right: 40px;
  }
}

@media (max-width: 1100px) {
  .student-review-grid {
    grid-template-columns: 1fr;
  }

  .student-practice-stage-top,
  .student-practice-stage-footer {
    grid-template-columns: 1fr;
  }

  .student-practice-stage-footer strong {
    order: -1;
  }

  .student-practice-side-actions {
    top: 24px;
    right: 24px;
  }
}

@media (max-width: 860px) {
  .student-practice-selector {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .student-practice-stage-glow {
    inset: auto 14px 0 auto;
    width: 34%;
    transform: translate(0, 42%);
  }

  .student-practice-hill.is-back {
    left: 10px;
    width: calc(44% - 10px);
  }

  .student-practice-hill.is-front {
    right: 10px;
    width: calc(64% - 10px);
  }

  .student-practice-option-icon {
    width: 100%;
    min-height: 92px;
  }

  .student-practice-stage-body {
    min-height: 320px;
    padding-top: 78px;
  }

  .student-practice-side-actions {
    position: static;
    display: flex;
    justify-content: center;
    margin: 0 auto 14px;
  }

  .student-practice-flashcard {
    width: 100%;
  }

  .student-practice-stat-row,
  .student-practice-attempt {
    grid-template-columns: 1fr;
  }

  .student-practice-attempt-button {
    width: fit-content;
  }
}
</style>





