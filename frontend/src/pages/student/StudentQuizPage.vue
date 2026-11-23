<template>
  <div class="class-focus-page class-quiz-page">
    <FocusTopBar
      :label="t('classes.lessonQuiz')"
      :close-label="t('classPlayer.quiz.leaveLabel')"
      :close-disabled="submitting || leaving"
      :progress="topProgress"
      @close="requestLeave"
    />

    <!-- Result -->
    <QuizResultStage
      v-if="result"
      :result="result"
      :just-submitted="justSubmitted"
      :playable-lesson="playableLesson"
      :lesson-route="lessonRoute"
      :starting="starting"
      :action-error="actionError"
      :lesson-error="lessonError"
      @retake="retake"
      @retry-lesson="readLesson"
    />

    <!-- An attempt in progress (keyed by attemptId so each attempt mounts with fresh local state) -->
    <QuizAttemptStage
      v-else-if="session && session.questions.length > 0"
      :key="session.attemptId"
      :session="session"
      :account="account"
      :leave-open="leaveOpen"
      :leaving="leaving"
      :external-error="actionError"
      @progress="attemptProgress = $event"
      @submitting="submitting = $event"
      @submitted="onSubmitted"
      @toast="showToast"
      @clear-external-error="actionError = null"
    />

    <div v-else class="focus-stage">
      <h1 class="sr-only">{{ t('classes.lessonQuiz') }}</h1>
      <StatePanel
        v-if="starting || loadingResult"
        heading-tag="h2"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('classPlayer.quiz.loadingMessage')"
      />
      <ClassLoadError
        v-else
        :title="t('classPlayer.quiz.loadErrorTitle')"
        :message="actionError"
        :back-to="{ name: 'student-classes' }"
        :back-label="t('nav.classes')"
        :on-retry="invalidAttempt || (requestedAttempt === null && quizId === null) ? undefined : openRequestedAttempt"
      />
    </div>

    <ConfirmDialog
      :open="leaveOpen"
      :busy="leaving"
      :title="t('classPlayer.quiz.leaveTitle')"
      :message="leaveMessage"
      :confirm-label="t('classes.leave')"
      :cancel-label="t('classes.keepGoing')"
      @confirm="leave"
      @cancel="leaveOpen = false"
    />
    <AppToast :message="toast" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import { getAuthSessionVersion } from '@/api/http'
import ClassLoadError from '@/components/classes/ClassLoadError.vue'
import QuizAttemptStage from '@/components/classes/QuizAttemptStage.vue'
import QuizResultStage from '@/components/classes/QuizResultStage.vue'
import AppToast from '@/components/common/AppToast.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import FocusTopBar from '@/components/layout/FocusTopBar.vue'
import { usePageActive, usePageRedirect } from '@/composables/usePageNavigation'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useToast } from '@/composables/useToast'
import { classPlayerMessages } from '@/plugins/classPlayerMessages'
import { classesMessages } from '@/plugins/classesMessages'
import type { QuizResult, QuizSession, StudentLesson } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { classLessonRoute } from '@/utils/classes'
import { parseRecordId } from '@/utils/recordId'
import '@/styles/classes.css'

const { t } = useLocalMessages({
  classPlayer: classPlayerMessages,
  classes: classesMessages,
})
const route = useRoute()
const router = useRouter()
const pageActive = usePageActive()
const { toast, showToast } = useToast()

const quizId = computed(() => parseRecordId(route.params.quizId))
const requestedAttempt = computed(() => parseRecordId(route.query.attempt))
const invalidAttempt = computed(() => route.query.attempt !== undefined && requestedAttempt.value === null)

const session = ref<QuizSession | null>(null)
const result = ref<QuizResult | null>(null)
const startingQuizId = ref<number | null>(null)
const starting = computed(() => startingQuizId.value !== null)
const loadingResult = ref(false)
const justSubmitted = ref(false)
const submitting = ref(false)
const attemptProgress = ref(0)
const actionError = ref<string | null>(null)
const leaveOpen = ref(false)
const leaving = ref(false)
let viewVersion = 0

const lesson = ref<StudentLesson | null>(null)
const lessonError = ref<string | null>(null)
const playableLesson = computed(() => (lesson.value?.available && !lesson.value.archived ? lesson.value : null))
let lessonRequest = 0
const account = getAuthSessionVersion()

const ownsView = (version: number): boolean => version === viewVersion && account === getAuthSessionVersion()

const lessonRoute = computed(() => {
  const quiz = result.value?.quiz ?? session.value?.quiz
  return quiz ? classLessonRoute(quiz.classId, quiz.lessonId) : { name: 'student-classes' }
})

const topProgress = computed(() => {
  if (result.value) return 100
  if (!session.value || session.value.questions.length === 0) return null
  return attemptProgress.value
})

const leaveMessage = computed(() => {
  const best = lesson.value?.quizzes.find((quiz) => quiz.id === quizId.value)?.bestPercent ?? null
  return best === null ? t('classPlayer.quiz.leaveMessageFirst') : t('classPlayer.quiz.leaveMessage', { percent: best })
})

