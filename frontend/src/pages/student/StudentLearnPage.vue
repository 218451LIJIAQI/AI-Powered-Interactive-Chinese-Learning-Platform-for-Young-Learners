<template>
  <div class="page-stack student-learn-page">
    <PageHeaderBlock :title="t('studentLearn.pageTitle')" :description="t('studentLearn.pageDescription')" icon="learn">
      <template v-if="hasActiveLesson" #before>
        <LearningBreadcrumb :items="lessonBreadcrumb" data-testid="lesson-breadcrumb" />
      </template>
    </PageHeaderBlock>

    <DataNotice :using-fallback="usingFallback" :error="actionError" :success="actionSuccess" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="student-learn-overview" aria-labelledby="student-learn-overview-title">
        <h2 id="student-learn-overview-title" class="sr-only">{{ t('studentLearn.overview') }}</h2>
        <article
          v-for="level in courseLevels"
          :key="level.id"
          class="student-learn-kpi"
          :class="{ 'is-active': level.active }"
        >
          <span class="student-learn-kpi-icon" aria-hidden="true"><IconGlyph :name="level.icon" :size="22" /></span>
          <div class="student-learn-kpi-copy">
            <span class="student-learn-kpi-label">{{ level.title }}</span>
            <strong class="student-learn-kpi-value">{{ level.progress }}</strong>
            <span class="student-learn-kpi-hint">{{ level.subtitle }}</span>
          </div>
        </article>
      </section>

      <div class="student-learn-layout">
        <div class="student-learn-main">
          <section class="student-learn-hero" aria-labelledby="student-learn-lesson-title">
            <div class="student-learn-hero-copy">
              <div class="student-learn-hero-chips">
                <span class="student-learn-hero-chip">{{ heroChipLabel }}</span>
                <MasteryChip v-if="hasActiveLesson" :mastery="lessonState.mastery" />
                <span v-if="bestQuizLabel" class="student-learn-hero-chip" data-testid="lesson-best-quiz">
                  {{ bestQuizLabel }}
                </span>
              </div>
              <h2 id="student-learn-lesson-title" class="student-learn-hero-title">{{ lessonTitle }}</h2>
              <p class="student-learn-hero-text">{{ lessonDescription }}</p>

              <div v-if="hasActiveLesson" class="student-learn-hero-progress">
                <div class="student-learn-hero-progress-row">
                  <span>{{ t('studentLearn.progress') }}</span>
                  <strong>{{ formatPercent(currentLessonPercent) }} - {{ currentLessonProgressLabel }}</strong>
                </div>
                <ProgressBar :progress="currentLessonPercent" :label="t('studentLearn.progress')" />
              </div>

              <button v-if="hasActiveLesson" class="btn btn-accent btn-lg" type="button" @click="openLessonQuiz">
                {{ lessonQuizzes.length ? t('studentLearn.takeQuiz') : t('studentLearn.openPracticeCta') }}
                <IconGlyph name="chevronRight" :size="18" />
              </button>
              <button v-else class="btn btn-accent btn-lg" type="button" @click="openCatalog">
                {{ t('studentLearn.browseCourses') }}
                <IconGlyph name="chevronRight" :size="18" />
              </button>
            </div>

            <PandaIllustration
              class="student-learn-hero-mascot"
              :size="180"
              outfit="var(--color-primary-500)"
              accent="var(--color-accent-400)"
              pose="wave"
            />
          </section>

          <section
            v-if="hasActiveLesson"
            class="panel student-learn-card"
            aria-labelledby="student-learn-content-title"
          >
            <h2 id="student-learn-content-title" class="card-title">{{ t('studentLearn.lessonContent') }}</h2>
            <img
              v-if="lessonCoverUrl"
              class="student-learn-cover"
              :src="lessonCoverUrl"
              :alt="coverImageAlt"
              loading="lazy"
              referrerpolicy="no-referrer"
              data-testid="lesson-cover-image"
            />
            <div v-if="lessonParagraphs.length" class="student-learn-body" data-testid="lesson-body">
              <p v-for="(paragraph, paragraphIndex) in lessonParagraphs" :key="paragraphIndex">
                <LinkifiedText :text="paragraph" />
              </p>
            </div>
            <p v-else class="student-learn-muted" data-testid="lesson-body">{{ t('studentLearn.noContent') }}</p>
          </section>

          <section
            v-if="hasActiveLesson"
            class="panel student-learn-card"
            aria-labelledby="student-learn-quizzes-title"
            data-testid="lesson-quizzes"
          >
            <h2 id="student-learn-quizzes-title" class="card-title">{{ t('studentLearn.quizzesTitle') }}</h2>
            <ul v-if="lessonQuizzes.length" class="student-learn-quiz-list">
              <li v-for="quiz in lessonQuizzes" :key="quiz.id" class="student-learn-quiz" data-testid="lesson-quiz">
                <span class="student-learn-quiz-icon" aria-hidden="true"><IconGlyph name="quiz" :size="20" /></span>
                <div class="student-learn-quiz-copy">
                  <strong>{{ quiz.title }}</strong>
                  <div class="student-learn-quiz-meta">
                    <LearningStageTag :stage="quiz.learningStage" />
                    <span>{{ quizMetaLabel(quiz) }}</span>
                  </div>
                </div>
                <button
                  class="btn btn-primary"
                  type="button"
                  :aria-label="t('studentLearn.startQuizNamed', { title: quiz.title })"
                  data-testid="lesson-start-quiz"
                  @click="openQuiz(quiz.id)"
                >
                  {{ t('studentLearn.startQuiz') }}
                </button>
              </li>
            </ul>
            <p v-else class="student-learn-muted">{{ t('studentLearn.noQuizzes') }}</p>

            <div class="student-learn-practice-link">
              <div class="student-learn-practice-copy">
                <strong>{{ t('studentLearn.practiceActivities') }}</strong>
                <span>{{ t('studentLearn.practiceHint') }}</span>
              </div>
              <button class="btn btn-secondary" type="button" data-testid="lesson-practice-link" @click="openPractice">
                {{ t('studentLearn.openPracticeCta') }}
                <IconGlyph name="chevronRight" :size="16" />
              </button>
            </div>
          </section>

          <section class="student-learn-modules" aria-labelledby="student-learn-recent-title">
            <h2 id="student-learn-recent-title" class="section-title">{{ t('studentLearn.recentProgress') }}</h2>

            <ul v-if="learningModules.length" class="student-module-grid">
              <li v-for="module in learningModules" :key="module.id">
                <button
                  class="student-module-card"
                  :class="{ 'is-complete': module.complete, 'is-current': module.current }"
                  type="button"
                  :aria-label="t('studentLearn.openLessonNamed', { title: module.title })"
                  data-testid="recent-lesson-card"
                  @click="openLesson(module.id)"
                >
                  <span class="student-module-index" aria-hidden="true">{{ module.displayIndex }}</span>
                  <span class="student-module-icon" aria-hidden="true">
                    <IconGlyph :name="module.icon" :size="22" />
                  </span>
                  <strong class="student-module-title">{{ module.title }}</strong>
                  <MasteryChip :mastery="module.mastery" />
                  <span class="student-module-progress">
                    <ProgressBar class="is-sm" :progress="module.progress" :tone="module.tone" :label="module.title" />
                    <span class="student-module-count">{{ formatPercent(module.progress) }}</span>
                  </span>
                </button>
              </li>
            </ul>
            <StatePanel
              v-else
              compact
              icon="learn"
              :title="t('studentLearn.recentProgress')"
              :message="t('studentLearn.noProgress')"
            />
          </section>
        </div>

        <aside class="student-learn-side">
          <section
            class="panel student-learn-card"
            aria-labelledby="student-learn-videos-title"
            data-testid="lesson-videos"
          >
            <div class="student-learn-card-head">
              <span class="media-badge small" aria-hidden="true"><IconGlyph name="play" :size="18" /></span>
              <h2 id="student-learn-videos-title" class="card-title">{{ t('studentLearn.recordedLessons') }}</h2>
            </div>

            <div v-if="featuredVideo" class="student-learn-video-scene">
              <PandaIllustration
                class="student-learn-video-panda"
                :size="112"
                outfit="var(--color-primary-500)"
                accent="var(--color-accent-400)"
                compact
              />
              <button
                class="student-learn-video-play"
                type="button"
                :aria-label="t('studentLearn.playRecordedNamed', { title: featuredVideo.title })"
                :disabled="videoActionId !== null || !isPlayable(featuredVideo)"
                @click="openRecordedVideo(featuredVideo)"
              >
                <IconGlyph name="play" :size="22" />
              </button>
            </div>

            <ul v-if="lessonVideos.length" class="student-learn-video-list">
              <li
                v-for="video in lessonVideos"
                :key="video.id"
                class="student-learn-video"
                :class="{ 'is-featured': video.id === featuredVideo?.id }"
                data-testid="lesson-video"
              >
                <div class="student-learn-video-copy">
                  <strong>{{ video.title }}</strong>
                  <p>{{ video.description || t('studentLearn.noVideoDetails') }}</p>
                </div>
                <p class="student-learn-video-meta">
                  <span>{{ videoDurationLabel(video) }}</span>
                  <span aria-hidden="true">·</span>
                  <span>{{ videoProgressLabel(video) }}</span>
                </p>
                <div class="student-learn-video-actions">
                  <button
                    class="btn btn-secondary btn-sm"
                    type="button"
                    :disabled="videoActionId !== null || !isPlayable(video)"
                    :aria-busy="videoActionId === video.id"
                    @click="openRecordedVideo(video)"
                  >
                    {{
                      videoActionId === video.id ? t('studentLearn.savingProgress') : t('studentLearn.startWatching')
                    }}
                  </button>
                  <button
                    class="btn btn-ghost btn-sm"
                    type="button"
                    :disabled="videoActionId !== null || !isPlayable(video) || isVideoCompleted(video)"
                    @click="markRecordedVideoComplete(video)"
                  >
                    <IconGlyph v-if="isVideoCompleted(video)" name="status" :size="16" />
                    {{ isVideoCompleted(video) ? t('studentLearn.completed') : t('studentLearn.markComplete') }}
                  </button>
                </div>
                <template v-if="playingVideoId === video.id">
                  <p v-if="videoFileLoading" class="student-learn-muted" role="status">
                    {{ t('studentLearn.loadingVideo') }}
                  </p>
                  <video
                    v-else-if="videoObjectUrl"
                    class="student-learn-inline-video"
                    :src="videoObjectUrl"
                    :aria-label="video.title"
                    controls
                    playsinline
                  />
                </template>
              </li>
            </ul>
            <p v-else class="student-learn-muted">{{ t('studentLearn.noVideos') }}</p>
          </section>

          <section class="panel student-learn-card" aria-labelledby="student-learn-progress-title">
            <div class="student-learn-card-head">
              <span class="media-badge small" aria-hidden="true"><IconGlyph name="progress" :size="18" /></span>
              <h2 id="student-learn-progress-title" class="card-title">{{ t('studentLearn.myProgress') }}</h2>
            </div>

            <div class="student-learn-progress-row">
              <div class="student-learn-progress-head">
                <span>{{ t('studentLearn.currentProgress') }}</span>
                <strong>{{ formatPercent(currentLessonPercent) }}</strong>
              </div>
              <ProgressBar :progress="currentLessonPercent" tone="success" :label="t('studentLearn.currentProgress')" />
              <span class="student-learn-progress-hint">{{ currentLessonProgressLabel }}</span>
            </div>

            <div class="student-learn-progress-row">
              <div class="student-learn-progress-head">
                <span>{{ t('studentLearn.overallProgress') }}</span>
                <strong>{{ formatPercent(progress.overallCompletionPercent) }}</strong>
              </div>
              <ProgressBar :progress="progress.overallCompletionPercent" :label="t('studentLearn.overallProgress')" />
              <span class="student-learn-progress-hint">{{ overallLessonCountLabel }}</span>
            </div>
          </section>

          <section class="student-learn-encourage">
            <span class="student-learn-encourage-icon" aria-hidden="true"><IconGlyph name="fun" :size="22" /></span>
            <div>
              <strong>{{ t('studentLearn.keepGoing', { name: studentName }) }}</strong>
              <p>{{ t('studentLearn.encouragement') }}</p>
            </div>
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
import MasteryChip from '@/components/common/MasteryChip.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import LearningStageTag from '@/components/content/LearningStageTag.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import LearningBreadcrumb, { type BreadcrumbItem } from '@/components/learning/LearningBreadcrumb.vue'
import LinkifiedText from '@/components/learning/LinkifiedText.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useAuthorizedFile } from '@/composables/useAuthorizedFile'
import { useResource } from '@/composables/useResource'
import { studentLessonPlayer, studentProgress as studentProgressDemo } from '@/demo/data'
import { learningContextMessages } from '@/plugins/learningContextMessages'
import { studentLearnMessages } from '@/plugins/studentLearnMessages'
import { useAuthStore } from '@/stores/auth'
import type {
  LessonMastery,
  RecordedLessonVideoResponse,
  StudentLessonPlayerResponse,
  StudentLessonProgressResponse,
  StudentQuizSummaryResponse,
  VideoProgressResponse,
} from '@/types/api'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { splitLessonParagraphs } from '@/utils/cjkText'
import { clampPercent, formatPercent, nonNegativeInteger } from '@/utils/format'
import { sortByLearningStage } from '@/utils/learningStage'
import { safeImageUrl } from '@/utils/linkify'
import { formatRecordingTime } from '@/utils/practiceUpload'
import { emptyStudentProgress } from '@/utils/progressSummary'
import { positiveIntQuery } from '@/utils/routeCompat'
import { openSafeHttpUrl, safeHttpUrl } from '@/utils/url'

