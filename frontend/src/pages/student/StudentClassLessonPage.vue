<template>
  <div class="page-stack">
    <AppToast :message="toast" />
    <template v-if="lesson && lesson.available">
      <ClassPageHeader
        :crumbs="[
          { label: t('nav.classes'), to: { name: 'student-classes' } },
          { label: lesson.classTitle, to: classRoute },
          { label: lesson.title },
        ]"
        :class-title="lesson.classTitle"
        :title="lesson.title"
        :meta="stateLine"
      >
        <template #actions>
          <RouterLink
            class="btn"
            :class="lesson.archived ? 'btn-secondary' : 'btn-primary'"
            :to="nextAction.to"
            data-testid="lesson-next"
          >
            {{ nextAction.label }}
            <IconGlyph v-if="!lesson.archived" name="chevronRight" :size="16" />
          </RouterLink>
        </template>
      </ClassPageHeader>

      <div class="student-lesson-layout">
        <section class="panel student-lesson-content" aria-labelledby="lesson-content-title">
          <h2 id="lesson-content-title" class="card-title">{{ t('studentClasses.lesson.content') }}</h2>

          <p v-if="!lesson.video && !lesson.keyPhrases.length" class="type-caption">
            {{ t('studentClasses.lesson.noContent') }}
          </p>

          <div v-if="lesson.video" class="student-lesson-block">
            <h3 class="page-eyebrow">{{ t('studentClasses.lesson.video') }}</h3>
            <LessonVideo
              :key="lessonId ?? undefined"
              :video="lesson.video"
              @watched="markWatched"
              @report="reporting = true"
            />
            <DataNotice :error="videoProgressError">
              <template #actions>
                <button class="btn btn-secondary btn-sm" type="button" @click="markWatched">
                  {{ t('common.retry') }}
                </button>
              </template>
            </DataNotice>
          </div>

          <div v-if="lesson.keyPhrases.length" class="student-lesson-block">
            <h3 class="page-eyebrow">{{ t('studentClasses.lesson.keyPhrases') }}</h3>
            <KeyPhraseCards :phrases="lesson.keyPhrases" />
          </div>
        </section>

        <div class="student-lesson-side">
          <section
            v-if="lesson.activities.length"
            class="panel student-lesson-activities-card"
            aria-labelledby="lesson-activities-title"
          >
            <h2 id="lesson-activities-title" class="card-title">{{ t('studentClasses.lesson.activities') }}</h2>
            <!-- Each row is one link: Start on an activity to do, a chevron on a done one. An archived class's rows only
                 show what was done. -->
            <ol class="row-list student-lesson-activities">
              <li
                v-for="activity in lesson.activities"
                :key="`${activity.type}-${activity.id}`"
                class="student-lesson-activity-row"
              >
                <component
                  :is="lesson.archived ? 'div' : RouterLink"
                  class="student-lesson-activity"
                  v-bind="lesson.archived ? {} : activityLink(activity)"
                >
                  <span class="student-lesson-step" :class="{ 'is-done': activity.done }">
                    <IconGlyph v-if="activity.done" name="check" :size="16" />
                    <template v-else>{{ activity.number }}</template>
                  </span>
                  <span class="row-list-copy">
                    <strong class="row-list-title"><CjkText :text="activity.title" /></strong>
                    <span class="row-list-meta">
                      {{ t(`practiceType.${activity.type}`) }}
                      <span v-if="activity.done" class="sr-only"> · {{ t('studentClasses.lesson.done') }}</span>
                    </span>
                  </span>
                  <template v-if="!lesson.archived">
                    <IconGlyph v-if="activity.done" class="class-row-chevron" name="chevronRight" :size="16" />
                    <span v-else class="btn btn-secondary btn-sm">
                      {{ t('studentClasses.lesson.start') }}
                    </span>
                  </template>
                </component>
              </li>
            </ol>
          </section>

          <section class="panel student-lesson-quiz" aria-labelledby="lesson-quiz-title">
            <h2 id="lesson-quiz-title" class="card-title">{{ t('classes.lessonQuiz') }}</h2>
            <p v-if="!quiz" class="type-caption">{{ t('studentClasses.lesson.noQuiz') }}</p>
            <template v-else>
              <div class="student-lesson-quiz-row">
                <span class="row-list-meta">{{ t('classes.questions', quiz.questionCount) }}</span>
                <span v-if="quiz.bestPercent !== null" class="student-lesson-best">
                  <span class="type-caption">{{ t('classes.bestScore') }}</span>
                  <strong class="student-lesson-best-figure">{{ quiz.bestPercent }}%</strong>
                </span>
                <button
                  v-if="lesson.archived"
                  type="button"
                  class="btn btn-secondary"
                  disabled
                  data-testid="quiz-start"
                >
                  <IconGlyph v-if="quiz.attemptCount" name="retry" :size="16" />
                  {{ quiz.attemptCount ? t('classes.retakeQuiz') : t('studentClasses.lesson.startQuiz') }}
                </button>
                <RouterLink v-else class="btn btn-secondary" :to="quizRoute(quiz.id)" data-testid="quiz-start">
                  <IconGlyph v-if="quiz.attemptCount && !quiz.inProgress" name="retry" :size="16" />
                  {{
                    quiz.inProgress
                      ? t('studentClasses.lesson.resumeQuiz')
                      : quiz.attemptCount
                        ? t('classes.retakeQuiz')
                        : t('studentClasses.lesson.startQuiz')
                  }}
                </RouterLink>
              </div>
              <RouterLink
                v-if="bestAttempt"
                class="btn btn-link student-lesson-best-link"
                :to="bestAttempt"
                data-testid="quiz-best"
              >
                <IconGlyph name="history" :size="16" />{{ t('studentClasses.lesson.bestResult') }}
                <IconGlyph name="chevronRight" :size="16" />
              </RouterLink>
            </template>
          </section>
        </div>
      </div>

      <ReportProblemDialog
        v-if="reporting && lesson.video"
        :lesson-title="lesson.title"
        :class-title="lesson.classTitle"
        :video-title="lesson.video.title"
        @close="reporting = false"
        @sent="onReportSent"
      />
    </template>

    <template v-else>
      <h1 class="sr-only">{{ lesson?.title ?? t('nav.classes') }}</h1>
      <StatePanel
        v-if="loading"
        heading-tag="h2"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('studentClasses.lesson.loading')"
      />
      <StatePanel
        v-else-if="lesson && !lesson.available"
        heading-tag="h2"
        tone="empty"
        icon="archive"
        spacious
        :title="t('studentClasses.lesson.unavailableTitle')"
        :message="t('studentClasses.lesson.unavailableMessage')"
      >
        <template #actions>
          <RouterLink class="btn btn-primary" :to="classRoute">{{ t('classes.openClass') }}</RouterLink>
        </template>
      </StatePanel>
      <ClassLoadError
        v-else-if="loadError"
        :title="t('classes.lessonLoadError')"
        :message="loadError"
        :back-to="classRoute"
        :back-label="t('classes.openClass')"
        @retry="load"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import ClassLoadError from '@/components/classes/ClassLoadError.vue'
