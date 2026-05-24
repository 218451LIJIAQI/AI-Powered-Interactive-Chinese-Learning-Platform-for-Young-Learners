<template>
  <div class="page-stack student-learn-page">
    <DataNotice :using-fallback="usingFallback" :error="actionError || pageError" :success="actionSuccess" />

    <ResourceSection :loading="pageLoading" :error="pageError" @retry="reloadAll">
      <section class="student-learn-layout">
        <div class="student-learn-main">
          <article class="panel student-course-levels">
            <div class="student-section-heading">
              <strong>Learning Overview</strong>
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
                  <span><IconGlyph :name="level.locked ? 'safe' : level.active ? 'star' : 'history'" :size="16" /></span>
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
              <div class="chip student-hero-chip">Lesson {{ lesson.lessonId }}</div>
              <h2>{{ lesson.title }}</h2>
              <p>{{ lesson.description || 'Lesson details are not available yet.' }}</p>

              <div class="student-learn-progress-block">
                <div class="row-between">
                  <strong>Progress</strong>
                  <strong>{{ formatPercent(lesson.completionPercent) }} - {{ currentLessonProgressLabel }}</strong>
                </div>
                <ProgressBar :progress="lesson.completionPercent" />
              </div>

              <button class="btn btn-accent student-learn-hero-cta" type="button" :disabled="!hasActiveLesson" @click="openLessonQuiz">
                Continue Unit
                <span><IconGlyph name="chevronRight" :size="18" /></span>
              </button>
            </div>

            <div class="student-learn-hero-mascot">
              <PandaIllustration :size="214" outfit="#7d56ff" accent="#f6d056" pose="wave" />
            </div>
          </article>

          <section class="student-learn-modules">
            <div class="student-section-heading">
              <strong>Recent Lesson Progress</strong>
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
                  <span v-if="module.complete" class="student-module-index-badge is-complete"><IconGlyph name="status" :size="12" /></span>
                  <span v-else-if="module.locked" class="student-module-index-badge is-locked"><IconGlyph name="safe" :size="12" /></span>
                  <span v-else-if="module.current" class="student-module-index-badge is-current"><IconGlyph name="flash" :size="12" /></span>
                </div>

                <div class="student-module-icon" :class="`is-${module.variant}`"><IconGlyph :name="module.icon" :size="24" /></div>

                <strong>{{ module.title }}</strong>
                <div class="student-module-status">{{ module.statusLabel }}</div>
                <div class="student-module-progress">
                  <ProgressBar :progress="module.progress" :tone="module.tone" />
                </div>
                <div class="student-module-count">{{ module.count }}</div>
              </article>
            </div>
            <p v-else class="muted u-m-0">No recent lesson progress yet.</p>

            <button class="student-view-units" type="button" @click="router.push('/student/practice')">
              Practice More Lessons
              <span><IconGlyph name="chevronRight" :size="16" /></span>
            </button>
          </section>
        </div>

        <aside class="student-learn-side">
          <article class="panel student-recorded-lesson-card">
            <div class="row u-mb-12">
              <div class="media-badge small"><IconGlyph name="play" :size="18" /></div>
              <strong>Recorded Lesson</strong>
            </div>

            <div class="student-recorded-lesson-scene">
              <span class="student-recorded-bamboo" />
              <span class="student-recorded-pagoda" />
              <span class="student-recorded-hill" />
              <PandaIllustration class="student-recorded-panda" :size="128" outfit="#7d56ff" accent="#f6d056" compact />
              <div class="student-recorded-bubble">
                <strong>Ni hao!</strong>
                <span>Hello!</span>
              </div>
              <button class="student-recorded-play" type="button" aria-label="Open recorded lesson" @click="openRecordedLesson">
                <IconGlyph name="play" :size="20" />
              </button>
            </div>

            <div class="stack u-gap-8">
              <strong>{{ primaryVideo.title }}</strong>
              <p class="muted u-m-0">
                {{ primaryVideo.description || 'Recorded lesson details are not available yet.' }}
              </p>
            </div>

            <div class="row student-recorded-meta">
              <span>{{ videoDurationLabel }}</span>
              <span>|</span>
              <span>{{ videoProgressLabel }}</span>
            </div>

            <button class="btn btn-secondary btn-block" type="button" :disabled="videoActionLoading || !hasPlayableVideo" @click="openRecordedLesson">
              {{ videoActionLoading ? 'Saving progress...' : 'Start Watching' }}
            </button>
            <button class="btn btn-accent btn-block" type="button" :disabled="videoActionLoading || !hasPlayableVideo || videoProgress?.completed" @click="markRecordedLessonComplete">
              {{ videoProgress?.completed ? 'Completed' : 'Mark Complete' }}
            </button>
          </article>

          <article class="panel student-progress-side-card">
            <div class="row u-mb-12">
              <div class="media-badge small"><IconGlyph name="progress" :size="18" /></div>
              <strong>My Progress</strong>
            </div>

            <div class="student-progress-row">
              <div class="row-between">
                <div class="row">
                  <span class="student-progress-icon"><IconGlyph name="learn" :size="18" /></span>
                  <strong>Current Lesson Progress</strong>
                </div>
                <strong>{{ formatPercent(lesson.completionPercent) }}</strong>
              </div>
              <ProgressBar :progress="lesson.completionPercent" tone="success" />
              <span class="muted">{{ currentLessonProgressLabel }}</span>
            </div>

            <div class="muted-divider" />

            <div class="student-progress-row">
              <div class="row-between">
                <div class="row">
                  <span class="student-progress-icon"><IconGlyph name="progress" :size="18" /></span>
                  <strong>Overall Course Progress</strong>
                </div>
                <strong>{{ formatPercent(progress.overallCompletionPercent) }}</strong>
              </div>
              <ProgressBar :progress="progress.overallCompletionPercent" />
              <span class="muted">{{ overallLessonCountLabel }}</span>
            </div>
          </article>

          <article class="student-side-encourage">
            <div>
              <strong>Keep going, {{ studentName }}!</strong>
              <p>Every lesson brings you closer to fluency.</p>
            </div>
            <div class="student-side-encourage-fish"><IconGlyph name="fun" :size="24" /></div>
          </article>
        </aside>
      </section>
    </ResourceSection>
  </div>
