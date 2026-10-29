<template>
  <div class="student-lesson-page page-stack">
    <template v-if="lesson && lesson.available">
      <PageHeaderBlock :eyebrow="eyebrow" :title="lesson.title" :description="stateLine">
        <template #before>
          <AppBreadcrumb
            :items="[
              { label: t('classes.classes'), to: { name: 'student-classes' } },
              { label: lesson.classTitle, to: { name: 'student-class', params: { classId: lesson.classId } } },
              { label: lesson.title },
            ]"
          />
        </template>
        <template #meta>
          <span class="student-lesson-byline">
            <PortraitAvatar :size="28" :name="lesson.teacherName" />
            {{ t('studentClasses.lesson.byTeacher', { teacher: lesson.teacherName }) }}
          </span>
        </template>
        <template v-if="nextActivity || quizToTake" #actions>
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
        </template>
      </PageHeaderBlock>
      <DataNotice :error="actionError" :success="actionSuccess" />

      <div class="student-lesson-layout">
        <div class="student-lesson-main">
          <p v-if="lesson.summary" class="student-lesson-summary"><CjkText :text="lesson.summary" /></p>

          <!-- Watch -->
          <section v-if="lesson.video" class="student-lesson-section" aria-labelledby="lesson-video-title">
            <div class="student-lesson-section-head">
              <h2 id="lesson-video-title" class="student-lesson-heading">{{ t('studentClasses.lesson.video') }}</h2>
            </div>
            <div class="student-lesson-video" :class="{ 'is-playing': videoUrl }">
              <video
                v-if="videoUrl"
                class="student-lesson-player"
                :src="videoUrl"
                controls
                preload="metadata"
                @ended="markWatched"
              />
              <!-- A still, not a control (the Open video button below plays it): a neutral video mark, no play button. -->
              <div v-else class="student-lesson-screen" aria-hidden="true">
                <IconGlyph name="video" :size="32" />
              </div>
              <div class="student-lesson-video-bar">
                <span class="student-lesson-video-copy">
                  <strong>{{ lesson.video.title }}</strong>
                  <span class="type-caption">
                    <template v-if="lesson.video.watched">{{ t('studentClasses.lesson.videoWatched') }}</template>
                    <template v-else>{{
                      t('studentClasses.lesson.videoFacts', {
                        length: formatClock(lesson.video.durationSeconds),
                        percent: lesson.video.percentWatched,
                      })
                    }}</template>
                  </span>
                  <span v-if="videoLoading" class="type-caption" role="status">
                    {{ t('studentClasses.lesson.videoLoading') }}
                  </span>
                </span>
                <span class="student-lesson-video-actions">
                  <button
                    v-if="lesson.video.streamPath && !videoUrl"
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
                  >
                    <IconGlyph name="external" :size="16" />{{ t('studentClasses.lesson.openVideo') }}
                    <span class="sr-only">{{ t('classes.opensInNewTab') }}</span>
                  </a>
                  <button
                    v-if="!lesson.video.watched"
                    type="button"
                    class="btn btn-ghost btn-sm"
                    :disabled="marking"
                    :aria-busy="marking"
                    @click="markWatched"
                  >
                    <IconGlyph name="check" :size="16" />{{ t('studentClasses.lesson.markWatched') }}
                  </button>
                </span>
              </div>
            </div>
          </section>

          <!-- Key phrases -->
          <section
            v-if="lesson.keyPhrases.length"
            class="student-lesson-section"
            aria-labelledby="lesson-phrases-title"
          >
            <div class="student-lesson-section-head">
              <h2 id="lesson-phrases-title" class="student-lesson-heading">
                {{ t('studentClasses.lesson.keyPhrases') }}
              </h2>
              <span class="type-caption">{{ t('studentClasses.lesson.phrases', lesson.keyPhrases.length) }}</span>
            </div>
            <KeyPhraseCards :phrases="lesson.keyPhrases" />
          </section>

          <!-- Activities, best done in order -->
          <section
            v-if="lesson.activities.length"
            class="student-lesson-section"
            aria-labelledby="lesson-activities-title"
          >
            <div class="student-lesson-section-head">
              <h2 id="lesson-activities-title" class="student-lesson-heading">
                {{ t('studentClasses.lesson.activities') }}
              </h2>
              <span class="type-caption">
                {{
                  t('studentClasses.lesson.activitiesFacts', { done: activitiesDone, total: lesson.activities.length })
                }}
              </span>
            </div>
            <ol class="student-lesson-activities">
              <li
                v-for="activity in lesson.activities"
                :key="`${activity.type}-${activity.id}`"
                class="student-lesson-activity"
                :class="{ 'is-done': activity.done, 'is-next': isNext(activity) }"
              >
                <span
                  class="student-lesson-step"
                  :class="{ 'is-done': activity.done, 'is-next': isNext(activity) }"
                  aria-hidden="true"
                >
                  <IconGlyph v-if="activity.done" name="check" :size="16" />
                  <template v-else>{{ activity.number }}</template>
                </span>
                <span class="student-lesson-activity-copy">
                  <strong>{{ activity.title }}</strong>
                  <span class="type-caption">{{ activityFacts(activity) }}</span>
                </span>
                <span v-if="activity.done" class="student-lesson-activity-done">
                  <IconGlyph name="check" :size="14" />{{ t('studentClasses.lesson.done') }}
                </span>
                <RouterLink
                  class="btn btn-sm"
                  :class="activity.done ? 'btn-ghost' : 'btn-secondary'"
                  :to="activityRoute(activity.type, activity.id)"
                  :aria-label="t('studentClasses.lesson.startActivity', { title: activity.title })"
                >
                  <template v-if="activity.done">
                    <IconGlyph name="retry" :size="16" />{{ t('studentClasses.lesson.practiseAgain') }}
                  </template>
                  <template v-else>{{ t('studentClasses.lesson.start') }}</template>
                </RouterLink>
              </li>
            </ol>
            <p class="student-lesson-note"><IconGlyph name="info" :size="14" />{{ t('classes.stageHint') }}</p>
          </section>

          <!-- The lesson quiz -->
          <section class="student-lesson-section" aria-labelledby="lesson-quiz-title">
            <div class="student-lesson-section-head">
              <h2 id="lesson-quiz-title" class="student-lesson-heading">{{ t('studentClasses.lesson.quiz') }}</h2>
            </div>
            <p v-if="!lesson.quizzes.length" class="student-lesson-note">{{ t('studentClasses.lesson.noQuiz') }}</p>
            <div v-for="quiz in lesson.quizzes" :key="quiz.id" class="student-lesson-quiz">
              <div class="student-lesson-quiz-copy">
                <strong class="student-lesson-quiz-title">{{ quiz.title }}</strong>
                <span class="type-caption">{{ quizFacts(quiz) }}</span>
                <span class="type-caption">
                  {{ t('studentClasses.lesson.attemptsSubmitted', quiz.attemptCount) }}
                  <RouterLink
                    v-if="quiz.attemptCount"
                    :to="{ name: 'student-class', params: { classId: lesson.classId, tab: 'results' } }"
                  >
                    · {{ t('studentClasses.lesson.seeAttempts') }}
                  </RouterLink>
                </span>
              </div>
              <div v-if="quiz.bestPercent !== null" class="student-lesson-best">
                <strong class="stat-figure">{{ quiz.bestPercent }}%</strong>
                <span class="type-caption">{{ t('studentClasses.lesson.bestScore') }}</span>
              </div>
              <RouterLink
                class="btn"
                :class="quiz.attemptCount ? 'btn-secondary' : 'btn-primary'"
                :to="quizRoute(quiz.id)"
              >
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
            <p class="callout is-compact">
              <IconGlyph name="target" :size="16" /><span>{{ t('studentClasses.lesson.completeHint') }}</span>
            </p>
          </section>
        </div>

        <aside class="student-lesson-side">
          <section class="student-lesson-progress" aria-labelledby="lesson-progress-title">
            <div class="class-sessions-head">
              <h2 id="lesson-progress-title" class="class-sessions-title">{{ t('studentClasses.lesson.progress') }}</h2>
            </div>
            <ProgressRing
              :progress="lesson.percent"
              :title="statusLabel('mastery', lesson.status)"
              :subtitle="t('studentClasses.lesson.steps', { done: lesson.stepsDone, total: lesson.stepsTotal })"
            />
            <dl class="kv-list">
              <dt>{{ t('studentClasses.lesson.activities') }}</dt>
              <dd>
                {{
                  t('studentClasses.lesson.activitiesDone', { done: activitiesDone, total: lesson.activities.length })
                }}
              </dd>
              <dt>{{ t('studentClasses.lesson.quiz') }}</dt>
              <dd>
                {{
                  lesson.bestQuizPercent !== null
                    ? t('studentClasses.lesson.quizState', { percent: lesson.bestQuizPercent })
                    : t('studentClasses.lesson.quizNotTaken')
                }}
              </dd>
            </dl>
          </section>
          <ClassSessionsPanel
            :sessions="lesson.liveSessions"
            :title="t('studentClasses.lesson.sessionsTitle')"
            :empty="t('studentClasses.lesson.noSessions')"
          />
        </aside>
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
import AppBreadcrumb from '@/components/common/AppBreadcrumb.vue'
import ClassSessionsPanel from '@/components/classes/ClassSessionsPanel.vue'
import KeyPhraseCards from '@/components/classes/KeyPhraseCards.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import ProgressRing from '@/components/common/ProgressRing.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import CjkText from '@/components/learning/CjkText.vue'
import PageHeaderBlock from '@/components/sections/PageHeaderBlock.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { useStatusLabels } from '@/composables/useStatusLabels'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { ActivityStep, ClassActivityType, QuizCard } from '@/types/classes'
import { resolveApiError } from '@/utils/apiError'
import { ACTIVITY_TYPE_KEYS, STAGE_KEYS, isWebLink } from '@/utils/classes'
import { formatClock } from '@/utils/format'
import '@/styles/classes.css'

