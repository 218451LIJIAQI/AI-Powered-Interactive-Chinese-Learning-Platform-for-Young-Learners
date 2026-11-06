<template>
  <div class="page-stack">
    <template v-if="page">
      <!-- Not joined yet: free to join, the lessons locked until then. -->
      <template v-if="!page.joined">
        <ClassPageHeader
          :crumbs="[
            { label: t('classes.classes'), to: { name: 'student-classes-find' } },
            { label: page.summary.title },
          ]"
          :teacher="page.summary.teacher.name"
          :title="page.summary.title"
          :meta="previewFacts"
        />
        <DataNotice :error="actionError" />

        <section class="panel student-class-join" aria-labelledby="class-join-title">
          <h2 id="class-join-title" class="card-title">{{ t('studentClasses.classPage.preview.free') }}</h2>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="joining"
            :aria-busy="joining"
            data-testid="join-class"
            @click="join"
          >
            {{ t('studentClasses.classPage.preview.join') }}
          </button>
        </section>

        <div class="student-class-preview">
          <section
            v-for="(unit, unitIndex) in page.preview"
            :key="unit.id"
            class="panel student-class-unit"
            :aria-labelledby="`class-unit-${unit.id}`"
          >
            <div class="row-list-copy">
              <h2 :id="`class-unit-${unit.id}`" class="card-title">
                {{ t('classes.unitLabel', { number: unitIndex + 1, title: unit.title }) }}
              </h2>
              <span class="row-list-meta">{{ t('classes.lessons', unit.lessons.length) }}</span>
            </div>
            <ol class="row-list">
              <li v-for="lesson in unit.lessons" :key="lesson.id" class="student-class-locked">
                <span class="student-class-lock" aria-hidden="true"><IconGlyph name="lock" :size="16" /></span>
                <span class="row-list-title">{{ lesson.title }}</span>
              </li>
            </ol>
          </section>
        </div>
      </template>

      <template v-else>
        <ClassPageHeader
          :crumbs="[{ label: t('classes.classes'), to: { name: 'student-classes' } }, { label: page.summary.title }]"
          :teacher="page.summary.teacher.name"
          :title="page.summary.title"
          :meta="
            tab === 'lessons'
              ? t('studentClasses.classPage.teacherFacts', {
                  teacher: page.summary.teacher.name,
                  units: t('classes.units', page.summary.unitCount),
                  lessons: t('classes.lessons', page.summary.lessonCount),
                })
              : t('studentClasses.classPage.teacherOnly', { teacher: page.summary.teacher.name })
          "
        />
        <DataNotice v-if="page.archived" :info="t('studentClasses.classPage.archived')" />
        <RouteTabs :tabs="tabs" :label="t('studentClasses.classPage.tabsLabel')" />

        <!-- Lessons: the next step and the units on the left, the class's live sessions in the rail. -->
        <div v-if="tab === 'lessons'" class="student-class-body">
          <div class="student-class-course">
            <section class="panel student-class-next" aria-labelledby="class-next-title">
              <div class="row-list-copy">
                <h2 id="class-next-title" class="page-eyebrow">{{ t('studentClasses.classPage.nextStep') }}</h2>
                <p class="card-title">{{ page.nextStep ? nextStepText : t('studentClasses.classPage.allDone') }}</p>
              </div>
              <div class="student-class-progress">
                <div class="student-class-progress-labels">
                  <span class="type-caption">{{ t('studentClasses.classPage.classProgress') }}</span>
                  <span class="type-caption">
                    {{
                      t('studentClasses.classPage.progressText', {
                        percent: page.progressPercent,
                        done: page.completedLessons,
                        total: page.lessonCount,
                      })
                    }}
                  </span>
                </div>
                <ProgressBar :progress="page.progressPercent" :label="t('studentClasses.classPage.classProgress')" />
              </div>
              <div v-if="page.nextStep" class="btn-group">
                <RouterLink
                  class="btn btn-primary"
                  :to="lessonRoute(page.nextStep.lessonId)"
                  data-testid="continue-class"
                >
                  {{ t('studentClasses.classPage.continue', { title: page.nextStep.lessonTitle }) }}
                  <IconGlyph name="chevronRight" :size="16" />
                </RouterLink>
              </div>
            </section>

            <section
              v-for="(unit, unitIndex) in page.units"
              :key="unit.id"
              class="panel student-class-unit"
              :aria-labelledby="`class-unit-${unit.id}`"
            >
              <div class="row-list-copy">
                <h2 :id="`class-unit-${unit.id}`" class="card-title">
                  {{ t('classes.unitLabel', { number: unitIndex + 1, title: unit.title }) }}
                </h2>
                <span class="row-list-meta">
                  {{
                    t('studentClasses.classPage.unitFacts', {
                      lessons: t('classes.lessons', unit.lessonCount),
                      done: unit.completedCount,
                    })
                  }}
                </span>
              </div>
              <ol class="row-list student-class-lessons">
                <li v-for="lesson in unit.lessons" :key="lesson.id">
                  <RouterLink
                    v-if="lesson.available"
                    class="student-class-lesson"
                    :to="lessonRoute(lesson.id)"
                    :aria-label="t('studentClasses.classPage.openLesson', { title: lesson.title })"
                  >
                    <span class="row-list-copy">
                      <strong class="row-list-title"><CjkText :text="lesson.title" /></strong>
                      <span class="row-list-meta">{{ lessonFacts(lesson) }}</span>
                    </span>
                    <ProgressBar
                      :progress="lesson.percent"
                      :label="t('studentClasses.classPage.lessonProgress', { title: lesson.title })"
                    />
                    <span class="student-class-lesson-percent type-caption">{{ lesson.percent }}%</span>
                    <StatusPill :label="stateLabel(lesson)" :tone="stateTone(lesson)" />
                    <IconGlyph class="class-row-chevron" name="chevronRight" :size="16" />
                  </RouterLink>
                  <div v-else class="student-class-lesson is-unavailable">
                    <span class="row-list-copy">
                      <strong class="row-list-title"><CjkText :text="lesson.title" /></strong>
                      <span class="row-list-meta">{{ lessonFacts(lesson) }}</span>
                    </span>
                    <StatusPill :label="t('studentClasses.lessonState.unavailable')" tone="outline" />
                  </div>
                </li>
              </ol>
            </section>
          </div>
          <aside class="student-class-side">
            <ClassSessionsPanel :sessions="page.liveSessions" />
          </aside>
        </div>

        <!-- Feedback: notes from the teacher, newest first. -->
        <template v-else>
          <StatePanel
            v-if="feedback.loading.value"
            tone="loading"
            :title="t('classes.loading')"
            :message="t('studentClasses.classPage.tabFeedback')"
          />
          <StatePanel
            v-else-if="feedback.error.value"
            tone="error"
            icon="alert"
            :title="t('studentClasses.classPage.feedback.loadError')"
            :message="feedback.error.value"
          >
            <template #actions>
              <button class="btn btn-primary" type="button" @click="feedback.reload">{{ t('classes.retry') }}</button>
            </template>
          </StatePanel>
          <section
            v-else-if="feedback.data.value?.length"
            class="panel student-class-feedback"
            aria-labelledby="class-feedback-title"
          >
            <div class="row-list-copy">
              <h2 id="class-feedback-title" class="card-title">{{ t('studentClasses.classPage.feedback.title') }}</h2>
              <span class="row-list-meta">
                {{ t('studentClasses.classPage.feedback.messages', feedback.data.value.length) }}
              </span>
            </div>
            <ul class="row-list">
              <li v-for="item in feedback.data.value" :key="item.id" class="student-class-note">
                <div class="student-class-note-head">
                  <PortraitAvatar :size="32" :name="item.teacherName" />
                  <span class="row-list-copy">
                    <strong class="row-list-title">{{ item.teacherName }}</strong>
                    <span class="row-list-meta">
                      {{ t('studentClasses.classPage.feedback.role', { date: formatDateTime(item.createdAt) }) }}
                      <template v-if="item.editedAt"> · {{ t('studentClasses.classPage.feedback.edited') }}</template>
                    </span>
                  </span>
                  <span class="tag">{{ aboutLabel(item) }}</span>
                </div>
                <p class="student-class-note-message"><CjkText :text="item.message" /></p>
                <div v-if="feedbackTarget(item)" class="student-class-note-actions">
                  <RouterLink
                    class="btn btn-secondary btn-sm"
                    :to="feedbackTarget(item)!"
                    :aria-label="t('studentClasses.classPage.feedback.openLabel', { about: aboutLabel(item) })"
                  >
                    {{ t('studentClasses.classPage.feedback.open') }}<IconGlyph name="chevronRight" :size="16" />
                  </RouterLink>
                </div>
              </li>
            </ul>
          </section>
          <div v-else class="panel student-classes-empty">
            <StatePanel
              tone="empty"
              icon="chat"
              :title="t('studentClasses.classPage.feedback.emptyTitle')"
              :message="t('studentClasses.classPage.feedback.emptyMessage')"
            />
          </div>
        </template>
      </template>
    </template>

    <template v-else>
      <h1 class="sr-only">{{ t('classes.classes') }}</h1>
      <StatePanel
        v-if="resource.loading.value"
        tone="loading"
        spacious
        :title="t('classes.loading')"
        :message="t('classes.classes')"
      />
      <StatePanel
        v-else
        tone="error"
        icon="alert"
        spacious
        :title="t('studentClasses.classPage.loadErrorTitle')"
        :message="resource.error.value ?? ''"
      >
        <template #actions>
          <button class="btn btn-secondary" type="button" @click="resource.reload">{{ t('classes.retry') }}</button>
          <RouterLink class="btn btn-primary" :to="{ name: 'student-classes' }">
            {{ t('studentClasses.classPage.backToClasses') }}
          </RouterLink>
        </template>
      </StatePanel>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter, type RouteLocationRaw } from 'vue-router'