const { t } = useI18n({
  messages: {
    en: { studentLearn: studentLearnMessages.en, learningContext: learningContextMessages.en },
    ms: { studentLearn: studentLearnMessages.ms, learningContext: learningContextMessages.ms },
    'zh-CN': { studentLearn: studentLearnMessages['zh-CN'], learningContext: learningContextMessages['zh-CN'] },
  },
})

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const requestedVideoId = computed(() => positiveIntQuery(route.query.videoId))
const progressResource = useResource(() => studentApi.progress(), studentProgressDemo)
const emptyProgress = emptyStudentProgress()
const progress = computed(() => progressResource.data.value ?? emptyProgress)
const lessonCandidateIds = computed(() => {
  const lessonIds = [
    positiveIntQuery(route.query.lessonId),
    ...progress.value.recentLessons.map((recentLesson) => recentLesson.lessonId),
  ]

  return Array.from(new Set(lessonIds.filter((lessonId): lessonId is number => Boolean(lessonId && lessonId > 0))))
})
const selectedLessonId = computed(() => lessonCandidateIds.value[0] ?? null)
const fallbackLesson = (): StudentLessonPlayerResponse => ({
  ...studentLessonPlayer,
  lessonId: selectedLessonId.value ?? studentLessonPlayer.lessonId,
})
const resource = useResource(loadLessonPlayer, fallbackLesson)

