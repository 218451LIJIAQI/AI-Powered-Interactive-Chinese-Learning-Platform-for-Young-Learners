<template>
  <div class="page-stack">
    <AppToast :message="toast" />
    <template v-if="lesson && lesson.available">
      <ClassPageHeader
        :crumbs="[
          { label: t('classes.classes'), to: { name: 'student-classes' } },
          { label: lesson.classTitle, to: classRoute },
          { label: lesson.title },
        ]"
        :teacher="lesson.teacherName"
        :title="lesson.title"
        :meta="stateLine"
      >
        <template #actions>
          <RouterLink
            v-if="nextActivity"
            class="btn btn-primary"
            :to="activityRoute(nextActivity.type, nextActivity.id)"
          >
            {{ t('studentClasses.lesson.continueActivity', { title: nextActivity.title }) }}
            <IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
          <RouterLink v-else-if="quizToTake" class="btn btn-primary" :to="quizRoute(quizToTake.id)">
            {{ t('studentClasses.lesson.takeQuiz') }}<IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
          <RouterLink v-else class="btn btn-primary" :to="classRoute">
            {{ t('studentClasses.lesson.openClass') }}<IconGlyph name="chevronRight" :size="16" />
          </RouterLink>
        </template>
      </ClassPageHeader>
      <DataNotice :error="actionError" />

      <div class="student-lesson-layout">
        <section class="panel student-lesson-content" aria-labelledby="lesson-content-title">
          <div class="row-list-copy">
            <h2 id="lesson-content-title" class="card-title">{{ t('studentClasses.lesson.content') }}</h2>
            <span class="row-list-meta">{{ unitLabel }}</span>
          </div>

          <p v-if="!lesson.video && !lesson.keyPhrases.length" class="type-caption">
            {{ t('studentClasses.lesson.noContent') }}
          </p>

          <div v-if="lesson.video" class="student-lesson-block">
            <h3 class="page-eyebrow">{{ t('studentClasses.lesson.video') }}</h3>
            <!-- Playing: the player takes the card's width, the title and Close under it. -->
            <div v-if="videoUrl" class="student-lesson-player-wrap">
              <video class="student-lesson-player" :src="videoUrl" controls autoplay @ended="markWatched" />
              <div class="student-lesson-player-bar">
                <strong class="row-list-title">{{ lesson.video.title }}</strong>
                <button type="button" class="btn btn-ghost btn-sm" @click="releaseVideo">
                  <IconGlyph name="close" :size="16" />{{ t('studentClasses.lesson.closeVideo') }}
                </button>
              </div>
            </div>
            <div v-else class="student-lesson-video">
              <span class="student-lesson-thumb" aria-hidden="true"><IconGlyph name="play" :size="24" /></span>
              <span class="row-list-copy">
                <strong class="row-list-title">{{ lesson.video.title }}</strong>
                <span class="row-list-meta" :role="videoLoading ? 'status' : undefined">
                  {{ videoLoading ? t('studentClasses.lesson.videoLoading') : videoLength }}
                </span>
              </span>
              <button
                v-if="lesson.video.streamPath"
                type="button"
                class="btn btn-secondary btn-sm"
                :disabled="videoLoading"
                :aria-busy="videoLoading"
                @click="loadVideo"
              >
                <IconGlyph name="play" :size="16" />{{ t('studentClasses.lesson.playVideo') }}
              </button>
              <a
                v-else-if="lesson.video.externalUrl && isWebLink(lesson.video.externalUrl)"
                class="btn btn-secondary btn-sm"
                :href="lesson.video.externalUrl"
                target="_blank"
                rel="noopener noreferrer"
                @click="markWatched"
              >
                <IconGlyph name="external" :size="16" />{{ t('studentClasses.lesson.openVideo') }}
                <span class="sr-only">{{ t('classes.opensInNewTab') }}</span>
              </a>
            </div>
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
            <div class="row-list-copy">
              <h2 id="lesson-activities-title" class="card-title">{{ t('studentClasses.lesson.activities') }}</h2>
              <span class="row-list-meta">
                {{
                  t('studentClasses.lesson.activitiesFacts', { done: activitiesDone, total: lesson.activities.length })
                }}
              </span>
            </div>
            <ol class="row-list student-lesson-activities">
              <li v-for="activity in lesson.activities" :key="`${activity.type}-${activity.id}`">
                <span class="student-lesson-step" :class="{ 'is-done': activity.done }" aria-hidden="true">
                  <IconGlyph v-if="activity.done" name="check" :size="16" />
                  <template v-else>{{ activity.number }}</template>
                </span>
                <span class="row-list-copy">
                  <strong class="row-list-title"><CjkText :text="activity.title" /></strong>
                  <span class="row-list-meta">
                    {{
                      t('studentClasses.lesson.activityFacts', {
                        number: activity.number,
                        type: t(ACTIVITY_TYPE_KEYS[activity.type]),
                      })
                    }}
                    <span v-if="activity.done" class="sr-only"> · {{ t('studentClasses.lesson.done') }}</span>
                  </span>
                </span>
                <RouterLink
                  class="btn btn-sm"
                  :class="activity.done ? 'btn-ghost' : 'btn-secondary'"
                  :to="activityRoute(activity.type, activity.id)"
                  :aria-label="
                    activity.done
                      ? t('studentClasses.lesson.practiseActivity', { title: activity.title })
                      : t('studentClasses.lesson.startActivity', { title: activity.title })
                  "
                >
                  <template v-if="activity.done">
                    <IconGlyph name="retry" :size="16" />{{ t('studentClasses.lesson.practiseAgain') }}
                  </template>
                  <template v-else>{{ t('studentClasses.lesson.start') }}</template>
                </RouterLink>
              </li>
            </ol>
          </section>

          <section class="panel student-lesson-quiz" aria-labelledby="lesson-quiz-title">
            <h2 id="lesson-quiz-title" class="card-title">{{ t('studentClasses.lesson.quiz') }}</h2>
            <p v-if="!quiz" class="type-caption">{{ t('studentClasses.lesson.noQuiz') }}</p>
            <template v-else>
              <div class="student-lesson-quiz-row">
                <span class="row-list-copy">
                  <strong class="row-list-title">{{
                    t('studentClasses.lesson.quizName', { title: quiz.title })
                  }}</strong>
                  <span class="row-list-meta">{{ t('classes.questions', quiz.questionCount) }}</span>
                </span>
                <span v-if="quiz.bestPercent !== null" class="student-lesson-best">
                  <span class="type-caption">{{ t('studentClasses.lesson.bestScore') }}</span>
                  <strong class="student-lesson-best-figure">{{ quiz.bestPercent }}%</strong>
                </span>
                <RouterLink class="btn btn-secondary" :to="quizRoute(quiz.id)">
                  <IconGlyph v-if="quiz.attemptCount && !quiz.inProgress" name="retry" :size="16" />
                  {{
                    quiz.inProgress
                      ? t('studentClasses.lesson.resumeQuiz')
                      : quiz.attemptCount
                        ? t('studentClasses.lesson.retakeQuiz')
                        : t('studentClasses.lesson.startQuiz')
                  }}
                </RouterLink>
              </div>
              <RouterLink v-if="latestAttempt" class="btn btn-link student-lesson-latest" :to="latestAttempt">
                <IconGlyph name="history" :size="16" />{{ t('studentClasses.lesson.latestResult') }}
                <IconGlyph name="chevronRight" :size="16" />
              </RouterLink>
            </template>
          </section>
        </div>
      </div>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ lesson?.title ?? t('classes.classes') }}</h1>
      <StatePanel
        v-if="resource.loading.value"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('studentClasses.lesson.loading')"
      />
      <StatePanel
        v-else-if="lesson && !lesson.available"
        tone="empty"
        icon="archive"
        spacious
        :title="t('studentClasses.lesson.unavailableTitle')"
        :message="t('studentClasses.lesson.unavailableMessage')"
      >
        <template #actions>
          <RouterLink class="btn btn-primary" :to="classRoute">{{ t('studentClasses.lesson.backToClass') }}</RouterLink>
        </template>
      </StatePanel>
      <StatePanel
        v-else
        tone="error"
        icon="alert"
        spacious
        :title="t('studentClasses.lesson.loadErrorTitle')"
        :message="resource.error.value ?? ''"
      >
        <template #actions>
          <button class="btn btn-secondary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
          <RouterLink class="btn btn-primary" :to="classRoute">{{ t('studentClasses.lesson.openClass') }}</RouterLink>
        </template>
      </StatePanel>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { activitySlug, studentClassesApi, videoBlob } from '@/api/classes'