import { studentClassesApi } from '@/api/classes'
import ClassPageHeader from '@/components/classes/ClassPageHeader.vue'
import ClassSessionsPanel from '@/components/classes/ClassSessionsPanel.vue'
import DataNotice from '@/components/common/DataNotice.vue'
import IconGlyph from '@/components/common/IconGlyph.vue'
import PortraitAvatar from '@/components/common/PortraitAvatar.vue'
import ProgressBar from '@/components/common/ProgressBar.vue'
import RouteTabs, { type RouteTab } from '@/components/common/RouteTabs.vue'
import StatePanel from '@/components/common/StatePanel.vue'
import StatusPill, { type PillTone } from '@/components/common/StatusPill.vue'
import CjkText from '@/components/learning/CjkText.vue'
import { useActionFeedback } from '@/composables/useActionFeedback'
import { leaveClassNotice } from '@/composables/useClassNotice'
import { useLocalMessages } from '@/composables/useLocalMessages'
import { useResource } from '@/composables/useResource'
import { classesMessages } from '@/plugins/classesMessages'
import { studentClassesMessages } from '@/plugins/studentClassesMessages'
import type { FeedbackAbout, FeedbackItem, LessonProgressRow } from '@/types/classes'
import { formatDateTime, parseDate } from '@/utils/format'
import '@/styles/classes.css'