const emptyLesson: StudentLessonPlayerResponse = {
  lessonId: 0,
  title: '',
  description: '',
  basicContent: null,
  estimatedDurationMinutes: null,
  completionPercent: 0,
  completed: false,
  learningType: '',
  liveClasses: [],
  recordedVideos: [],
  contentViewed: false,
  mastery: 'NOT_STARTED',
  bestQuizPercentage: null,
  quizzes: [],
}
const lesson = computed(() => resource.data.value ?? emptyLesson)
const hasActiveLesson = computed(() => lesson.value.lessonId > 0)
const lessonTitle = computed(() => (hasActiveLesson.value ? lesson.value.title : t('studentLearn.lessonUnavailable')))
const lessonDescription = computed(() =>
  hasActiveLesson.value ? lesson.value.description || t('studentLearn.noLessonDetails') : t('studentLearn.unassigned'),
)
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
/** Latest progress returned by `POST /students/lessons/{id}/viewed` (newer than the lesson player payload). */
const viewedProgress = ref<StudentLessonProgressResponse | null>(null)
const viewedLessonIds = new Set<number>()
const videoProgressById = ref<Record<number, VideoProgressResponse>>({})
const videoProgressLoading = ref(false)
const videoActionId = ref<number | null>(null)
let videoProgressSequence = 0