import ClassPageHeader from '@/components/classes/ClassPageHeader.vue'
import KeyPhraseCards from '@/components/classes/KeyPhraseCards.vue'
import AppToast from '@/components/common/AppToast.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useClassNotice } from '@/composables/useClassNotice'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { ClassActivityType } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { ACTIVITY_TYPE_KEYS, isWebLink } from '@/utils/classes'
import { formatClock } from '@/utils/format'
import '@/styles/classes.css'

const { t } = useLocalMessages({ studentClasses: studentClassesMessages, classes: classesMessages })
const route = useRoute()
const { actionError, showError, clearFeedback } = useActionFeedback()
const { toast } = useClassNotice()

const classId = computed(() => Number(route.params.classId))
const lessonId = computed(() => Number(route.params.lessonId))
const classRoute = computed(() => ({ name: 'student-class', params: { classId: classId.value } }))

const resource = useResource(() => studentClassesApi.lesson(lessonId.value))
const lesson = computed(() => resource.data.value)
/** The class page's units give the unit's number; its quiz results give the attempt behind "See latest result". */
const classPage = useResource(() => studentClassesApi.classPage(classId.value))
const results = useResource(() => studentClassesApi.quizResults(classId.value))

watch(lessonId, () => {
  releaseVideo()
  void resource.reload()
})