const { t } = useLocalMessages({ studentClasses: studentClassesMessages, classes: classesMessages })
const route = useRoute()
const router = useRouter()
const { actionError, runAction } = useActionFeedback()

const FEEDBACK_ABOUT_KEYS: Record<FeedbackAbout, string> = {
  QUIZ_ATTEMPT: 'studentClasses.classPage.feedback.about.QUIZ_ATTEMPT',
  LESSON: 'studentClasses.classPage.feedback.about.LESSON',
  GENERAL: 'studentClasses.classPage.feedback.about.GENERAL',
}

const classId = computed(() => Number(route.params.classId))
const tab = computed(() => (route.params.tab === 'feedback' ? 'feedback' : 'lessons'))

// The quiz results tab is gone; an old link lands on the class.
if (route.params.tab === 'results') {
  void router.replace({ name: 'student-class', params: { classId: classId.value } })
}

const resource = useResource(() => studentClassesApi.classPage(classId.value))
const page = computed(() => resource.data.value)
const feedback = useResource(() => studentClassesApi.feedback(classId.value), false)
/** Only to open the attempt a piece of feedback is about. */
const attempts = useResource(() => studentClassesApi.quizResults(classId.value), false)

watch(classId, () => void resource.reload())
watch(
  [tab, () => page.value?.joined],
  ([current, joined]) => {
    if (!joined || current !== 'feedback') return
    void feedback.reload()
    void attempts.reload()
  },
  { immediate: true },
)