const pageLoading = computed(() => resource.loading.value || progressResource.loading.value || switchingLesson.value)
const pageError = computed(() => resource.error.value || progressResource.error.value)
const usingFallback = computed(() => resource.usingFallback.value || progressResource.usingFallback.value)
const studentName = computed(
  () => auth.currentUser?.studentProfile?.displayName || auth.displayName.split(' ')[0] || t('studentLearn.learner'),
)

const lessonState = computed<{
  completionPercent: number
  completed: boolean
  mastery: LessonMastery
  bestQuizPercentage: number | null
}>(() => {
  const override = viewedProgress.value?.lessonId === lesson.value.lessonId ? viewedProgress.value : null
  return {
    completionPercent: override?.completionPercent ?? lesson.value.completionPercent,
    completed: override?.completed ?? lesson.value.completed,
    mastery: override?.mastery ?? lesson.value.mastery,
    bestQuizPercentage: (override ? override.bestQuizPercentage : lesson.value.bestQuizPercentage) ?? null,
  }
})
const currentLessonPercent = computed(() => clampPercent(lessonState.value.completionPercent))
const currentLessonProgressLabel = computed(() => {
  if (lessonState.value.completed || currentLessonPercent.value >= 100) return t('studentLearn.completed')
  if (currentLessonPercent.value > 0) return t('studentLearn.inProgress')
  return t('studentLearn.notStarted')
})
const bestQuizLabel = computed(() => {
  const best = lessonState.value.bestQuizPercentage
  if (!hasActiveLesson.value || best === null || !Number.isFinite(best)) return ''
  return t('studentLearn.bestQuiz', { percent: formatPercent(best) })
})
const heroChipLabel = computed(() => {
  const minutes = nonNegativeInteger(lesson.value.estimatedDurationMinutes)
  if (!hasActiveLesson.value || minutes <= 0) return t('studentLearn.lessonLabel')
  return `${t('studentLearn.lessonLabel')} - ${t('studentLearn.durationMinutes', { count: minutes }, minutes)}`
})
const lessonParagraphs = computed(() => splitLessonParagraphs(lesson.value.basicContent))
const lessonCoverUrl = computed(() => safeImageUrl(lesson.value.coverImageUrl))
const coverImageAlt = computed(() => t('learningContext.coverImageAlt', { title: lessonTitle.value }))
/** FR-LRN-02: Course / Module / Lesson the learner is studying. */
const lessonBreadcrumb = computed<BreadcrumbItem[]>(() => [
  { kind: 'section', label: t('learningContext.learn') },
  {
    kind: 'course',
    label: lesson.value.courseTitle,
    // Public course pages are keyed `PACKAGE-<id>` (courses are stored as course packages, D1).
    to: lesson.value.courseId
      ? { name: 'course-detail', params: { courseId: `PACKAGE-${lesson.value.courseId}` } }
      : undefined,
  },
  { kind: 'module', label: lesson.value.moduleTitle },
  { kind: 'lesson', label: lessonTitle.value },
])
const lessonQuizzes = computed(() => sortByLearningStage(lesson.value.quizzes ?? []))
const lessonVideos = computed(() =>
  [...(lesson.value.recordedVideos ?? [])]
    .filter((video) => video.id > 0)
    .sort((first, second) => first.sortOrder - second.sortOrder),
)
const featuredVideo = computed<RecordedLessonVideoResponse | null>(
  () => lessonVideos.value.find((video) => video.id === requestedVideoId.value) ?? lessonVideos.value[0] ?? null,
)
const overallLessonCountLabel = computed(() => {
  const completed = progress.value.completedLessons
  const started = progress.value.totalLessonsStarted
  if (!completed && !started) return t('studentLearn.noLessons')
  return t('studentLearn.lessonCounts', { completed, started })
})

type ModuleIcon = 'flash' | 'history' | 'status'
type ModuleTone = 'primary' | 'success'