/** "In progress · 3 of 4 steps"; a mastered lesson reads Completed. */
const stateLine = computed(() => {
  const current = lesson.value
  if (!current) return ''
  const status =
    current.status === 'IN_PROGRESS'
      ? t('studentClasses.lessonState.IN_PROGRESS')
      : current.status === 'NOT_STARTED'
        ? t('studentClasses.lessonState.NOT_STARTED')
        : t('studentClasses.lessonState.COMPLETED')
  return t('studentClasses.lesson.stateLine', { status, done: current.stepsDone, total: current.stepsTotal })
})

/** "Unit 1: First Conversations"; only the title until the class page says which unit it is. */
const unitLabel = computed(() => {
  const title = lesson.value?.unitTitle ?? ''
  const index = classPage.data.value?.units.findIndex((unit) => unit.lessons.some((row) => row.id === lessonId.value))
  return index === undefined || index < 0 ? title : t('classes.unitLabel', { number: index + 1, title })
})

const activitiesDone = computed(() => lesson.value?.activities.filter((activity) => activity.done).length ?? 0)
const nextActivity = computed(() => lesson.value?.activities.find((activity) => !activity.done) ?? null)
/** A lesson has one quiz. */
const quiz = computed(() => lesson.value?.quizzes[0] ?? null)
const quizToTake = computed(() =>
  quiz.value && (quiz.value.inProgress || !quiz.value.attemptCount) ? quiz.value : null,
)

const latestAttempt = computed(() => {
  const current = quiz.value
  if (!current?.attemptCount) return null
  // The server lists a class's attempts newest first.
  const attempt = results.data.value?.attempts.find((row) => row.quizId === current.id)
  return attempt ? { name: 'student-quiz-attempt', params: { quizId: current.id, attemptId: attempt.attemptId } } : null
})

function activityRoute(type: ClassActivityType, activityId: number) {
  return {
    name: 'student-class-activity',
    params: { classId: classId.value, lessonId: lessonId.value, activityType: activitySlug(type), activityId },
  }
}

function quizRoute(quizId: number) {
  return { name: 'student-quiz', params: { quizId } }
}

// ----- video -----

const videoUrl = ref<string | null>(null)
const videoLoading = ref(false)
let videoAbort: AbortController | null = null

const videoLength = computed(() =>
  lesson.value?.video?.durationSeconds ? formatClock(lesson.value.video.durationSeconds) : '',
)

function releaseVideo(): void {
  videoAbort?.abort()
  videoAbort = null
  if (videoUrl.value) URL.revokeObjectURL(videoUrl.value)
  videoUrl.value = null
}

async function loadVideo(): Promise<void> {
  const path = lesson.value?.video?.streamPath
  if (!path) return
  releaseVideo()
  clearFeedback()
  videoLoading.value = true
  videoAbort = new AbortController()
  try {
    videoUrl.value = URL.createObjectURL(await videoBlob(path, videoAbort.signal))
  } catch (reason) {
    if (!videoAbort?.signal.aborted) showError(resolveApiError(reason, t('studentClasses.lesson.videoError')))
  } finally {
    videoLoading.value = false
  }
}

onBeforeUnmount(releaseVideo)

/** The teacher still sees who watched; the student's page keeps no watched state, so a failure here says nothing. */
function markWatched(): void {
  if (lesson.value?.video?.watched) return
  studentClassesApi.markVideoWatched(lessonId.value).catch(() => undefined)
}
</script>
