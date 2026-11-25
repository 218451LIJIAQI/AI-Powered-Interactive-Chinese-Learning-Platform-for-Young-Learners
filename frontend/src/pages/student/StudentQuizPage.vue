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
      v-else-if="activeSession"
      :key="activeSession.attemptId"
      v-model:error="actionError"
      :session="activeSession"
      :leave-open="leaveOpen"
      :leaving="leaving"
      @progress="attemptProgress = $event"
      @submitting="submitting = $event"
      @submitted="onSubmitted"
      @toast="showToast"
    />

    <div v-else class="focus-stage">
      <h1 class="sr-only">{{ t('classes.lessonQuiz') }}</h1>
      <StatePanel
        v-if="starting || loadingResult || leaving"
        heading-tag="h2"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('classPlayer.quiz.loadingMessage')"
      />
      <StatePanel
        v-else-if="discardedTarget"
        heading-tag="h2"
        tone="empty"
        spacious
        :title="t('classPlayer.quiz.discardedTitle')"
        :message="actionError ?? t('classPlayer.quiz.discardedMessage')"
        data-testid="quiz-discarded"
      >
        <template #actions>
          <RouterLink class="btn btn-primary" :to="discardedTarget">{{ t('classPlayer.openLesson') }}</RouterLink>
        </template>
      </StatePanel>
      <ClassLoadError
        v-else
        :title="t('classPlayer.quiz.loadErrorTitle')"
        :message="actionError"
        :back-to="{ name: 'student-classes' }"
        :back-label="t('nav.classes')"
        :on-retry="requestedTarget.kind === 'invalid' ? undefined : openRequestedAttempt"
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
import { RouterLink, useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

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
const requestedTarget = computed<{ kind: 'invalid' } | { kind: 'attempt'; id: number } | { kind: 'quiz'; id: number }>(
  () => {
    if (route.query.attempt !== undefined) {
      return requestedAttempt.value === null ? { kind: 'invalid' } : { kind: 'attempt', id: requestedAttempt.value }
    }
    return quizId.value === null ? { kind: 'invalid' } : { kind: 'quiz', id: quizId.value }
  },
)

const session = ref<QuizSession | null>(null)
const activeSession = computed(() => (session.value?.questions.length ? session.value : null))
const result = ref<QuizResult | null>(null)
const startingQuizId = ref<number | null>(null)
const starting = computed(() => startingQuizId.value !== null)
const loadingAttemptId = ref<number | null>(null)
const loadingResult = computed(() => loadingAttemptId.value !== null)
const justSubmitted = ref(false)
const submitting = ref(false)
const attemptProgress = ref(0)
const actionError = ref<string | null>(null)
const leaveOpen = ref(false)
const leaving = ref(false)
const discardedTarget = ref<RouteLocationRaw | null>(null)
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
  return activeSession.value ? attemptProgress.value : null
})

const leaveMessage = computed(() => {
  const activeQuizId = session.value?.quiz.id ?? quizId.value
  const best = lesson.value?.quizzes.find((quiz) => quiz.id === activeQuizId)?.bestPercent ?? null
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
  loadingAttemptId.value = null
  submitting.value = false
  attemptProgress.value = 0
  leaveOpen.value = false
  leaving.value = false
  discardedTarget.value = null
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
  if (activeSession.value) leaveOpen.value = true
  else void router.push(lessonRoute.value)
}

async function leave(): Promise<void> {
  const current = session.value
  if (leaving.value || submitting.value || !current || account !== getAuthSessionVersion()) return
  const version = viewVersion
  const target = lessonRoute.value
  leaving.value = true
  actionError.value = null
  try {
    await studentClassesApi.discardAttempt(current.attemptId)
  } catch (reason) {
    if (ownsView(version)) {
      actionError.value = resolveApiError(reason, t('classPlayer.quiz.leaveError'))
      leaving.value = false
      leaveOpen.value = false
    }
    return
  }
  if (!ownsView(version)) return
  leaveOpen.value = false
  session.value = null
  discardedTarget.value = target
  if (!pageActive.value) {
    leaving.value = false
    return
  }
  try {
    await router.push(target)
  } catch (reason) {
    if (ownsView(version)) actionError.value = resolveApiError(reason)
  } finally {
    if (ownsView(version)) leaving.value = false
  }
}

async function showAttempt(attemptId: number): Promise<void> {
  if (
    account !== getAuthSessionVersion() ||
    result.value?.attemptId === attemptId ||
    loadingAttemptId.value === attemptId
  ) {
    return
  }
  const version = resetView()
  loadingAttemptId.value = attemptId
  try {
    const next = await studentClassesApi.quizResult(attemptId)
    if (!ownsView(version)) return
    result.value = next
    void readLesson(next.quiz.lessonId)
  } catch (reason) {
    if (ownsView(version)) actionError.value = resolveApiError(reason, t('classPlayer.quiz.loadErrorTitle'))
  } finally {
    if (ownsView(version)) loadingAttemptId.value = null
  }
}

function openRequestedAttempt(): void {
  const target = requestedTarget.value
  if (target.kind === 'invalid') {
    resetView()
  } else if (target.kind === 'attempt') {
    void showAttempt(target.id)
  } else if (activeSession.value?.quiz.id !== target.id) {
    void openQuiz(target.id)
  }
}

watch(
  [() => requestedTarget.value.kind, () => ('id' in requestedTarget.value ? requestedTarget.value.id : null)],
  openRequestedAttempt,
  { immediate: true },
)

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
  if (session.value && (session.value.quiz.id !== quizId.value || route.query.attempt !== undefined)) {
    return {
      name: 'student-quiz',
      params: { quizId: session.value.quiz.id },
      query: { ...route.query, attempt: undefined },
      hash: route.hash,
    }
  }
  return null
}, pageActive)

onBeforeUnmount(() => {
  viewVersion += 1
  lessonRequest += 1
})
</script>