interface CourseLevel {
  id: number
  title: string
  subtitle: string
  progress: string
  icon: string
  active: boolean
}

interface LearningModule {
  id: number
  displayIndex: number
  title: string
  icon: ModuleIcon
  mastery: LessonMastery
  progress: number
  complete: boolean
  current: boolean
  tone: ModuleTone
}

const courseLevels = computed<CourseLevel[]>(() => [
  {
    id: 1,
    title: t('studentLearn.started'),
    subtitle: t('studentLearn.lessonsOpened'),
    progress: String(nonNegativeInteger(progress.value.totalLessonsStarted)),
    icon: 'learn',
    active: nonNegativeInteger(progress.value.totalLessonsStarted) > 0,
  },
  {
    id: 2,
    title: t('studentLearn.completed'),
    subtitle: t('studentLearn.lessonsFinished'),
    progress: String(nonNegativeInteger(progress.value.completedLessons)),
    icon: 'status',
    active: nonNegativeInteger(progress.value.completedLessons) > 0,
  },
  {
    id: 3,
    title: t('studentLearn.quizAverage'),
    subtitle: t('studentLearn.assessmentScore'),
    progress: formatPercent(progress.value.quizAverage),
    icon: 'star',
    active: clampPercent(progress.value.quizAverage) > 0,
  },
  {
    id: 4,
    title: t('studentLearn.overallProgress'),
    subtitle: progress.value.lastActivitySummary || t('studentLearn.courseCompletion'),
    progress: formatPercent(progress.value.overallCompletionPercent),
    icon: 'progress',
    active: clampPercent(progress.value.overallCompletionPercent) > 0,
  },
])
const learningModules = computed<LearningModule[]>(() =>
  progress.value.recentLessons.map((recentLesson, index) => {
    const completionPercent = clampPercent(recentLesson.completionPercent)
    const complete = recentLesson.completed || completionPercent >= 100
    const current = recentLesson.lessonId === lesson.value.lessonId
    return {
      id: recentLesson.lessonId,
      displayIndex: index + 1,
      title: recentLesson.lessonTitle,
      icon: iconForProgress(completionPercent, complete),
      mastery: recentLesson.mastery,
      progress: completionPercent,
      complete,
      current,
      tone: complete ? 'success' : 'primary',
    }
  }),
)

// resource.reload() keeps the previous lesson on screen, so show the loading state while switching lessons.
const switchingLesson = ref(false)
watch(selectedLessonId, async () => {
  switchingLesson.value = true
  try {
    await resource.reload()
  } finally {
    switchingLesson.value = false
  }
})

watch(
  () =>
    `${lesson.value.lessonId}:${lessonVideos.value.map((video) => video.id).join(',')}:${resource.usingFallback.value}`,
  () => {
    void loadVideoProgress()
  },
  { immediate: true },
)

/** The lesson id whose content is currently on screen (0 while loading, on error, in preview or with no lesson). */
const shownLessonId = computed(() => {
  if (pageLoading.value || pageError.value || resource.usingFallback.value) return 0
  return resource.data.value?.lessonId ?? 0
})

watch(
  shownLessonId,
  (lessonId) => {
    void recordLessonViewed(lessonId)
  },
  { immediate: true, flush: 'post' },
)

function iconForProgress(completionPercent: number, completed: boolean): ModuleIcon {
  if (completed || completionPercent >= 100) return 'status'
  if (completionPercent > 0) return 'flash'
  return 'history'
}

function quizMetaLabel(quiz: StudentQuizSummaryResponse): string {
  const questionCount = nonNegativeInteger(quiz.questionCount)
  const points = nonNegativeInteger(quiz.totalPoints)
  const minutes = nonNegativeInteger(quiz.timeLimitMinutes)
  return [
    t('studentLearn.questions', { count: questionCount }, questionCount),
    t('studentLearn.points', { count: points }, points),
    minutes > 0 ? t('studentLearn.timeLimit', { count: minutes }) : '',
  ]
    .filter(Boolean)
    .join(' - ')
}

async function loadLessonPlayer(): Promise<StudentLessonPlayerResponse> {
  for (const lessonId of lessonCandidateIds.value) {
    try {
      return await studentApi.lessonPlayer(lessonId)
    } catch (reason) {
      if (apiErrorStatus(reason) !== 404) {
        throw reason
      }
    }
  }

  return emptyLesson
}

async function recordLessonViewed(lessonId: number): Promise<void> {
  if (lessonId <= 0 || viewedLessonIds.has(lessonId)) return
  viewedLessonIds.add(lessonId)
  try {
    const response = await studentApi.markLessonViewed(lessonId)
    if (response.lessonId === lesson.value.lessonId) {
      viewedProgress.value = response
    }
  } catch (reason) {
    viewedLessonIds.delete(lessonId)
    actionError.value = resolveApiError(reason, t('studentLearn.viewedError'))
  }
}

function videoDurationLabel(video: RecordedLessonVideoResponse): string {
  const durationSeconds = nonNegativeInteger(video.durationSeconds)
  return durationSeconds > 0 ? formatRecordingTime(durationSeconds) : t('studentLearn.durationUnavailable')
}