async function readLesson(lessonId: number): Promise<void> {
  if (account !== getAuthSessionVersion()) return
  lesson.value = null
  lessonError.value = null
  const request = ++lessonRequest
  const stillCurrent = () => request === lessonRequest && account === getAuthSessionVersion()
  try {
    const loaded = await studentClassesApi.lesson(lessonId)
    if (stillCurrent()) lesson.value = loaded
  } catch (reason) {
    if (stillCurrent()) lessonError.value = resolveApiError(reason)
  }
}

function resetView(keepResult = false): number {
  viewVersion += 1
  if (!keepResult) {
    lessonRequest += 1
    result.value = null
    justSubmitted.value = false
    lesson.value = null
    lessonError.value = null
  }
  session.value = null
  startingQuizId.value = null
  loadingResult.value = false
  submitting.value = false
  attemptProgress.value = 0
  leaveOpen.value = false
  leaving.value = false
  actionError.value = null
  return viewVersion
}

async function openQuiz(id: number, keepResult = false): Promise<void> {
  if (account !== getAuthSessionVersion()) return
  if (startingQuizId.value === id) {
    if (!keepResult) {
      result.value = null
      justSubmitted.value = false
    }
    return
  }
  const version = resetView(keepResult)
  startingQuizId.value = id
  try {
    const next = await studentClassesApi.startQuiz(id)
    if (!ownsView(version)) return
    session.value = next
    result.value = null
    justSubmitted.value = false
    void readLesson(next.quiz.lessonId)
  } catch (reason) {
    if (ownsView(version)) actionError.value = resolveApiError(reason, t('classPlayer.quiz.loadErrorTitle'))
  } finally {
    if (ownsView(version)) startingQuizId.value = null
  }
}

function retake(): void {
  if (!playableLesson.value || starting.value) return
  const id = result.value?.quiz.id ?? quizId.value
  if (id !== null) void openQuiz(id, true)
}

function onSubmitted(next: QuizResult): void {
  if (session.value?.attemptId !== next.attemptId || account !== getAuthSessionVersion()) return
  viewVersion += 1
  result.value = next
  session.value = null
  submitting.value = false
  actionError.value = null
  justSubmitted.value = true
}

function requestLeave(): void {
  if (submitting.value || leaving.value) return
  if (session.value && session.value.questions.length > 0) leaveOpen.value = true
  else void router.push(lessonRoute.value)
}

async function leave(): Promise<void> {
  const current = session.value
  if (leaving.value || submitting.value || !current || account !== getAuthSessionVersion()) return
  const version = viewVersion
  const target = lessonRoute.value
  leaving.value = true
  actionError.value = null
  let discarded = false
  try {
    await studentClassesApi.discardAttempt(current.attemptId)
    discarded = true
  } catch (reason) {
    if (ownsView(version)) actionError.value = resolveApiError(reason, t('classPlayer.quiz.leaveError'))
  } finally {
    if (ownsView(version)) {
      leaving.value = false
      leaveOpen.value = false
    }
  }
  if (!discarded || !ownsView(version)) return
  session.value = null
  if (!pageActive.value) return
  try {
    await router.push(target)
  } catch (reason) {
    if (ownsView(version)) actionError.value = resolveApiError(reason)
  }
}

async function showAttempt(attemptId: number): Promise<void> {
  if (account !== getAuthSessionVersion() || result.value?.attemptId === attemptId) return
  const version = resetView()
  loadingResult.value = true
  try {
    const next = await studentClassesApi.quizResult(attemptId)
    if (!ownsView(version)) return
    result.value = next
    void readLesson(next.quiz.lessonId)
  } catch (reason) {
    if (ownsView(version)) actionError.value = resolveApiError(reason, t('classPlayer.quiz.loadErrorTitle'))
  } finally {
    if (ownsView(version)) loadingResult.value = false
  }
}

function openRequestedAttempt(): void {
  if (invalidAttempt.value) {
    resetView()
  } else if (requestedAttempt.value !== null) {
    void showAttempt(requestedAttempt.value)
  } else if (quizId.value === null) {
    resetView()
  } else if (session.value?.quiz.id !== quizId.value || session.value.questions.length === 0) {
    void openQuiz(quizId.value)
  }
}

watch([quizId, requestedAttempt, invalidAttempt], openRequestedAttempt, { immediate: true })

// A saved score names its quiz and attempt. A retake opens at the quiz address without the old attempt.
usePageRedirect(() => {
  const score = result.value
  if (score && (score.quiz.id !== quizId.value || score.attemptId !== requestedAttempt.value)) {
    return {
      name: 'student-quiz',
      params: { quizId: score.quiz.id },
      query: { ...route.query, attempt: score.attemptId },
      hash: route.hash,
    }
  }
  if (session.value && route.query.attempt !== undefined) {
    return { query: { ...route.query, attempt: undefined }, hash: route.hash }
  }
  return null
}, pageActive)

onBeforeUnmount(() => {
  viewVersion += 1
  lessonRequest += 1
})
</script>