const tabs = computed<RouteTab[]>(() => [
  {
    label: t('studentClasses.classPage.tabLessons'),
    to: { name: 'student-class', params: { classId: classId.value } },
    icon: 'learn',
    count: page.value?.lessonCount ?? null,
  },
  {
    label: t('studentClasses.classPage.tabFeedback'),
    to: { name: 'student-class', params: { classId: classId.value, tab: 'feedback' } },
    icon: 'chat',
    count: page.value?.feedbackCount ?? null,
  },
])

/** "Wong Siew Ling · Daily life · 4 lessons · best after Unit 3" */
const previewFacts = computed(() => {
  const summary = page.value?.summary
  if (!summary) return ''
  return [
    summary.teacher.name,
    summary.topicName,
    t('classes.lessons', summary.lessonCount),
    summary.bestAfterUnit ? t('classes.bestAfterInline', { unit: summary.bestAfterUnit }) : null,
  ]
    .filter(Boolean)
    .join(' · ')
})

const nextStepText = computed(() => {
  const next = page.value?.nextStep
  if (!next) return ''
  const allActivitiesDone = next.activityCount > 0 && next.activitiesDone >= next.activityCount
  return allActivitiesDone && !next.quizDone
    ? t('studentClasses.classPage.nextStepQuiz', { lesson: next.lessonTitle, number: next.lessonNumber })
    : t('studentClasses.classPage.nextStepText', {
        lesson: next.lessonTitle,
        number: next.lessonNumber,
        done: next.activitiesDone,
        total: next.activityCount,
      })
})

function lessonFacts(lesson: LessonProgressRow): string {
  return lesson.bestQuizPercent !== null
    ? t('studentClasses.classPage.lessonFactsQuiz', { number: lesson.number, percent: lesson.bestQuizPercent })
    : t('studentClasses.classPage.lessonFacts', { number: lesson.number })
}

function stateLabel(lesson: LessonProgressRow): string {
  if (lesson.status === 'IN_PROGRESS') return t('studentClasses.lessonState.IN_PROGRESS')
  if (lesson.status === 'NOT_STARTED') return t('studentClasses.lessonState.NOT_STARTED')
  return t('studentClasses.lessonState.COMPLETED')
}

function stateTone(lesson: LessonProgressRow): PillTone {
  if (lesson.status === 'IN_PROGRESS') return 'info'
  return lesson.status === 'NOT_STARTED' ? 'outline' : 'success'
}

function lessonRoute(lessonId: number) {
  return { name: 'student-class-lesson', params: { classId: classId.value, lessonId } }
}

function aboutLabel(item: FeedbackItem): string {
  return t(FEEDBACK_ABOUT_KEYS[item.about], { lesson: item.lessonTitle ?? '' })
}

/** Where a note's Open goes: the quiz attempt it answers (the latest one before it), else its lesson; general notes stay. */
function feedbackTarget(item: FeedbackItem): RouteLocationRaw | null {
  if (!item.lessonTitle) return null
  if (item.about === 'QUIZ_ATTEMPT') {
    const sent = parseDate(item.createdAt)?.getTime() ?? Number.POSITIVE_INFINITY
    const attempt = attempts.data.value?.attempts.find(
      (row) => row.lessonTitle === item.lessonTitle && (parseDate(row.completedAt)?.getTime() ?? 0) <= sent,
    )
    if (attempt) {
      return { name: 'student-quiz-attempt', params: { quizId: attempt.quizId, attemptId: attempt.attemptId } }
    }
  }
  const lesson = page.value?.units.flatMap((unit) => unit.lessons).find((row) => row.title === item.lessonTitle)
  return lesson?.available ? lessonRoute(lesson.id) : null
}

const joining = ref(false)

async function join(): Promise<void> {
  joining.value = true
  const joined = await runAction(t('studentClasses.classPage.preview.joinError'), async () => {
    await studentClassesApi.join(classId.value)
    return ''
  })
  joining.value = false
  if (!joined) return
  // Joining ends in My classes, with the new class listed and a confirmation.
  leaveClassNotice(t('studentClasses.classPage.preview.joined', { title: page.value?.summary.title ?? '' }))
  await router.push({ name: 'student-classes' })
}
</script>