import ClassPageHeader from '@/components/classes/ClassPageHeader.vue'
import KeyPhraseCards from '@/components/classes/KeyPhraseCards.vue'
import LessonVideo from '@/components/classes/LessonVideo.vue'
import ReportProblemDialog from '@/components/classes/ReportProblemDialog.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { usePageRedirect } from '@/composables/usePageNavigation'
import { useClassNotice } from '@/composables/useClassNotice'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { StudentLesson } from '@/types/classes'
import { apiErrorStatus, resolveApiError } from '@/utils/apiError'
import { classActivityRoute, classLessonRoute } from '@/utils/classes'
import { parseRecordId } from '@/utils/recordId'
import '@/styles/classes.css'

const { t } = useLocalMessages({ studentClasses: studentClassesMessages, classes: classesMessages })
const route = useRoute()
const { toast, showToast } = useClassNotice(undefined, 'student-class-lesson')

const routeClassId = computed(() => parseRecordId(route.params.classId))
const lessonId = computed(() => parseRecordId(route.params.lessonId))
const lesson = ref<StudentLesson | null>(null)
const classId = computed(() => lesson.value?.classId ?? routeClassId.value)
const classRoute = computed(() =>
  classId.value !== null ? { name: 'student-class', params: { classId: classId.value } } : { name: 'student-classes' },
)

const loadFailure = shallowRef<{ reason: unknown } | null>(null)
const needsEnrollment = computed(() => apiErrorStatus(loadFailure.value?.reason) === 409)
const loadError = computed(() =>
  loadFailure.value && !needsEnrollment.value
    ? resolveApiError(loadFailure.value.reason, t('classes.lessonLoadError'))
    : null,
)
/** Neither the lesson nor the reason it could not be read is in yet. */
const loading = computed(() => lesson.value === null && loadError.value === null)
const reporting = ref(false)
const videoProgressError = ref<string | null>(null)
let markingVideo: StudentLesson['video'] = null
let request = 0