function playableVideoUrl(video: RecordedLessonVideoResponse): string | null {
  const streamUrl = safeHttpUrl(video.streamUrl, { allowRelative: true, sameOriginOnly: true })
  if (streamUrl) return streamUrl
  return safeHttpUrl(video.externalVideoUrl)
}

function isPlayable(video: RecordedLessonVideoResponse): boolean {
  return Boolean(video.id && playableVideoUrl(video))
}

function isVideoCompleted(video: RecordedLessonVideoResponse): boolean {
  return Boolean(videoProgressById.value[video.id]?.completed)
}

function videoProgressLabel(video: RecordedLessonVideoResponse): string {
  if (videoProgressLoading.value) return t('studentLearn.loadingProgress')
  const record = videoProgressById.value[video.id]
  if (record?.completed) return t('studentLearn.completePercent')
  // Before the first save the server returns a zero record (`id: null`, 0 %), shown as "Not started".
  const percent = clampPercent(record?.progressPercentage)
  if (!record || record.id === null || percent <= 0) return t('studentLearn.notStarted')
  return t('studentLearn.watchedPercent', { progress: percent })
}

function startProgressPayload(video: RecordedLessonVideoResponse): {
  watchedSeconds: number | undefined
  totalSeconds: number | undefined
  progressPercentage: number
  completed: boolean
} {
  const safeDurationSeconds = nonNegativeInteger(video.durationSeconds)
  const totalSeconds = safeDurationSeconds > 0 ? safeDurationSeconds : undefined
  const watchedSeconds = totalSeconds ? Math.max(1, Math.min(30, Math.floor(totalSeconds * 0.1))) : undefined
  const progressPercentage =
    totalSeconds && watchedSeconds ? Math.min(95, Math.max(1, clampPercent((watchedSeconds / totalSeconds) * 100))) : 1

  return {
    watchedSeconds,
    totalSeconds,
    progressPercentage,
    completed: false,
  }
}

async function loadVideoProgress(): Promise<void> {
  const sequence = ++videoProgressSequence
  const videos = lessonVideos.value
  videoProgressById.value = {}
  if (!videos.length || resource.usingFallback.value || !hasActiveLesson.value) {
    videoProgressLoading.value = false
    return
  }

  videoProgressLoading.value = true
  try {
    const results = await Promise.allSettled(videos.map((video) => studentApi.videoProgress(video.id)))
    if (sequence !== videoProgressSequence) return
    const next: Record<number, VideoProgressResponse> = {}
    results.forEach((result, index) => {
      const video = videos[index]
      if (result.status === 'fulfilled' && video) next[video.id] = result.value
    })
    videoProgressById.value = next
    const failure = results.find((result): result is PromiseRejectedResult => result.status === 'rejected')
    if (failure) actionError.value = resolveApiError(failure.reason, t('studentLearn.loadVideoError'))
  } finally {
    if (sequence === videoProgressSequence) videoProgressLoading.value = false
  }
}

/**
 * Uploaded videos are played inline from a Blob fetched with the bearer token (the token lives in memory only, so a
 * plain link to the stream endpoint would be refused); external videos open in a new tab.
 */
const {
  objectUrl: videoObjectUrl,
  loading: videoFileLoading,
  error: videoFileError,
  load: loadVideoFile,
} = useAuthorizedFile()
const playingVideoId = ref<number | null>(null)

async function openRecordedVideo(video: RecordedLessonVideoResponse): Promise<void> {
  if (videoActionId.value !== null) return
  actionError.value = null
  actionSuccess.value = null
  const uploaded = video.sourceType === 'UPLOADED_FILE' && !resource.usingFallback.value
  if (uploaded) {
    if (!playableVideoUrl(video)) {
      actionError.value = t('studentLearn.noPlayableLink')
      return
    }
    playingVideoId.value = video.id
    const loaded = await loadVideoFile(() => studentApi.recordedVideoFile(video.id), t('studentLearn.videoFileError'))
    if (!loaded) {
      if (videoFileError.value) actionError.value = videoFileError.value
      return
    }
  } else if (!openSafeHttpUrl(playableVideoUrl(video), { allowRelative: true })) {
    actionError.value = t('studentLearn.noPlayableLink')
    return
  }

  if (resource.usingFallback.value) {
    actionSuccess.value = t('studentLearn.openingVideo')
    return
  }

  videoActionId.value = video.id
  try {
    const saved = await studentApi.saveVideoProgress(video.id, startProgressPayload(video))
    videoProgressById.value = { ...videoProgressById.value, [video.id]: saved }
    actionSuccess.value = t('studentLearn.openingAndSaving')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentLearn.openedSaveError'))
  } finally {
    videoActionId.value = null
  }
}