const { t } = useLocalMessages({ studentClasses: studentClassesMessages, classes: classesMessages })
const { statusLabel } = useStatusLabels()
const route = useRoute()
const { actionError, actionSuccess, runAction, showError } = useActionFeedback()

const classId = computed(() => Number(route.params.classId))
const lessonId = computed(() => Number(route.params.lessonId))
const classRoute = computed(() => ({ name: 'student-class', params: { classId: classId.value } }))

const resource = useResource(() => studentClassesApi.lesson(lessonId.value))
const lesson = computed(() => resource.data.value)
watch(lessonId, () => {
  releaseVideo()
  void resource.reload()
})

/** "Lesson 1 · First Conversations · about 18 min" above the title. */
const eyebrow = computed(() => {
  const current = lesson.value
  if (!current) return ''
  return current.minutes
    ? t('studentClasses.lesson.eyebrow', { number: current.number, unit: current.unitTitle, minutes: current.minutes })
    : t('studentClasses.lesson.eyebrowNoTime', { number: current.number, unit: current.unitTitle })
})

/** "In progress · 3 of 4 steps" under the title. */
const stateLine = computed(() => {
  const current = lesson.value
  if (!current) return ''
  return t('studentClasses.lesson.stateLine', {
    status: statusLabel('mastery', current.status),
    done: current.stepsDone,
    total: current.stepsTotal,
  })
})