/**
 * Reads the lesson; only the newest request counts. A lesson of a class the learner has not joined is refused (409):
 * the 403 page says so and, from the lesson's address in `redirect`, offers its class.
 */
async function load(): Promise<void> {
  const id = lessonId.value
  const mine = ++request
  loadFailure.value = null
  if (id === null) {
    loadFailure.value = { reason: null }
    return
  }
  try {
    const loaded = await studentClassesApi.lesson(id)
    if (mine === request) lesson.value = loaded
  } catch (reason) {
    if (mine === request) loadFailure.value = { reason }
  }
}

onMounted(load)
onBeforeUnmount(() => {
  request += 1
})

// Another lesson in this same page; leaving the page (to the refused page, say) loads nothing more.
watch(lessonId, () => {
  if (route.name !== 'student-class-lesson') return
  reporting.value = false
  videoProgressError.value = null
  lesson.value = null
  void load()
})

// The lesson id is global; a bookmarked URL may carry a different class in its parent segment.
usePageRedirect(() => {
  if (needsEnrollment.value) return { name: 'unauthorized', query: { redirect: route.fullPath } }
  const current = lesson.value
  const id = lessonId.value
  if (!current || id === null || current.classId === routeClassId.value) return null
  return { ...classLessonRoute(current.classId, id), query: route.query, hash: route.hash }
})

/** "In progress · 3 of 4 steps", or "Completed · archived class". */
const stateLine = computed(() => {
  const current = lesson.value
  if (!current) return ''
  const status = t(`studentClasses.lessonState.${current.status}`)
  return current.archived
    ? t('studentClasses.lesson.archivedLine', { status })
    : t('studentClasses.lesson.stateLine', { status, done: current.stepsDone, total: current.stepsTotal })
})

/** A lesson has one quiz. */
const quiz = computed(() => lesson.value?.quizzes[0] ?? null)

const nextAction = computed(() => {
  if (lesson.value?.archived) return { to: classRoute.value, label: t('studentClasses.lesson.backToClass') }
  const activity = lesson.value?.activities.find((item) => !item.done)
  if (activity && classId.value !== null && lessonId.value !== null) {
    return {
      to: classActivityRoute(classId.value, lessonId.value, activity),
      label: t('classes.continueNamed', { title: activity.title }),
    }
  }
  const currentQuiz = quiz.value
  if (currentQuiz && (currentQuiz.inProgress || !currentQuiz.attemptCount)) {
    return { to: quizRoute(currentQuiz.id), label: t('studentClasses.lesson.continueQuiz') }
  }
  return { to: classRoute.value, label: t('classes.openClass') }
})

/** The attempt with the best score: "See best result" (a later, lower retake does not replace it). */
const bestAttempt = computed(() =>
  quiz.value?.bestAttemptId
    ? { name: 'student-quiz-attempt', params: { quizId: quiz.value.id, attemptId: quiz.value.bestAttemptId } }
    : null,
)

/** The row itself supplies the activity name, type and status. Its tooltip describes the action. */
function activityLink(activity: StudentLesson['activities'][number]) {
  return {
    to:
      classId.value !== null && lessonId.value !== null
        ? classActivityRoute(classId.value, lessonId.value, activity)
        : classRoute.value,
    title: t(activity.done ? 'studentClasses.lesson.openActivity' : 'studentClasses.lesson.startActivity', {
      title: activity.title,
    }),
  }
}

function quizRoute(quizId: number) {
  return { name: 'student-quiz', params: { quizId } }
}

/** Watching starts the lesson without adding a completed activity or quiz. */
async function markWatched(): Promise<void> {
  const id = lessonId.value
  const current = lesson.value
  const video = current?.video
  if (id === null || !current || !video || video.watched || markingVideo === video) return
  markingVideo = video
  videoProgressError.value = null
  try {
    await studentClassesApi.markVideoWatched(id)
    if (lesson.value !== current) return
    video.watched = true
    if (current.status === 'NOT_STARTED') current.status = 'IN_PROGRESS'
  } catch (reason) {
    if (lesson.value === current)
      videoProgressError.value = resolveApiError(reason, t('studentClasses.lesson.videoProgressError'))
  } finally {
    if (markingVideo === video) markingVideo = null
  }
}

function onReportSent(): void {
  reporting.value = false
  showToast(t('studentClasses.report.sent'))
}
</script>
