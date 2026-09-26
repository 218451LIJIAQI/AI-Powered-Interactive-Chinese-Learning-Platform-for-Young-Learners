<template>
  <div class="page-stack student-learn-page">
    <DataNotice :using-fallback="usingFallback" :error="noticeError" :success="actionSuccess" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="student-learn-layout">
        <div class="student-learn-main">
          <article class="panel student-course-levels">
            <div class="student-section-heading">
              <strong>{{ t('studentLearn.overview') }}</strong>
            </div>

            <div class="student-level-strip">
              <article
                v-for="level in courseLevels"
                :key="level.id"
                class="student-level-card"
                :class="{ 'is-active': level.active, 'is-locked': level.locked }"
              >
                <div class="student-level-avatar"><IconGlyph :name="level.icon" :size="24" /></div>
                <div class="stack u-gap-2">
                  <strong>{{ level.title }}</strong>
                  <span class="muted">{{ level.subtitle }}</span>
                </div>
                <div class="student-level-progress">
                  <span>{{ level.progress }}</span>
                  <span><IconGlyph :name="levelStatusIcon(level)" :size="16" /></span>
                </div>
              </article>
            </div>
          </article>

          <article class="student-learn-hero">
            <span class="student-learn-cloud student-learn-cloud--1" />
            <span class="student-learn-cloud student-learn-cloud--2" />
            <span class="student-learn-cloud student-learn-cloud--3" />
            <span class="student-learn-pagoda" />
            <span class="student-learn-hill student-learn-hill--back" />
            <span class="student-learn-hill student-learn-hill--mid" />
            <span class="student-learn-hill student-learn-hill--front" />

            <div class="student-learn-hero-copy">
              <div class="chip student-hero-chip">{{ t('studentLearn.lesson', { id: lesson.lessonId }) }}</div>
              <h2>{{ lesson.title }}</h2>
              <p>{{ lessonDescription }}</p>

              <div class="student-learn-progress-block">
                <div class="row-between">
                  <strong>{{ t('studentLearn.progress') }}</strong>
                  <strong>{{ formatPercent(currentLessonPercent) }} - {{ currentLessonProgressLabel }}</strong>
                </div>
                <ProgressBar :progress="currentLessonPercent" />
              </div>

              <button
                class="btn btn-accent student-learn-hero-cta"
                type="button"
                :disabled="!hasActiveLesson"
                @click="openLessonQuiz"
              >
                {{ t('studentLearn.continueUnit') }}
                <span><IconGlyph name="chevronRight" :size="18" /></span>
              </button>
            </div>

            <div class="student-learn-hero-mascot">
              <PandaIllustration :size="214" outfit="#7d56ff" accent="#f6d056" pose="wave" />
            </div>
          </article>

          <section class="student-learn-modules">
            <div class="student-section-heading">
              <strong>{{ t('studentLearn.recentProgress') }}</strong>
            </div>

            <div v-if="learningModules.length" class="student-module-grid">
              <article
                v-for="module in learningModules"
                :key="module.id"
                class="student-module-card"
                :class="{
                  'is-complete': module.complete,
                  'is-current': module.current,
                  'is-locked': module.locked,
                }"
              >
                <div class="student-module-index">
                  <span>{{ module.displayIndex }}</span>
                  <span v-if="module.complete" class="student-module-index-badge is-complete"
                    ><IconGlyph name="status" :size="12"
                  /></span>
                  <span v-else-if="module.locked" class="student-module-index-badge is-locked"
                    ><IconGlyph name="safe" :size="12"
                  /></span>
                  <span v-else-if="module.current" class="student-module-index-badge is-current"
                    ><IconGlyph name="flash" :size="12"
                  /></span>
                </div>

                <div class="student-module-icon" :class="`is-${module.variant}`">
                  <IconGlyph :name="module.icon" :size="24" />
                </div>

                <strong>{{ module.title }}</strong>
                <div class="student-module-status">{{ module.statusLabel }}</div>
                <div class="student-module-progress">
                  <ProgressBar :progress="module.progress" :tone="module.tone" />
                </div>
                <div class="student-module-count">{{ module.count }}</div>
              </article>
            </div>
            <p v-else class="muted u-m-0">{{ t('studentLearn.noProgress') }}</p>

            <button class="student-view-units" type="button" @click="openPractice">
              {{ t('studentLearn.practiceMore') }}
              <span><IconGlyph name="chevronRight" :size="16" /></span>
            </button>
          </section>
        </div>

        <aside class="student-learn-side">
          <article class="panel student-recorded-lesson-card">
            <div class="row u-mb-12">
              <div class="media-badge small"><IconGlyph name="play" :size="18" /></div>
              <strong>{{ t('studentLearn.recordedLesson') }}</strong>
            </div>

            <div class="student-recorded-lesson-scene">
              <span class="student-recorded-bamboo" />
              <span class="student-recorded-pagoda" />
              <span class="student-recorded-hill" />
              <PandaIllustration class="student-recorded-panda" :size="128" outfit="#7d56ff" accent="#f6d056" compact />
              <div class="student-recorded-bubble">
                <strong>Ni hao!</strong>
                <span>{{ t('studentLearn.hello') }}</span>
              </div>
              <button
                class="student-recorded-play"
                type="button"
                :aria-label="t('studentLearn.openRecorded')"
                @click="openRecordedLesson"
              >
                <IconGlyph name="play" :size="20" />
              </button>
            </div>

            <div class="stack u-gap-8">
              <strong>{{ primaryVideo.title }}</strong>
              <p class="muted u-m-0">
                {{ primaryVideoDescription }}
              </p>
            </div>

            <div class="row student-recorded-meta">
              <span>{{ videoDurationLabel }}</span>
              <span>|</span>
              <span>{{ videoProgressLabel }}</span>
            </div>

            <button
              class="btn btn-secondary btn-block"
              type="button"
              :disabled="videoActionLoading || !hasPlayableVideo"
              @click="openRecordedLesson"
            >
              {{ videoWatchActionLabel }}
            </button>
            <button
              class="btn btn-accent btn-block"
              type="button"
              :disabled="videoActionLoading || !hasPlayableVideo || videoProgress?.completed"
              @click="markRecordedLessonComplete"
            >
              {{ videoCompleteActionLabel }}
            </button>
          </article>

          <article class="panel student-progress-side-card">
            <div class="row u-mb-12">
              <div class="media-badge small"><IconGlyph name="progress" :size="18" /></div>
              <strong>{{ t('studentLearn.myProgress') }}</strong>
            </div>

            <div class="student-progress-row">
              <div class="row-between">
                <div class="row">
                  <span class="student-progress-icon"><IconGlyph name="learn" :size="18" /></span>
                  <strong>{{ t('studentLearn.currentProgress') }}</strong>
                </div>
                <strong>{{ formatPercent(currentLessonPercent) }}</strong>
              </div>
              <ProgressBar :progress="currentLessonPercent" tone="success" />
              <span class="muted">{{ currentLessonProgressLabel }}</span>
            </div>

            <div class="muted-divider" />

            <div class="student-progress-row">
              <div class="row-between">
                <div class="row">
                  <span class="student-progress-icon"><IconGlyph name="progress" :size="18" /></span>
                  <strong>{{ t('studentLearn.overallProgress') }}</strong>
                </div>
                <strong>{{ formatPercent(progress.overallCompletionPercent) }}</strong>
              </div>
              <ProgressBar :progress="progress.overallCompletionPercent" />
              <span class="muted">{{ overallLessonCountLabel }}</span>
            </div>
          </article>

          <article class="student-side-encourage">
            <div>
              <strong>{{ t('studentLearn.keepGoing', { name: studentName }) }}</strong>
              <p>{{ t('studentLearn.encouragement') }}</p>
            </div>
            <div class="student-side-encourage-fish"><IconGlyph name="fun" :size="24" /></div>
          </article>
        </aside>
      </section>
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
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useResource } from '@/composables/useResource'
import { studentLessonPlayer, studentProgress as studentProgressDemo } from '@/demo/data'
import { studentLearnMessages } from '@/plugins/studentLearnMessages'
import { useAuthStore } from '@/stores/auth'
import type { RecordedLessonVideoResponse, StudentLessonPlayerResponse, VideoProgressResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { clampPercent, formatPercent, nonNegativeInteger, toFiniteNumber } from '@/utils/format'
import { openSafeHttpUrl, safeHttpUrl } from '@/utils/url'

const { t } = useI18n({
  messages: {
    en: { studentLearn: studentLearnMessages.en },
    ms: { studentLearn: studentLearnMessages.ms },
    zh: { studentLearn: studentLearnMessages.zh },
  },
})

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const requestedVideoId = computed(() => numericQuery(route.query.videoId))
const progressResource = useResource(() => studentApi.progress(), studentProgressDemo)
const emptyProgress = {
  ...studentProgressDemo,
  completedLessons: 0,
  totalLessonsStarted: 0,
  quizAverage: 0,
  overallCompletionPercent: 0,
  weakAreas: [],
  recentLessons: [],
}
const progress = computed(
  () => progressResource.data.value ?? (progressResource.usingFallback.value ? studentProgressDemo : emptyProgress),
)
const lessonCandidateIds = computed(() => {
  const lessonIds = [
    numericQuery(route.query.lessonId),
    ...progress.value.recentLessons.map((recentLesson) => recentLesson.lessonId),
  ]

  return Array.from(new Set(lessonIds.filter((lessonId): lessonId is number => Boolean(lessonId && lessonId > 0))))
})
const selectedLessonId = computed(() => lessonCandidateIds.value[0] ?? null)
const fallbackLesson = (): StudentLessonPlayerResponse => ({
  ...studentLessonPlayer,
  lessonId: selectedLessonId.value ?? 0,
})
const resource = useResource(loadLessonPlayer, fallbackLesson)

const emptyLesson = {
  ...studentLessonPlayer,
  lessonId: 0,
  title: t('studentLearn.lessonUnavailable'),
  description: t('studentLearn.unassigned'),
  completionPercent: 0,
  liveClasses: [],
  recordedVideos: [],
}
const lesson = computed(() => resource.data.value ?? (resource.usingFallback.value ? studentLessonPlayer : emptyLesson))
const lessonDescription = computed(() => lesson.value.description || t('studentLearn.noLessonDetails'))
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const videoProgress = ref<VideoProgressResponse | null>(null)
const videoProgressLoading = ref(false)
const videoActionLoading = ref(false)

const pageLoading = computed(() => resource.loading.value || progressResource.loading.value)
const pageError = computed(() => resource.error.value || progressResource.error.value)
const noticeError = computed(() => actionError.value || pageError.value)
const usingFallback = computed(() => resource.usingFallback.value || progressResource.usingFallback.value)
const studentName = computed(
  () => auth.currentUser?.studentProfile?.displayName || auth.displayName.split(' ')[0] || t('studentLearn.learner'),
)
function emptyVideo(lessonId: number): RecordedLessonVideoResponse {
  return {
    id: 0,
    lessonId,
    title: t('studentLearn.videoUnavailable'),
    description: t('studentLearn.videoUnavailableHint'),
    sourceType: 'UPLOAD',
    externalVideoUrl: null,
    streamUrl: null,
    durationSeconds: 0,
    sortOrder: 0,
    active: false,
    createdAt: '',
    updatedAt: '',
  }
}
const primaryVideo = computed(
  () =>
    lesson.value.recordedVideos.find((video) => video.id === requestedVideoId.value) ??
    lesson.value.recordedVideos[0] ??
    (resource.usingFallback.value ? studentLessonPlayer.recordedVideos[0] : undefined) ??
    emptyVideo(lesson.value.lessonId),
)
const primaryVideoDescription = computed(() => primaryVideo.value.description || t('studentLearn.noVideoDetails'))

const videoDurationLabel = computed(() => {
  const durationSeconds = nonNegativeInteger(primaryVideo.value?.durationSeconds)
  if (durationSeconds > 0) return formatDuration(durationSeconds)
  return t('studentLearn.durationUnavailable')
})
const playableVideoUrl = computed(() => {
  const streamUrl = safeHttpUrl(primaryVideo.value.streamUrl, { allowRelative: true, sameOriginOnly: true })
  if (streamUrl) return streamUrl

  return safeHttpUrl(primaryVideo.value.externalVideoUrl)
})
const hasPlayableVideo = computed(() => Boolean(primaryVideo.value.id && playableVideoUrl.value))
const currentLessonPercent = computed(() => clampPercent(lesson.value.completionPercent))
const videoProgressLabel = computed(() => {
  if (videoProgressLoading.value) return t('studentLearn.loadingProgress')
  if (!primaryVideo.value.id) return t('studentLearn.noVideo')
  if (!videoProgress.value) return t('studentLearn.notStarted')
  return videoProgress.value.completed
    ? t('studentLearn.completePercent')
    : t('studentLearn.watchedPercent', { progress: clampPercent(videoProgress.value.progressPercentage) })
})
const videoWatchActionLabel = computed(() =>
  videoActionLoading.value ? t('studentLearn.savingProgress') : t('studentLearn.startWatching'),
)
const videoCompleteActionLabel = computed(() =>
  videoProgress.value?.completed ? t('studentLearn.completed') : t('studentLearn.markComplete'),
)
const currentLessonProgressLabel = computed(() => {
  if (currentLessonPercent.value >= 100) return t('studentLearn.completed')
  if (currentLessonPercent.value > 0) return t('studentLearn.inProgress')
  return t('studentLearn.notStarted')
})
const hasActiveLesson = computed(() => lesson.value.lessonId > 0)
const overallLessonCountLabel = computed(() => {
  const completed = progress.value.completedLessons
  const started = progress.value.totalLessonsStarted
  if (!completed && !started) return t('studentLearn.noLessons')
  return t('studentLearn.lessonCounts', { completed, started })
})

type LevelStatusIcon = 'history' | 'safe' | 'star'
type ModuleIcon = 'flash' | 'history' | 'status'
type ModuleTone = 'primary' | 'success'
type ModuleVariant = 'greeting' | 'listening' | 'pinyin' | 'tones'

interface CourseLevel {
  id: number
  title: string
  subtitle: string
  progress: string
  icon: string
  active: boolean
  locked: boolean
}

interface LearningModule {
  id: number
  displayIndex: number
  title: string
  icon: ModuleIcon
  statusLabel: string
  progress: number
  count: string
  complete: boolean
  current: boolean
  locked: boolean
  tone: ModuleTone
  variant: ModuleVariant
}

const courseLevels = computed<CourseLevel[]>(() => [
  {
    id: 1,
    title: t('studentLearn.started'),
    subtitle: t('studentLearn.lessonsOpened'),
    progress: String(nonNegativeInteger(progress.value.totalLessonsStarted)),
    icon: 'learn',
    active: nonNegativeInteger(progress.value.totalLessonsStarted) > 0,
    locked: false,
  },
  {
    id: 2,
    title: t('studentLearn.completed'),
    subtitle: t('studentLearn.lessonsFinished'),
    progress: String(nonNegativeInteger(progress.value.completedLessons)),
    icon: 'status',
    active: nonNegativeInteger(progress.value.completedLessons) > 0,
    locked: false,
  },
  {
    id: 3,
    title: t('studentLearn.quizAverage'),
    subtitle: t('studentLearn.assessmentScore'),
    progress: formatPercent(progress.value.quizAverage),
    icon: 'star',
    active: clampPercent(progress.value.quizAverage) > 0,
    locked: false,
  },
  {
    id: 4,
    title: t('studentLearn.overallProgress'),
    subtitle: progress.value.lastActivitySummary || t('studentLearn.courseCompletion'),
    progress: formatPercent(progress.value.overallCompletionPercent),
    icon: 'progress',
    active: clampPercent(progress.value.overallCompletionPercent) > 0,
    locked: false,
  },
])
const learningModules = computed<LearningModule[]>(() =>
  progress.value.recentLessons.map((recentLesson, index) => {
    const completionPercent = clampPercent(recentLesson.completionPercent)
    const complete = recentLesson.completed || completionPercent >= 100
    const current = recentLesson.lessonId === lesson.value.lessonId || (!complete && index === 0)
    return {
      id: recentLesson.lessonId,
      displayIndex: index + 1,
      title: recentLesson.lessonTitle,
      icon: iconForProgress(completionPercent, complete),
      statusLabel: progressLabel(completionPercent, complete),
      progress: completionPercent,
      count: progressLabel(completionPercent, complete),
      complete,
      current,
      locked: false,
      tone: complete ? 'success' : 'primary',
      variant: variantForProgress(completionPercent, complete),
    }
  }),
)

watch(
  lessonCandidateIds,
  () => {
    videoProgress.value = null
    void resource.reload()
  },
  { deep: true },
)

watch(
  () => primaryVideo.value.id,
  () => {
    void loadVideoProgress()
  },
  { immediate: true },
)

function numericQuery(value: unknown): number | null {
  const rawValue = Array.isArray(value) ? value[0] : value
  if (rawValue === null || rawValue === undefined || rawValue === '') return null

  const parsed = Number(rawValue)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

function responseStatus(reason: unknown): number | null {
  return (reason as { response?: { status?: number } } | null)?.response?.status ?? null
}

function iconForProgress(completionPercent: number, completed: boolean): ModuleIcon {
  if (completed || completionPercent >= 100) return 'status'
  if (completionPercent > 0) return 'flash'
  return 'history'
}

function progressLabel(completionPercent: number, completed: boolean): string {
  if (completed || completionPercent >= 100) return t('studentLearn.completed')
  if (completionPercent > 0) return t('studentLearn.inProgress')
  return t('studentLearn.notStarted')
}

function variantForProgress(completionPercent: number, completed: boolean): ModuleVariant {
  if (completed || completionPercent >= 100) return 'pinyin'
  if (completionPercent >= 50) return 'listening'
  if (completionPercent > 0) return 'tones'
  return 'greeting'
}

async function loadLessonPlayer(): Promise<StudentLessonPlayerResponse> {
  for (const lessonId of lessonCandidateIds.value) {
    try {
      return await studentApi.lessonPlayer(lessonId)
    } catch (reason) {
      if (responseStatus(reason) !== 404) {
        throw reason
      }
    }
  }

  return emptyLesson
}

function formatDuration(totalSeconds: number): string {
  const safeSeconds = nonNegativeInteger(totalSeconds)
  const minutes = Math.floor(safeSeconds / 60)
  const seconds = safeSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
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
  videoProgress.value = null
  if (!primaryVideo.value.id || resource.usingFallback.value) return

  videoProgressLoading.value = true
  try {
    videoProgress.value = await studentApi.videoProgress(primaryVideo.value.id)
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentLearn.loadVideoError'))
  } finally {
    videoProgressLoading.value = false
  }
}

async function openRecordedLesson(): Promise<void> {
  if (videoActionLoading.value) return
  actionError.value = null
  actionSuccess.value = null
  const url = playableVideoUrl.value
  if (openSafeHttpUrl(url, { allowRelative: true, sameOriginOnly: primaryVideo.value.sourceType === 'UPLOAD' })) {
    if (resource.usingFallback.value || !primaryVideo.value.id) {
      actionSuccess.value = t('studentLearn.openingVideo')
      return
    }

    videoActionLoading.value = true
    try {
      videoProgress.value = await studentApi.saveVideoProgress(
        primaryVideo.value.id,
        startProgressPayload(primaryVideo.value),
      )
      actionSuccess.value = t('studentLearn.openingAndSaving')
    } catch (reason) {
      actionError.value = resolveApiError(reason, t('studentLearn.openedSaveError'))
    } finally {
      videoActionLoading.value = false
    }
    return
  }

  actionError.value = t('studentLearn.noPlayableLink')
}

async function markRecordedLessonComplete(): Promise<void> {
  if (videoActionLoading.value) return
  actionError.value = null
  actionSuccess.value = null
  if (!hasPlayableVideo.value) {
    actionError.value = t('studentLearn.noPlayableLink')
    return
  }

  if (resource.usingFallback.value) {
    actionError.value = t('studentLearn.previewComplete')
    return
  }

  videoActionLoading.value = true
  try {
    videoProgress.value = await studentApi.completeVideo(primaryVideo.value.id)
    await Promise.all([resource.reload(), progressResource.reload()])
    actionSuccess.value = t('studentLearn.markedComplete')
  } catch (reason) {
    actionError.value = resolveApiError(reason, t('studentLearn.completeError'))
  } finally {
    videoActionLoading.value = false
  }
}

function openLessonQuiz(): void {
  const lessonId = toFiniteNumber(lesson.value.lessonId)
  if (lessonId <= 0) {
    openQuiz()
    return
  }

  openStudentQuiz(lessonId)
}

function reloadAll(): void {
  void resource.reload()
  void progressResource.reload()
  void loadVideoProgress()
}

function levelStatusIcon(level: CourseLevel): LevelStatusIcon {
  if (level.locked) return 'safe'
  return level.active ? 'star' : 'history'
}

function openPractice(): void {
  void router.push('/student/practice')
}

function openQuiz(): void {
  void router.push('/student/quiz')
}

function openStudentQuiz(lessonId: number): void {
  void router.push({
    name: 'student-quiz',
    query: { lessonId: String(lessonId) },
  })
}
</script>