</template>

<script setup lang="ts">
/**
 * Student curriculum / lesson player surface.
 *
 * <p>Renders module / lesson navigation, the lesson body, and the
 * "next best step" recommendations. Paid content stays gated by the
 * backend entitlement check — the page never tries to render
 * locked lesson body text, so a malicious user cannot inspect the
 * DOM to bypass the paywall.</p>
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { studentApi } from '@/api/services'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PandaIllustration from '@/components/common/PandaIllustration.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import ResourceSection from '@/components/dashboard/ResourceSection.vue'
import { useResource } from '@/composables/useResource'
import { studentLessonPlayer, studentProgress as studentProgressDemo } from '@/demo/data'
import { useAuthStore } from '@/stores/auth'
import type { RecordedLessonVideoResponse, StudentLessonPlayerResponse, VideoProgressResponse } from '@/types/api'
import { resolveApiError } from '@/utils/apiError'
import { formatPercent } from '@/utils/format'
import { safeHttpUrl } from '@/utils/url'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const requestedVideoId = computed(() => numericQuery(route.query.videoId))
const progressResource = useResource(() => studentApi.progress(), studentProgressDemo)
const emptyProgress = { ...studentProgressDemo, completedLessons: 0, totalLessonsStarted: 0, quizAverage: 0, overallCompletionPercent: 0, weakAreas: [], recentLessons: [] }
const progress = computed(() => progressResource.data.value ?? (progressResource.usingFallback.value ? studentProgressDemo : emptyProgress))
const lessonCandidateIds = computed(() => {
  const lessonIds = [
    numericQuery(route.query.lessonId),
    ...progress.value.recentLessons.map((recentLesson) => recentLesson.lessonId),
  ]

  return Array.from(new Set(lessonIds.filter((lessonId): lessonId is number => Boolean(lessonId && lessonId > 0))))
})
const selectedLessonId = computed(() => lessonCandidateIds.value[0] ?? null)
const fallbackLesson = (): StudentLessonPlayerResponse => ({ ...studentLessonPlayer, lessonId: selectedLessonId.value ?? 0 })
const resource = useResource(loadLessonPlayer, fallbackLesson)

const emptyLesson = {
  ...studentLessonPlayer,
  lessonId: 0,
  title: 'Lesson unavailable',
  description: 'A published lesson has not been assigned to this learner yet.',
  completionPercent: 0,
  liveClasses: [],
  recordedVideos: [],
}
const lesson = computed(() => resource.data.value ?? (resource.usingFallback.value ? studentLessonPlayer : emptyLesson))
const actionError = ref<string | null>(null)
const actionSuccess = ref<string | null>(null)
const videoProgress = ref<VideoProgressResponse | null>(null)
const videoProgressLoading = ref(false)
const videoActionLoading = ref(false)

const pageLoading = computed(() => resource.loading.value || progressResource.loading.value)
const pageError = computed(() => resource.error.value || progressResource.error.value)
const usingFallback = computed(() => resource.usingFallback.value || progressResource.usingFallback.value)
const studentName = computed(() => auth.currentUser?.studentProfile?.displayName ?? auth.displayName.split(' ')[0] ?? 'Learner')
const primaryVideo = computed(
  () =>
    lesson.value.recordedVideos.find((video) => video.id === requestedVideoId.value) ??
    lesson.value.recordedVideos[0] ??
    (resource.usingFallback.value
      ? (studentLessonPlayer.recordedVideos[0] ?? {
          id: 0,
          lessonId: lesson.value.lessonId,
          title: 'Video unavailable',
          description: 'Recorded video is unavailable right now.',
          sourceType: 'UPLOAD',
          externalVideoUrl: null,
          streamUrl: null,
          durationSeconds: 0,
          sortOrder: 0,
          active: false,
          createdAt: '',
          updatedAt: '',
        })
      : {
          id: 0,
          lessonId: lesson.value.lessonId,
          title: 'Video unavailable',
          description: 'Recorded video is unavailable right now.',
          sourceType: 'UPLOAD',
          externalVideoUrl: null,
          streamUrl: null,
          durationSeconds: 0,
          sortOrder: 0,
          active: false,
          createdAt: '',
          updatedAt: '',
        }),
)

const videoDurationLabel = computed(() => {
  const durationSeconds = primaryVideo.value?.durationSeconds ?? 0
  if (durationSeconds > 0) return formatDuration(durationSeconds)
  return 'Duration unavailable'
})
const playableVideoUrl = computed(() => safeHttpUrl(primaryVideo.value.streamUrl || primaryVideo.value.externalVideoUrl))
const hasPlayableVideo = computed(() => Boolean(primaryVideo.value.id && playableVideoUrl.value))
const videoProgressLabel = computed(() => {
  if (videoProgressLoading.value) return 'Loading progress'
  if (!primaryVideo.value.id) return 'No video'
  if (!videoProgress.value) return 'Not started'
  return videoProgress.value.completed ? '100% complete' : `${Math.round(videoProgress.value.progressPercentage)}% watched`
})
const currentLessonProgressLabel = computed(() => {
  if (lesson.value.completionPercent >= 100) return 'Completed'
  if (lesson.value.completionPercent > 0) return 'In progress'
  return 'Not started'
})
const hasActiveLesson = computed(() => lesson.value.lessonId > 0)
const overallLessonCountLabel = computed(() => {
  const completed = progress.value.completedLessons
  const started = progress.value.totalLessonsStarted
  if (!completed && !started) return 'No lessons started yet'
  return `${completed} completed - ${started} started`
})

const courseLevels = computed(() => [
  {
    id: 1,
    title: 'Started',
    subtitle: 'Lessons opened',
    progress: String(progress.value.totalLessonsStarted),
    icon: 'learn',
    active: progress.value.totalLessonsStarted > 0,
    locked: false,
  },
  {
    id: 2,
    title: 'Completed',
    subtitle: 'Lessons finished',
    progress: String(progress.value.completedLessons),
    icon: 'status',
    active: progress.value.completedLessons > 0,
    locked: false,
  },
  {
    id: 3,
    title: 'Quiz Average',
    subtitle: 'Assessment score',
    progress: formatPercent(progress.value.quizAverage),
    icon: 'star',
    active: progress.value.quizAverage > 0,
    locked: false,
  },
  {
    id: 4,
    title: 'Overall Progress',
    subtitle: progress.value.lastActivitySummary || 'Course completion',
    progress: formatPercent(progress.value.overallCompletionPercent),
    icon: 'progress',
    active: progress.value.overallCompletionPercent > 0,
    locked: false,
  },
])
const learningModules = computed(() =>
  progress.value.recentLessons.map((recentLesson, index) => {
    const complete = recentLesson.completionPercent >= 100
    const current = recentLesson.lessonId === lesson.value.lessonId || (!complete && index === 0)
    return {
      id: recentLesson.lessonId,
      displayIndex: index + 1,
      title: recentLesson.lessonTitle,
      icon: iconForProgress(recentLesson.completionPercent, recentLesson.completed),
      statusLabel: progressLabel(recentLesson.completionPercent, recentLesson.completed),
      progress: recentLesson.completionPercent,
      count: progressLabel(recentLesson.completionPercent, recentLesson.completed),
      complete,
      current,
      locked: false,
      tone: complete ? ('success' as const) : ('primary' as const),
      variant: variantForProgress(recentLesson.completionPercent, recentLesson.completed),
    }
  }),
)

watch(lessonCandidateIds, () => {
  videoProgress.value = null
  void resource.reload()
}, { deep: true })

watch(
  () => primaryVideo.value.id,
  () => {
    void loadVideoProgress()
  },
  { immediate: true },
)

function numericQuery(value: unknown) {
  const rawValue = Array.isArray(value) ? value[0] : value
  if (rawValue === null || rawValue === undefined || rawValue === '') return null

  const parsed = Number(rawValue)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

function responseStatus(reason: unknown) {
  return (reason as { response?: { status?: number } } | null)?.response?.status ?? null
}

function iconForProgress(completionPercent: number, completed: boolean) {
  if (completed || completionPercent >= 100) return 'status'
  if (completionPercent > 0) return 'flash'
  return 'history'
}

function progressLabel(completionPercent: number, completed: boolean) {
  if (completed || completionPercent >= 100) return 'Completed'
  if (completionPercent > 0) return 'In progress'
  return 'Not started'
}

function variantForProgress(completionPercent: number, completed: boolean) {
  if (completed || completionPercent >= 100) return 'pinyin'
  if (completionPercent >= 50) return 'listening'
  if (completionPercent > 0) return 'tones'
  return 'greeting'
}

async function loadLessonPlayer() {
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

function formatDuration(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

function startProgressPayload(video: RecordedLessonVideoResponse) {
  const totalSeconds = video.durationSeconds ?? undefined
  const watchedSeconds = totalSeconds ? Math.max(1, Math.min(30, Math.floor(totalSeconds * 0.1))) : undefined
  const progressPercentage = totalSeconds && watchedSeconds ? Math.min(95, Math.max(1, Math.round((watchedSeconds / totalSeconds) * 100))) : 1

  return {
    watchedSeconds,
    totalSeconds,
    progressPercentage,
    completed: false,
  }
}

async function loadVideoProgress() {
  videoProgress.value = null
  if (!primaryVideo.value.id || resource.usingFallback.value) return

  videoProgressLoading.value = true
  try {
    videoProgress.value = await studentApi.videoProgress(primaryVideo.value.id)
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to load recorded lesson progress.')
  } finally {
    videoProgressLoading.value = false
  }
}

async function openRecordedLesson() {
  actionError.value = null
  actionSuccess.value = null
  const url = playableVideoUrl.value
  if (url) {
    window.open(url, '_blank', 'noopener,noreferrer')
    if (resource.usingFallback.value || !primaryVideo.value.id) {
      actionSuccess.value = 'Opening the recorded lesson in a new tab.'
      return
    }

    videoActionLoading.value = true
    try {
      videoProgress.value = await studentApi.saveVideoProgress(primaryVideo.value.id, startProgressPayload(primaryVideo.value))
      actionSuccess.value = 'Opening the recorded lesson and saving your watch progress.'
    } catch (reason) {
      actionError.value = resolveApiError(reason, 'Recorded lesson opened, but progress could not be saved.')
    } finally {
      videoActionLoading.value = false
    }
    return
  }

  actionError.value = 'This recorded lesson does not have a playable video link yet.'
}

async function markRecordedLessonComplete() {
  actionError.value = null
  actionSuccess.value = null
  if (!hasPlayableVideo.value) {
    actionError.value = 'This recorded lesson does not have a playable video link yet.'
    return
  }

  if (resource.usingFallback.value) {
    actionError.value = 'Preview recorded lessons cannot save completion progress. Use a live student account to update progress.'
    return
  }

  videoActionLoading.value = true
  try {
    videoProgress.value = await studentApi.completeVideo(primaryVideo.value.id)
    await Promise.all([resource.reload(), progressResource.reload()])
    actionSuccess.value = 'Recorded lesson marked complete. Your progress has been refreshed.'
  } catch (reason) {
    actionError.value = resolveApiError(reason, 'Unable to mark this recorded lesson complete.')
  } finally {
    videoActionLoading.value = false
  }
}

function openLessonQuiz() {
  if (!lesson.value.lessonId) {
    void router.push('/student/quiz')
    return
  }

  void router.push({
    name: 'student-quiz',
    query: { lessonId: String(lesson.value.lessonId) },
  })
}

function reloadAll() {
  void resource.reload()
  void progressResource.reload()
  void loadVideoProgress()
}
</script>