const activitiesDone = computed(() => lesson.value?.activities.filter((activity) => activity.done).length ?? 0)
const nextActivity = computed(() => lesson.value?.activities.find((activity) => !activity.done) ?? null)
const quizToTake = computed(
  () =>
    lesson.value?.quizzes.find((quiz) => quiz.inProgress) ??
    lesson.value?.quizzes.find((quiz) => !quiz.attemptCount) ??
    null,
)

function isNext(activity: ActivityStep): boolean {
  return nextActivity.value?.id === activity.id && nextActivity.value?.type === activity.type
}

/** "Flashcards · Guided" */
function activityFacts(activity: ActivityStep): string {
  const type = t(ACTIVITY_TYPE_KEYS[activity.type])
  return activity.stage
    ? t('studentClasses.lesson.activityFacts', { type, stage: t(STAGE_KEYS[activity.stage]) })
    : type
}

function activityRoute(type: ClassActivityType, activityId: number) {
  return {
    name: 'student-class-activity',
    params: { classId: classId.value, lessonId: lessonId.value, activityType: activitySlug(type), activityId },
  }
}

function quizRoute(quizId: number) {
  return { name: 'student-quiz', params: { quizId } }
}

function quizFacts(quiz: QuizCard): string {
  const counts = { questions: t('classes.questions', quiz.questionCount), marks: t('classes.marks', quiz.marks) }
  const parts = [
    quiz.stage
      ? t('studentClasses.lesson.quizFacts', { stage: t(STAGE_KEYS[quiz.stage]), ...counts })
      : t('studentClasses.lesson.quizFactsNoStage', counts),
  ]
  if (quiz.timeLimitMinutes) parts.push(t('studentClasses.lesson.timeLimit', { minutes: quiz.timeLimitMinutes }))
  if (quiz.hintsAvailable) parts.push(t('studentClasses.lesson.hintsAvailable'))
  return parts.join(' · ')
}

// ----- video -----

const videoUrl = ref<string | null>(null)
const videoLoading = ref(false)
let videoAbort: AbortController | null = null

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

const marking = ref(false)

async function markWatched(): Promise<void> {
  if (marking.value || lesson.value?.video?.watched) return
  marking.value = true
  await runAction(t('studentClasses.lesson.videoError'), async () => {
    resource.data.value = await studentClassesApi.markVideoWatched(lessonId.value)
    return t('studentClasses.lesson.watchedSaved')
  })
  marking.value = false
}
</script>