async function markRecordedVideoComplete(video: RecordedLessonVideoResponse): Promise<void> {
  if (videoActionId.value !== null) return
  actionError.value = null
  actionSuccess.value = null
  if (!isPlayable(video)) {
    actionError.value = t('studentLearn.noPlayableLink')
    return
  }

  if (resource.usingFallback.value) {
    actionError.value = t('studentLearn.previewComplete')
    return
  }

  videoActionId.value = video.id
  try {
    const completed = await studentApi.completeVideo(video.id)
    videoProgressById.value = { ...videoProgressById.value, [video.id]: completed }
    // Video completion updates lesson progress on the server; the reloaded lesson replaces the "viewed" snapshot.
    viewedProgress.value = null
    await Promise.all([resource.reload(), progressResource.reload()])
    actionSuccess.value = t('studentLearn.markedComplete')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentLearn.completeError'))
  } finally {
    videoActionId.value = null
  }
}

function reloadAll(): void {
  void resource.reload()
  void progressResource.reload()
  void loadVideoProgress()
}

function openLessonQuiz(): void {
  const firstQuiz = lessonQuizzes.value[0]
  if (firstQuiz) {
    openQuiz(firstQuiz.id)
    return
  }
  openPractice()
}

function openQuiz(quizId: number): void {
  void router.push({ name: 'student-quiz', params: { quizId: String(quizId) } })
}

function openLesson(lessonId: number): void {
  if (lessonId <= 0) return
  void router.push({ name: 'student-learn', query: { lessonId: String(lessonId) } })
}

function openPractice(): void {
  // The lesson id lets the practice page show Module / Lesson in its breadcrumb (FR-LRN-02).
  const lessonId = lesson.value.lessonId
  void router.push(
    lessonId > 0 ? { name: 'student-practice', query: { lessonId: String(lessonId) } } : { name: 'student-practice' },
  )
}

function openCatalog(): void {
  void router.push({ name: 'catalog' })
}
</script>

<style scoped>
/* KPI row */
.student-learn-overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 160px), 1fr));
  gap: var(--grid-gap);
}

.student-learn-kpi {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-card);
  box-shadow: var(--shadow-sm);
}

.student-learn-kpi-icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--surface-sunken);
  color: var(--text-secondary);
}

.student-learn-kpi.is-active .student-learn-kpi-icon {
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.student-learn-kpi-copy {
  display: grid;
  gap: var(--space-0-5);
  min-width: 0;
}

.student-learn-kpi-label {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.student-learn-kpi-value {
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
}

.student-learn-kpi-hint {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-snug);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

/* Main + side layout */
.student-learn-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 340px);
  gap: var(--page-gap);
  align-items: start;
}

.student-learn-main,
.student-learn-side {
  display: grid;
  gap: var(--page-gap);
  align-content: start;
  min-width: 0;
}

.student-learn-card {
  display: grid;
  gap: var(--space-4);
  align-content: start;
}

.student-learn-card-head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.student-learn-card-head .card-title {
  min-width: 0;
  overflow-wrap: anywhere;
}

.student-learn-muted {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

/* Lesson hero */
.student-learn-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-6);
  align-items: end;
  padding: var(--space-8);
  overflow: hidden;
  border-radius: var(--radius-2xl);
  color: var(--text-on-primary);
  background:
    radial-gradient(circle at 90% 10%, var(--color-accent-a48), transparent 28%),
    radial-gradient(ellipse at 85% 120%, var(--color-white-a24), transparent 45%), var(--gradient-brand);
  box-shadow: var(--shadow-primary);
}

.student-learn-hero-copy {
  display: grid;
  gap: var(--space-4);
  justify-items: start;
  min-width: 0;
}

.student-learn-hero-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.student-learn-hero-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: var(--space-0-5) var(--space-2-5);
  border: 1px solid var(--color-white-a32);
  border-radius: var(--radius-pill);
  background: var(--surface-on-brand);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.student-learn-hero-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-5xl);
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
  letter-spacing: var(--letter-spacing-tight);
  overflow-wrap: anywhere;
}

.student-learn-hero-text {
  max-width: 56ch;
  margin: 0;
  color: var(--color-primary-50);
  line-height: var(--line-height-normal);
}

.student-learn-hero-progress {
  display: grid;
  gap: var(--space-2);
  width: min(100%, 440px);
}

.student-learn-hero-progress-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-1) var(--space-3);
  font-size: var(--font-size-sm);
}

.student-learn-hero-progress-row span {
  color: var(--color-primary-50);
  font-weight: var(--font-weight-bold);
}

.student-learn-hero-progress :deep(.progress-bar) {
  background: var(--color-white-a24);
}

.student-learn-hero-progress :deep(.progress-bar > span) {
  background: var(--color-accent-400);
}

/* Lesson content */
.student-learn-cover {
  display: block;
  width: 100%;
  max-height: 280px;
  object-fit: cover;
  border-radius: var(--radius-lg);
  background: var(--color-primary-50);
}

.student-learn-body {
  display: grid;
  gap: var(--space-3);
  max-width: var(--reading-max);
  color: var(--text-primary);
  line-height: var(--line-height-relaxed);
  overflow-wrap: anywhere;
}

.student-learn-body p {
  margin: 0;
  white-space: pre-line;
}

/* Quizzes */
.student-learn-quiz-list {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-learn-quiz {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3) var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
}

.student-learn-quiz-icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}

.student-learn-quiz-copy {
  display: grid;
  flex: 1 1 220px;
  gap: var(--space-1-5);
  min-width: 0;
}

.student-learn-quiz-copy strong {
  overflow-wrap: anywhere;
}

.student-learn-quiz-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.student-learn-practice-link {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3) var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.student-learn-practice-copy {
  display: grid;
  flex: 1 1 260px;
  gap: var(--space-1);
  min-width: 0;
}

.student-learn-practice-copy span {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

/* Recent lessons */
.student-learn-modules {
  display: grid;
  gap: var(--section-gap);
}

.student-module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 200px), 1fr));
  gap: var(--grid-gap);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-module-card {
  position: relative;
  display: grid;
  justify-items: center;
  gap: var(--space-2-5);
  width: 100%;
  height: 100%;
  padding: var(--space-5) var(--space-4) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  background: var(--surface-card);
  color: var(--text-primary);
  text-align: center;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    box-shadow var(--duration-fast) var(--ease-standard),
    transform var(--duration-fast) var(--ease-standard);
}

.student-module-card:hover {
  border-color: var(--border-interactive);
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}

.student-module-card.is-current {
  border-color: var(--color-primary-300);
  background: linear-gradient(180deg, var(--color-primary-50), var(--surface-card) 60%);
}

.student-module-index {
  position: absolute;
  top: var(--space-3);
  left: var(--space-3);
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: var(--radius-round);
  background: var(--color-primary-500);
  color: var(--text-on-primary);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-extrabold);
}

.student-module-icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: var(--radius-round);
  background: var(--surface-sunken);
  color: var(--text-secondary);
}

.student-module-card.is-current .student-module-icon {
  background: var(--color-accent-500);
  color: var(--text-on-accent);
}

.student-module-card.is-complete .student-module-icon {
  background: var(--color-success-soft);
  color: var(--color-success-text);
}

.student-module-title {
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  overflow-wrap: anywhere;
}

.student-module-progress {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
}

.student-module-count {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

/* Side: recorded lessons */
.student-learn-inline-video {
  display: block;
  width: 100%;
  max-height: 360px;
  border-radius: var(--radius-md);
  background: var(--color-ink-900);
}

.student-learn-video-scene {
  position: relative;
  display: grid;
  place-items: end end;
  height: 148px;
  padding-inline: var(--space-6);
  overflow: hidden;
  border-radius: var(--radius-lg);
  background: linear-gradient(180deg, var(--color-info-soft) 0 60%, var(--color-success-soft) 60%);
}

.student-learn-video-play {
  position: absolute;
  top: 50%;
  left: 32%;
  z-index: var(--z-raised);
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border: 0;
  border-radius: var(--radius-round);
  background: var(--color-primary-500);
  color: var(--text-on-primary);
  box-shadow: var(--shadow-primary);
  cursor: pointer;
  transform: translate(-50%, -50%);
  transition: background var(--duration-fast) var(--ease-standard);
}

.student-learn-video-play:hover:not(:disabled) {
  background: var(--color-primary-600);
}

.student-learn-video-play:disabled {
  background: var(--color-ink-300);
  box-shadow: none;
  cursor: not-allowed;
}

.student-learn-video-list {
  display: grid;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.student-learn-video {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
}

.student-learn-video.is-featured {
  border-color: var(--color-primary-200);
  background: var(--color-primary-50);
}

.student-learn-video-copy {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.student-learn-video-copy strong {
  overflow-wrap: anywhere;
}

.student-learn-video-copy p,
.student-learn-video-meta {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.student-learn-video-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1-5);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.student-learn-video-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

/* Side: progress and encouragement */
.student-learn-progress-row {
  display: grid;
  gap: var(--space-2);
}

.student-learn-progress-row + .student-learn-progress-row {
  padding-top: var(--space-4);
  border-top: 1px solid var(--border-subtle);
}

.student-learn-progress-head {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
}

.student-learn-progress-head strong {
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.student-learn-progress-hint {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.student-learn-encourage {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--card-padding);
  border: 1px solid var(--color-accent-200);
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, var(--color-accent-50), var(--color-primary-50));
}

.student-learn-encourage-icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-round);
  background: var(--surface-card);
  color: var(--color-primary-600);
}

.student-learn-encourage strong {
  font-family: var(--font-display);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.student-learn-encourage p {
  margin: var(--space-1) 0 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

@media (max-width: 1200px) {
  .student-learn-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .student-learn-side {
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr));
    align-items: start;
  }
}

@media (max-width: 900px) {
  .student-learn-hero {
    grid-template-columns: minmax(0, 1fr);
    padding: var(--space-6);
  }

  .student-learn-hero-mascot {
    display: none;
  }
}

@media (max-width: 640px) {
  .student-learn-kpi {
    flex-direction: column;
    gap: var(--space-2);
    padding: var(--space-3);
  }

  .student-learn-hero {
    padding: var(--space-5) var(--space-4);
    border-radius: var(--radius-xl);
  }

  .student-learn-hero-title {
    font-size: var(--font-size-3xl);
  }

  .student-learn-hero-copy > .btn {
    width: 100%;
  }

  .student-learn-quiz > .btn,
  .student-learn-practice-link > .btn {
    width: 100%;
  }
}
</style>
